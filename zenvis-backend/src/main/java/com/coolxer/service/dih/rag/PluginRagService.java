package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.model.dih.dto.PluginRagConfigDto;
import com.coolxer.service.core.MinioService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 插件 RAG 生命周期服务。
 * <p>
 * 负责解析 08_rag 配置、校验包内文档、同步托管数据集和知识库、执行增量构建，以及导出当前托管资源。
 */
@Slf4j
@Service
public class PluginRagService {

    private static final Pattern SAFE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final String RAG_DIR = "08_rag";
    private static final String DOC_DIR = "00_doc";
    private static final String MANAGED_DIRECTORY_ROOT = "rag/plugins/";
    private static final String FINGERPRINT_VERSION = "plugin-rag-v1";

    @Autowired
    private RagDatasetRepository ragDatasetRepository;

    @Autowired
    private RagKnowledgeBaseRepository ragKnowledgeBaseRepository;

    @Autowired
    private RagKnowledgeBaseDatasetRepository ragKnowledgeBaseDatasetRepository;

    @Autowired
    private RagDatasetService ragDatasetService;

    @Autowired
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Autowired
    private MinioService minioService;

    @Autowired
    private VectorStoreInitializerService vectorStoreInitializerService;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    @Value("${spring.ai.vectorstore.redis.index:}")
    private String vectorIndex;

    /**
     * 预检插件 RAG 配置，不修改数据库或 MinIO。
     *
     * @param packageName 插件包名
     * @param pluginRoot  插件根目录
     * @param configJson  08_rag/config.json 内容，可为空
     * @return 可执行的 RAG 计划
     */
    public PluginRagPlan prepare(String packageName, Path pluginRoot, String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return prepareEmptyPlan(packageName);
        }
        PluginRagConfigDto config;
        try {
            config = JacksonConfig.OBJECT_MAPPER.readValue(configJson, PluginRagConfigDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("08_rag/config.json 解析失败: " + e.getMessage(), e);
        }
        if (config == null
                || (isEmpty(config.getDatasets()) && isEmpty(config.getKnowledgeBases()))) {
            return prepareEmptyPlan(packageName);
        }

        Path root = pluginRoot.toAbsolutePath().normalize();
        List<DatasetPlan> datasets = prepareDatasets(packageName, root, config.getDatasets());
        List<KnowledgeBasePlan> knowledgeBases = prepareKnowledgeBases(packageName, config.getKnowledgeBases(),
                datasets);
        validateManagedDatasetRemoval(packageName, datasets);
        validateManagedKnowledgeBasesIdle(packageName);
        return new PluginRagPlan(packageName, datasets, knowledgeBases);
    }

    /**
     * 同步声明式应用插件 RAG 资源，并在需要时同步构建知识库。
     *
     * @param packageName    插件包名
     * @param plan           预检计划，可为空
     * @param activePluginRoot 当前已生效的插件根目录
     * @param warnings       非阻断告警
     */
    public void synchronize(String packageName, PluginRagPlan plan, Path activePluginRoot,
                            Collection<String> warnings) {
        if (plan == null || plan.isEmpty()) {
            cleanupManagedResources(packageName, warnings, true);
            return;
        }

        Path root = activePluginRoot.toAbsolutePath().normalize();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        List<StagedDataset> stagedDatasets = new ArrayList<>();
        List<DatasetSnapshot> datasetSnapshots = new ArrayList<>();
        List<KnowledgeBaseSnapshot> knowledgeBaseSnapshots = new ArrayList<>();
        List<BuildTarget> buildTargets = new ArrayList<>();
        try {
            cleanupLegacyDocumentVectors(packageName, warnings);
            stageDatasetFiles(packageName, plan, root, operationId, stagedDatasets);
            Map<String, RagDataset> datasetsByCode = reconcileDatasets(plan, stagedDatasets,
                    datasetSnapshots);
            reconcileKnowledgeBases(plan, datasetsByCode, knowledgeBaseSnapshots, buildTargets);
            removeObsoleteManagedResources(plan);
            buildKnowledgeBases(buildTargets, warnings);
            cleanupReplacedDirectories(stagedDatasets);
        } catch (Exception e) {
            rollbackSnapshots(datasetSnapshots, knowledgeBaseSnapshots, stagedDatasets);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("插件 RAG 同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除插件托管的所有 RAG 资源。
     *
     * @param packageName 插件包名
     * @param warnings    非阻断告警
     */
    public void cleanupManagedResources(String packageName, Collection<String> warnings) {
        cleanupManagedResources(packageName, warnings, false);
    }

    private void cleanupManagedResources(String packageName, Collection<String> warnings, boolean failFast) {
        for (RagKnowledgeBase knowledgeBase : ragKnowledgeBaseRepository.findAllManaged(packageName)) {
            try {
                ragKnowledgeBaseService.delete(knowledgeBase.getId());
            } catch (Exception e) {
                if (failFast) {
                    throw cleanupFailure("知识库「" + knowledgeBase.getName() + "」清理失败", e);
                }
                warnings.add("知识库「" + knowledgeBase.getName() + "」清理失败：" + e.getMessage());
                log.warn("清理插件知识库失败: package={}, id={}", packageName, knowledgeBase.getId(), e);
            }
        }
        for (RagDataset dataset : ragDatasetRepository.findAllManaged(packageName)) {
            List<RagKnowledgeBaseDataset> references = ragKnowledgeBaseDatasetRepository.findByDatasetId(dataset.getId());
            if (!references.isEmpty()) {
                warnings.add("数据集「" + dataset.getName() + "」仍被其他知识库引用，已保留");
                continue;
            }
            try {
                ragDatasetService.delete(dataset.getId());
            } catch (Exception e) {
                if (failFast) {
                    throw cleanupFailure("数据集「" + dataset.getName() + "」清理失败", e);
                }
                warnings.add("数据集「" + dataset.getName() + "」清理失败：" + e.getMessage());
                log.warn("清理插件数据集失败: package={}, id={}", packageName, dataset.getId(), e);
            }
        }
        cleanupLegacyDocumentVectors(packageName, warnings);
    }

    /**
     * 导出插件当前托管资源到 08_rag 目录。
     *
     * @param packageName 插件包名
     * @param targetRagDir 目标 08_rag 目录
     * @throws IOException 文件复制失败
     */
    public void exportManagedResources(String packageName, Path targetRagDir) throws IOException {
        deleteDirectory(targetRagDir);
        Files.createDirectories(targetRagDir);
        List<RagDataset> datasets = ragDatasetRepository.findAllManaged(packageName).stream()
                .sorted(Comparator.comparing(RagDataset::getId))
                .toList();
        List<RagKnowledgeBase> knowledgeBases = ragKnowledgeBaseRepository.findAllManaged(packageName).stream()
                .sorted(Comparator.comparing(RagKnowledgeBase::getId))
                .toList();

        PluginRagConfigDto config = new PluginRagConfigDto();
        List<PluginRagConfigDto.Dataset> datasetDefinitions = new ArrayList<>();
        Map<Integer, String> datasetCodeById = new HashMap<>();
        for (RagDataset dataset : datasets) {
            String code = StringUtils.defaultIfBlank(dataset.getPluginCode(), "dataset-" + dataset.getId());
            datasetCodeById.put(dataset.getId(), code);
            PluginRagConfigDto.Dataset definition = new PluginRagConfigDto.Dataset();
            definition.setCode(code);
            definition.setName(dataset.getName());
            definition.setSource(dataset.getSource());
            definition.setTags(readTags(dataset.getTags()));
            definition.setDescription(dataset.getDescription());
            definition.setPath(RAG_DIR + "/datasets/" + code);
            datasetDefinitions.add(definition);
            exportDatasetFiles(dataset, targetRagDir.resolve("datasets").resolve(code));
        }

        List<PluginRagConfigDto.KnowledgeBase> knowledgeBaseDefinitions = new ArrayList<>();
        for (RagKnowledgeBase knowledgeBase : knowledgeBases) {
            PluginRagConfigDto.KnowledgeBase definition = new PluginRagConfigDto.KnowledgeBase();
            definition.setCode(StringUtils.defaultIfBlank(knowledgeBase.getPluginCode(),
                    "knowledge-base-" + knowledgeBase.getId()));
            definition.setName(knowledgeBase.getName());
            definition.setSource(knowledgeBase.getSource());
            definition.setTags(readTags(knowledgeBase.getTags()));
            definition.setDescription(knowledgeBase.getDescription());
            List<String> datasetCodes = ragKnowledgeBaseDatasetRepository
                    .findByKnowledgeBaseId(knowledgeBase.getId()).stream()
                    .map(RagKnowledgeBaseDataset::getDatasetId)
                    .map(datasetCodeById::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            definition.setDatasetCodes(datasetCodes);
            knowledgeBaseDefinitions.add(definition);
        }
        config.setDatasets(datasetDefinitions);
        config.setKnowledgeBases(knowledgeBaseDefinitions);
        JacksonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(targetRagDir.resolve("config.json").toFile(), config);
    }

    private List<DatasetPlan> prepareDatasets(String packageName, Path root, List<PluginRagConfigDto.Dataset> definitions) {
        if (isEmpty(definitions)) {
            return List.of();
        }
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<DatasetPlan> plans = new ArrayList<>();
        for (PluginRagConfigDto.Dataset definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("08_rag datasets 不能包含空项");
            }
            String code = normalizeCode(definition.getCode(), "数据集");
            String name = requireName(definition.getName(), "数据集");
            if (!codes.add(code.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("08_rag 数据集编码重复: " + code);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("08_rag 数据集名称重复: " + name);
            }
            validateDatasetNameConflict(packageName, code, name);
            String sourcePath = normalizeSourcePath(packageName, root, definition.getPath());
            List<FilePlan> files = collectFiles(root.resolve(sourcePath), root);
            plans.add(new DatasetPlan(code, name,
                    StringUtils.defaultIfBlank(StringUtils.trim(definition.getSource()), packageName),
                    normalizeTags(definition.getTags()), StringUtils.trimToNull(definition.getDescription()),
                    sourcePath, files));
        }
        return List.copyOf(plans);
    }

    private List<KnowledgeBasePlan> prepareKnowledgeBases(String packageName,
                                                          List<PluginRagConfigDto.KnowledgeBase> definitions,
                                                          List<DatasetPlan> datasets) {
        if (isEmpty(definitions)) {
            return List.of();
        }
        Set<String> datasetCodes = datasets.stream().map(DatasetPlan::code).collect(Collectors.toSet());
        Map<String, List<FilePlan>> filesByDatasetCode = datasets.stream()
                .collect(Collectors.toMap(DatasetPlan::code, DatasetPlan::files));
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<KnowledgeBasePlan> plans = new ArrayList<>();
        for (PluginRagConfigDto.KnowledgeBase definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("08_rag knowledge_bases 不能包含空项");
            }
            String code = normalizeCode(definition.getCode(), "知识库");
            String name = requireName(definition.getName(), "知识库");
            if (!codes.add(code.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("08_rag 知识库编码重复: " + code);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("08_rag 知识库名称重复: " + name);
            }
            validateKnowledgeBaseNameConflict(packageName, code, name);
            List<String> references = definition.getDatasetCodes() == null ? List.of()
                    : definition.getDatasetCodes().stream().map(StringUtils::trim).filter(StringUtils::isNotBlank)
                    .distinct().toList();
            if (references.isEmpty()) {
                throw new IllegalArgumentException("知识库「" + name + "」必须关联至少一个数据集");
            }
            for (String reference : references) {
                if (!datasetCodes.contains(reference)) {
                    throw new IllegalArgumentException("知识库「" + name + "」引用了未声明的数据集: " + reference);
                }
            }
            plans.add(new KnowledgeBasePlan(code, name,
                    StringUtils.defaultIfBlank(StringUtils.trim(definition.getSource()), packageName),
                    normalizeTags(definition.getTags()), StringUtils.trimToNull(definition.getDescription()),
                    List.copyOf(references), filesByDatasetCode));
        }
        return List.copyOf(plans);
    }

    private void validateDatasetNameConflict(String packageName, String code, String name) {
        for (RagDataset existing : ragDatasetRepository.findActiveByName(name)) {
            if (!sameManagedResource(packageName, code, existing.getPluginPackage(), existing.getPluginCode())) {
                throw new IllegalArgumentException("数据集名称已被现有资源占用: " + name);
            }
        }
        List<RagDataset> managed = ragDatasetRepository.findManaged(packageName, code);
        if (managed.size() > 1) {
            throw new IllegalArgumentException("插件数据集编码存在重复托管记录: " + code);
        }
    }

    private void validateKnowledgeBaseNameConflict(String packageName, String code, String name) {
        for (RagKnowledgeBase existing : ragKnowledgeBaseRepository.findActiveByName(name)) {
            if (!sameManagedResource(packageName, code, existing.getPluginPackage(), existing.getPluginCode())) {
                throw new IllegalArgumentException("知识库名称已被现有资源占用: " + name);
            }
        }
        List<RagKnowledgeBase> managed = ragKnowledgeBaseRepository.findManaged(packageName, code);
        if (managed.size() > 1) {
            throw new IllegalArgumentException("插件知识库编码存在重复托管记录: " + code);
        }
    }

    private boolean sameManagedResource(String packageName, String code, String resourcePackage, String resourceCode) {
        return Objects.equals(packageName, resourcePackage) && Objects.equals(code, resourceCode);
    }

    private void validateManagedDatasetRemoval(String packageName, List<DatasetPlan> declaredDatasets) {
        Set<String> declaredCodes = declaredDatasets.stream().map(DatasetPlan::code).collect(Collectors.toSet());
        for (RagDataset existing : ragDatasetRepository.findAllManaged(packageName)) {
            if (declaredCodes.contains(existing.getPluginCode())) {
                continue;
            }
            for (RagKnowledgeBaseDataset relation : ragKnowledgeBaseDatasetRepository.findByDatasetId(existing.getId())) {
                RagKnowledgeBase knowledgeBase = ragKnowledgeBaseRepository.findById(relation.getKnowledgeBaseId())
                        .orElse(null);
                if (knowledgeBase == null || !Objects.equals(packageName, knowledgeBase.getPluginPackage())) {
                    throw new IllegalArgumentException("数据集「" + existing.getName() + "」被非托管知识库引用，无法升级");
                }
            }
        }
    }

    private void validateManagedKnowledgeBasesIdle(String packageName) {
        for (RagKnowledgeBase existing : ragKnowledgeBaseRepository.findAllManaged(packageName)) {
            if (RagKnowledgeBaseStatus.BUILDING == existing.getStatus()) {
                throw new IllegalArgumentException("知识库「" + existing.getName() + "」正在构建中，无法升级");
            }
        }
    }

    private String normalizeSourcePath(String packageName, Path root, String rawPath) {
        if (StringUtils.isBlank(rawPath)) {
            throw new IllegalArgumentException("插件「" + packageName + "」的数据集缺少 path");
        }
        String normalizedRaw = rawPath.replace('\\', '/').trim();
        if (normalizedRaw.startsWith("/") || normalizedRaw.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("数据集 path 必须使用插件内相对路径: " + rawPath);
        }
        String normalized = Paths.get(normalizedRaw).normalize().toString().replace('\\', '/');
        if (normalized.equals("..") || normalized.startsWith("../")) {
            throw new IllegalArgumentException("数据集 path 不能越出插件目录: " + rawPath);
        }
        if (!(normalized.equals(DOC_DIR) || normalized.startsWith(DOC_DIR + "/")
                || normalized.startsWith(RAG_DIR + "/"))) {
            throw new IllegalArgumentException("数据集 path 必须位于 00_doc 或 08_rag 下: " + rawPath);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("数据集 path 不能越出插件目录: " + rawPath);
        }
        validateNoSymlink(root, resolved);
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("数据集 path 不是目录: " + rawPath);
        }
        return normalized;
    }

    private void validateNoSymlink(Path root, Path target) {
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("数据集 path 不允许包含符号链接: " + target);
            }
        }
    }

    private List<FilePlan> collectFiles(Path source, Path root) {
        List<FilePlan> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("数据集目录不允许包含符号链接: " + root.relativize(path));
                }
                if (path.equals(source) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("数据集目录包含非普通文件: " + root.relativize(path));
                }
                String fileName = path.getFileName().toString();
                if (!StringUtils.endsWithIgnoreCase(fileName, ".md")) {
                    throw new IllegalArgumentException("数据集目录仅支持 Markdown 文件: " + root.relativize(path));
                }
                String relative = source.relativize(path).toString().replace('\\', '/');
                if (!paths.add(relative)) {
                    throw new IllegalArgumentException("数据集文档路径重复: " + relative);
                }
                files.add(new FilePlan(relative, sha256(path), Files.size(path)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取数据集文档失败: " + e.getMessage(), e);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("数据集目录中没有 Markdown 文件: " + root.relativize(source));
        }
        files.sort(Comparator.comparing(FilePlan::relativePath));
        return List.copyOf(files);
    }

    private void stageDatasetFiles(String packageName, PluginRagPlan plan, Path root, String operationId,
                                   List<StagedDataset> stagedDatasets) throws IOException {
        for (DatasetPlan datasetPlan : plan.datasets()) {
            String directory = MANAGED_DIRECTORY_ROOT + safePathSegment(packageName) + "/"
                    + safePathSegment(datasetPlan.code()) + "/" + operationId;
            stagedDatasets.add(new StagedDataset(datasetPlan, directory, null));
            Path source = root.resolve(datasetPlan.sourcePath());
            for (FilePlan file : datasetPlan.files()) {
                String objectName = directory + "/" + file.relativePath();
                Path filePath = source.resolve(file.relativePath());
                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    minioService.uploadStream(objectName, inputStream, file.size(), "text/markdown", null);
                }
            }
        }
    }

    private Map<String, RagDataset> reconcileDatasets(PluginRagPlan plan,
                                                       List<StagedDataset> stagedDatasets,
                                                       List<DatasetSnapshot> snapshots) {
        Map<String, StagedDataset> stagedByCode = stagedDatasets.stream()
                .collect(Collectors.toMap(item -> item.plan().code(), item -> item));
        Map<String, RagDataset> result = new LinkedHashMap<>();
        for (DatasetPlan datasetPlan : plan.datasets()) {
            RagDataset existing = findSingleManagedDataset(plan.packageName(), datasetPlan.code());
            DatasetSnapshot snapshot = new DatasetSnapshot(existing == null ? null : copyDataset(existing));
            snapshots.add(snapshot);
            RagDataset dataset = existing == null ? new RagDataset() : existing;
            if (existing != null) {
                stagedByCode.get(datasetPlan.code()).setOldDirectory(existing.getDirectory());
            }
            dataset.setName(datasetPlan.name())
                    .setSource(datasetPlan.source())
                    .setTags(writeTags(datasetPlan.tags()))
                    .setDescription(datasetPlan.description())
                    .setDirectory(stagedByCode.get(datasetPlan.code()).newDirectory())
                    .setFileCount(datasetPlan.files().size())
                    .setPluginPackage(plan.packageName())
                    .setPluginCode(datasetPlan.code());
            if (dataset.getIsDelete() == null) {
                dataset.setIsDelete(0);
            }
            dataset = ragDatasetRepository.save(dataset);
            if (existing == null) {
                snapshot.setCreatedId(dataset.getId());
            }
            result.put(datasetPlan.code(), dataset);
        }
        return result;
    }

    private void reconcileKnowledgeBases(PluginRagPlan plan,
                                         Map<String, RagDataset> datasetsByCode,
                                         List<KnowledgeBaseSnapshot> snapshots,
                                         List<BuildTarget> buildTargets) {
        Map<String, KnowledgeBasePlan> knowledgeBasePlans = plan.knowledgeBases().stream()
                .collect(Collectors.toMap(KnowledgeBasePlan::code, item -> item));
        for (KnowledgeBasePlan knowledgeBasePlan : plan.knowledgeBases()) {
            RagKnowledgeBase existing = findSingleManagedKnowledgeBase(plan.packageName(), knowledgeBasePlan.code());
            if (existing != null && RagKnowledgeBaseStatus.BUILDING == existing.getStatus()) {
                throw new IllegalStateException("知识库「" + existing.getName() + "」正在构建中，无法同步");
            }
            List<Integer> oldDatasetIds = existing == null ? List.of()
                    : ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(existing.getId()).stream()
                    .map(RagKnowledgeBaseDataset::getDatasetId).toList();
            KnowledgeBaseSnapshot snapshot = new KnowledgeBaseSnapshot(
                    existing == null ? null : copyKnowledgeBase(existing), oldDatasetIds);
            snapshots.add(snapshot);

            RagKnowledgeBase knowledgeBase = existing == null ? new RagKnowledgeBase() : existing;
            knowledgeBase.setName(knowledgeBasePlan.name())
                    .setSource(knowledgeBasePlan.source())
                    .setTags(writeTags(knowledgeBasePlan.tags()))
                    .setDescription(knowledgeBasePlan.description())
                    .setPluginPackage(plan.packageName())
                    .setPluginCode(knowledgeBasePlan.code());
            if (knowledgeBase.getIsDelete() == null) {
                knowledgeBase.setIsDelete(0);
            }
            List<RagDataset> datasets = knowledgeBasePlan.datasetCodes().stream()
                    .map(datasetsByCode::get).filter(Objects::nonNull).toList();
            String fingerprint = buildFingerprint(knowledgeBasePlan, datasets);
            boolean rebuild = existing == null
                    || RagKnowledgeBaseStatus.BUILT != existing.getStatus()
                    || !Objects.equals(existing.getBuildFingerprint(), fingerprint);
            if (rebuild) {
                knowledgeBase.setStatus(RagKnowledgeBaseStatus.UNBUILD)
                        .setDocumentCount(0)
                        .setBuildFingerprint(null)
                        .setOperationError(null)
                        .setOperationMessage(null);
            }
            knowledgeBase = ragKnowledgeBaseRepository.save(knowledgeBase);
            if (existing == null) {
                snapshot.setCreatedId(knowledgeBase.getId());
            }
            replaceRelations(knowledgeBase.getId(), datasets);
            if (rebuild) {
                buildTargets.add(new BuildTarget(knowledgeBasePlans.get(knowledgeBasePlan.code()), knowledgeBase,
                        fingerprint));
            }
        }
    }

    private void replaceRelations(Integer knowledgeBaseId, List<RagDataset> datasets) {
        ragKnowledgeBaseDatasetRepository.deleteAll(
                ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(knowledgeBaseId));
        List<RagKnowledgeBaseDataset> relations = datasets.stream()
                .map(dataset -> new RagKnowledgeBaseDataset()
                        .setKnowledgeBaseId(knowledgeBaseId)
                        .setDatasetId(dataset.getId()))
                .toList();
        ragKnowledgeBaseDatasetRepository.saveAll(relations);
    }

    private void removeObsoleteManagedResources(PluginRagPlan plan) {
        Set<String> knowledgeBaseCodes = plan.knowledgeBases().stream().map(KnowledgeBasePlan::code)
                .collect(Collectors.toSet());
        for (RagKnowledgeBase existing : ragKnowledgeBaseRepository.findAllManaged(plan.packageName())) {
            if (!knowledgeBaseCodes.contains(existing.getPluginCode())) {
                ragKnowledgeBaseService.delete(existing.getId());
            }
        }
        Set<String> datasetCodes = plan.datasets().stream().map(DatasetPlan::code).collect(Collectors.toSet());
        for (RagDataset existing : ragDatasetRepository.findAllManaged(plan.packageName())) {
            if (datasetCodes.contains(existing.getPluginCode())) {
                continue;
            }
            if (!ragKnowledgeBaseDatasetRepository.findByDatasetId(existing.getId()).isEmpty()) {
                throw new IllegalStateException("数据集「" + existing.getName() + "」仍被知识库引用，无法删除");
            }
            ragDatasetService.delete(existing.getId());
        }
    }

    private void buildKnowledgeBases(List<BuildTarget> targets, Collection<String> warnings) {
        for (BuildTarget target : targets) {
            boolean available;
            try {
                available = vectorStoreInitializerService.isRagAvailable();
            } catch (Exception e) {
                available = false;
                log.warn("检查 Embedding/RAG 服务失败: knowledgeBase={}", target.entity().getName(), e);
            }
            if (!available) {
                target.entity().setStatus(RagKnowledgeBaseStatus.UNBUILD)
                        .setOperationMessage("Embedding/RAG 服务不可用，待服务恢复后重建")
                        .setOperationError(null);
                ragKnowledgeBaseRepository.save(target.entity());
                warnings.add("知识库「" + target.entity().getName() + "」构建已跳过：Embedding/RAG 服务不可用");
                continue;
            }
            try {
                RagKnowledgeBaseStatus status = ragKnowledgeBaseService.buildNow(target.entity().getId());
                RagKnowledgeBase saved = ragKnowledgeBaseRepository.findById(target.entity().getId())
                        .orElse(target.entity());
                saved.setStatus(status);
                if (RagKnowledgeBaseStatus.BUILT == status) {
                    saved.setBuildFingerprint(target.fingerprint());
                } else {
                    saved.setBuildFingerprint(null);
                    warnings.add("知识库「" + target.entity().getName() + "」构建失败，请查看构建日志后重试");
                }
                ragKnowledgeBaseRepository.save(saved);
            } catch (DataAccessException e) {
                throw e;
            } catch (Exception e) {
                warnings.add("知识库「" + target.entity().getName() + "」构建失败：" + e.getMessage());
                log.warn("插件知识库同步构建失败: id={}", target.entity().getId(), e);
            }
        }
    }

    private void cleanupReplacedDirectories(List<StagedDataset> stagedDatasets) {
        for (StagedDataset staged : stagedDatasets) {
            if (StringUtils.isNotBlank(staged.oldDirectory())
                    && !Objects.equals(staged.oldDirectory(), staged.newDirectory())) {
                deleteMinioDirectory(staged.oldDirectory());
            }
        }
    }

    private void rollbackSnapshots(List<DatasetSnapshot> datasetSnapshots,
                                   List<KnowledgeBaseSnapshot> knowledgeBaseSnapshots,
                                   List<StagedDataset> stagedDatasets) {
        for (StagedDataset staged : stagedDatasets) {
            deleteMinioDirectory(staged.newDirectory());
        }
        for (int i = knowledgeBaseSnapshots.size() - 1; i >= 0; i--) {
            KnowledgeBaseSnapshot snapshot = knowledgeBaseSnapshots.get(i);
            try {
                if (snapshot.entity() == null) {
                    Integer id = snapshot.createdId();
                    if (id != null) {
                        ragKnowledgeBaseDatasetRepository.deleteAll(
                                ragKnowledgeBaseDatasetRepository.findByKnowledgeBaseId(id));
                        ragKnowledgeBaseRepository.deleteById(id);
                    }
                    continue;
                }
                RagKnowledgeBase restored = snapshot.entity();
                restored.setBuildFingerprint(null);
                if (RagKnowledgeBaseStatus.BUILT == restored.getStatus()
                        || RagKnowledgeBaseStatus.BUILD_FAILED == restored.getStatus()) {
                    restored.setStatus(RagKnowledgeBaseStatus.UNBUILD)
                            .setDocumentCount(0);
                }
                restored = ragKnowledgeBaseRepository.save(restored);
                replaceRelations(restored.getId(), snapshot.datasetIds().stream()
                        .map(id -> {
                            RagDataset dataset = new RagDataset();
                            dataset.setId(id);
                            return dataset;
                        }).toList());
            } catch (Exception e) {
                log.error("恢复插件知识库快照失败", e);
            }
        }
        for (int i = datasetSnapshots.size() - 1; i >= 0; i--) {
            DatasetSnapshot snapshot = datasetSnapshots.get(i);
            try {
                if (snapshot.entity() == null) {
                    Integer id = snapshot.createdId();
                    if (id != null) {
                        ragDatasetRepository.deleteById(id);
                    }
                    continue;
                }
                ragDatasetRepository.save(snapshot.entity());
            } catch (Exception e) {
                log.error("恢复插件数据集快照失败", e);
            }
        }
    }

    private void cleanupLegacyDocumentVectors(String packageName, Collection<String> warnings) {
        String legacySource = packageName.replace('.', '_');
        try {
            if (vectorStoreInitializerService.isRagAvailable()) {
                vectorStoreInitializerService.unloadDocFromRag(legacySource);
            }
        } catch (Exception e) {
            warnings.add("旧版 00_doc 向量清理失败，可在 RAG 服务恢复后手工处理");
            log.warn("清理旧版插件文档向量失败: package={}", packageName, e);
        }
    }

    private void exportDatasetFiles(RagDataset dataset, Path targetDirectory) throws IOException {
        List<String> objectNames = minioService.listObjects(dataset.getDirectory(), null);
        String prefix = dataset.getDirectory() + "/";
        for (String objectName : objectNames) {
            if (StringUtils.isBlank(objectName) || !objectName.startsWith(prefix)
                    || !StringUtils.endsWithIgnoreCase(objectName, ".md")) {
                continue;
            }
            String relative = objectName.substring(prefix.length());
            if (relative.isBlank() || relative.startsWith("/") || relative.contains("..")) {
                continue;
            }
            Path target = targetDirectory.resolve(relative).normalize();
            if (!target.startsWith(targetDirectory.normalize())) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = minioService.downloadFile(objectName, null)) {
                Files.copy(inputStream, target);
            }
        }
    }

    private void deleteMinioDirectory(String directory) {
        if (StringUtils.isBlank(directory)) {
            return;
        }
        try {
            for (String objectName : minioService.listObjects(directory, null)) {
                minioService.deleteFile(objectName, null);
            }
        } catch (Exception e) {
            log.warn("清理插件 RAG MinIO 目录失败: {}", directory, e);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private IllegalStateException cleanupFailure(String message, Exception cause) {
        return new IllegalStateException(message + "：" + cause.getMessage(), cause);
    }

    private RagDataset findSingleManagedDataset(String packageName, String code) {
        List<RagDataset> matches = ragDatasetRepository.findManaged(packageName, code);
        if (matches.size() > 1) {
            throw new IllegalStateException("插件数据集编码存在重复托管记录: " + code);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private RagKnowledgeBase findSingleManagedKnowledgeBase(String packageName, String code) {
        List<RagKnowledgeBase> matches = ragKnowledgeBaseRepository.findManaged(packageName, code);
        if (matches.size() > 1) {
            throw new IllegalStateException("插件知识库编码存在重复托管记录: " + code);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private String buildFingerprint(KnowledgeBasePlan plan, List<RagDataset> datasets) {
        List<String> lines = new ArrayList<>();
        lines.add(FINGERPRINT_VERSION);
        lines.add("knowledge_base=" + plan.code());
        lines.add("name=" + plan.name());
        lines.add("tags=" + plan.tags().stream().sorted().collect(Collectors.joining(",")));
        lines.add("embedding_model=" + StringUtils.defaultString(embeddingModel));
        lines.add("vector_index=" + StringUtils.defaultString(vectorIndex));
        List<RagDataset> orderedDatasets = datasets.stream()
                .sorted(Comparator.comparing(RagDataset::getPluginCode))
                .toList();
        for (RagDataset dataset : orderedDatasets) {
            lines.add("dataset=" + dataset.getPluginCode() + "|" + dataset.getName());
            for (FilePlan file : findDatasetPlanFor(plan, dataset.getPluginCode())) {
                lines.add("file=" + dataset.getPluginCode() + "/" + file.relativePath() + "|" + file.sha256());
            }
        }
        return sha256(String.join("\n", lines));
    }

    private List<FilePlan> findDatasetPlanFor(KnowledgeBasePlan knowledgeBasePlan, String datasetCode) {
        if (knowledgeBasePlan.filesByDatasetCode() == null) {
            return List.of();
        }
        return knowledgeBasePlan.filesByDatasetCode().getOrDefault(datasetCode, List.of());
    }

    private PluginRagPlan emptyPlan(String packageName) {
        return new PluginRagPlan(packageName, List.of(), List.of());
    }

    private PluginRagPlan prepareEmptyPlan(String packageName) {
        validateManagedDatasetRemoval(packageName, List.of());
        validateManagedKnowledgeBasesIdle(packageName);
        return emptyPlan(packageName);
    }

    private String normalizeCode(String value, String type) {
        String code = StringUtils.trim(value);
        if (StringUtils.isBlank(code) || !SAFE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(type + "编码非法，仅允许字母、数字、点、下划线和短横线: " + value);
        }
        return code;
    }

    private String requireName(String value, String type) {
        String name = StringUtils.trim(value);
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException(type + "名称不能为空");
        }
        return name;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream().map(StringUtils::trim).filter(StringUtils::isNotBlank).distinct().toList();
    }

    private String writeTags(List<String> tags) {
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (Exception e) {
            throw new IllegalStateException("写入 RAG 标签失败", e);
        }
    }

    private List<String> readTags(String tags) {
        try {
            if (StringUtils.isBlank(tags)) {
                return List.of();
            }
            List<String> parsed = JacksonConfig.OBJECT_MAPPER.readValue(tags,
                    new TypeReference<List<String>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String safePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalArgumentException("计算文档摘要失败: " + path, e);
        }
    }

    private String sha256(String value) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算构建指纹失败", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private RagDataset copyDataset(RagDataset source) {
        RagDataset copy = new RagDataset()
                .setName(source.getName())
                .setSource(source.getSource())
                .setTags(source.getTags())
                .setDescription(source.getDescription())
                .setDirectory(source.getDirectory())
                .setFileCount(source.getFileCount())
                .setPluginPackage(source.getPluginPackage())
                .setPluginCode(source.getPluginCode());
        copy.setId(source.getId());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setCreateBy(source.getCreateBy());
        copy.setUpdateBy(source.getUpdateBy());
        copy.setIsDelete(source.getIsDelete());
        return copy;
    }

    private RagKnowledgeBase copyKnowledgeBase(RagKnowledgeBase source) {
        RagKnowledgeBase copy = new RagKnowledgeBase()
                .setName(source.getName())
                .setSource(source.getSource())
                .setTags(source.getTags())
                .setDescription(source.getDescription())
                .setStatus(source.getStatus())
                .setDocumentCount(source.getDocumentCount())
                .setOperationMessage(source.getOperationMessage())
                .setOperationError(source.getOperationError())
                .setOperationStartedAt(source.getOperationStartedAt())
                .setOperationEndedAt(source.getOperationEndedAt())
                .setPluginPackage(source.getPluginPackage())
                .setPluginCode(source.getPluginCode())
                .setBuildFingerprint(source.getBuildFingerprint());
        copy.setId(source.getId());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setCreateBy(source.getCreateBy());
        copy.setUpdateBy(source.getUpdateBy());
        copy.setIsDelete(source.getIsDelete());
        return copy;
    }

    private static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * 预检后的插件 RAG 声明。
     */
    public record PluginRagPlan(String packageName, List<DatasetPlan> datasets,
                                List<KnowledgeBasePlan> knowledgeBases) {
        public PluginRagPlan {
            datasets = datasets == null ? List.of() : List.copyOf(datasets);
            knowledgeBases = knowledgeBases == null ? List.of() : List.copyOf(knowledgeBases);
        }

        public boolean isEmpty() {
            return datasets.isEmpty() && knowledgeBases.isEmpty();
        }
    }

    /**
     * 数据集声明及其文档清单。
     */
    public record DatasetPlan(String code, String name, String source, List<String> tags,
                              String description, String sourcePath, List<FilePlan> files) {
    }

    /**
     * 知识库声明。
     */
    public record KnowledgeBasePlan(String code, String name, String source, List<String> tags,
                                    String description, List<String> datasetCodes,
                                    Map<String, List<FilePlan>> filesByDatasetCode) {
        public KnowledgeBasePlan(String code, String name, String source, List<String> tags,
                                 String description, List<String> datasetCodes) {
            this(code, name, source, tags, description, datasetCodes, Map.of());
        }
    }

    /**
     * 包内 Markdown 文件摘要。
     */
    public record FilePlan(String relativePath, String sha256, long size) {
    }

    private static final class StagedDataset {
        private final DatasetPlan plan;
        private final String newDirectory;
        private String oldDirectory;

        private StagedDataset(DatasetPlan plan, String newDirectory, String oldDirectory) {
            this.plan = plan;
            this.newDirectory = newDirectory;
            this.oldDirectory = oldDirectory;
        }

        private DatasetPlan plan() {
            return plan;
        }

        private String newDirectory() {
            return newDirectory;
        }

        private String oldDirectory() {
            return oldDirectory;
        }

        private void setOldDirectory(String oldDirectory) {
            this.oldDirectory = oldDirectory;
        }
    }

    private static final class DatasetSnapshot {
        private final RagDataset entity;
        private Integer createdId;

        private DatasetSnapshot(RagDataset entity) {
            this.entity = entity;
        }

        private RagDataset entity() {
            return entity;
        }

        private Integer createdId() {
            return createdId;
        }

        private void setCreatedId(Integer createdId) {
            this.createdId = createdId;
        }
    }

    private static final class KnowledgeBaseSnapshot {
        private final RagKnowledgeBase entity;
        private final List<Integer> datasetIds;
        private Integer createdId;

        private KnowledgeBaseSnapshot(RagKnowledgeBase entity, List<Integer> datasetIds) {
            this.entity = entity;
            this.datasetIds = datasetIds;
        }

        private RagKnowledgeBase entity() {
            return entity;
        }

        private List<Integer> datasetIds() {
            return datasetIds;
        }

        private Integer createdId() {
            return createdId;
        }

        private void setCreatedId(Integer createdId) {
            this.createdId = createdId;
        }
    }

    private record BuildTarget(KnowledgeBasePlan plan, RagKnowledgeBase entity, String fingerprint) {
    }
}
