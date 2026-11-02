package com.coolxer.service.dih.dataapp;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dataapp.DataAppArtifact;
import com.coolxer.model.dih.dataapp.DataAppChangeSet;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class DataAppPackageService {

    public static final String MANIFEST_FILE = "_app.json";

    private final ConfigService configService;
    private final MetaDataService metaDataService;
    private final DataAppValidator validator;
    private final DataAppCompiler compiler;
    private final DataAppQuerySampler querySampler;

    public DataAppPackageService(ConfigService configService,
                                 MetaDataService metaDataService,
                                 DataAppValidator validator,
                                 DataAppCompiler compiler,
                                 DataAppQuerySampler querySampler) {
        this.configService = configService;
        this.metaDataService = metaDataService;
        this.validator = validator;
        this.compiler = compiler;
        this.querySampler = querySampler;
    }

    public Optional<DataAppManifest> readManifest(String appId) {
        String normalized = DataAppSupport.normalizeAppId(appId);
        if (!DataAppSupport.validAppId(normalized)
                || !configService.fileExistsInConfigPath(normalized, MANIFEST_FILE)) {
            return Optional.empty();
        }
        String content = configService.readFile(normalized, MANIFEST_FILE);
        if (!StringUtils.isNotBlank(content)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JacksonConfig.OBJECT_MAPPER.readValue(content, DataAppManifest.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public DataAppValidationResult build(DataAppSpec requestedSpec,
                                         DataAppChangeSet changeSet,
                                         String workflowId) {
        return build(requestedSpec, changeSet, workflowId, null, null);
    }

    public DataAppValidationResult build(DataAppSpec requestedSpec,
                                         DataAppChangeSet changeSet,
                                         String workflowId,
                                         String previewToken) {
        return build(requestedSpec, changeSet, workflowId, null, previewToken);
    }

    public DataAppValidationResult build(DataAppSpec requestedSpec,
                                         DataAppChangeSet changeSet,
                                         String workflowId,
                                         String chatId,
                                         String previewToken) {
        List<DataAppDiagnostic> diagnostics = new ArrayList<>();
        String appId = DataAppSupport.normalizeAppId(
                changeSet != null && StringUtils.isNotBlank(changeSet.getAppId())
                        ? changeSet.getAppId() : requestedSpec == null ? null : requestedSpec.getAppId());
        if (!DataAppSupport.validAppId(appId)) {
            DataAppValidationResult result = failed("app_id 无效");
            result.setWorkflowId(workflowId);
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "APP_ID_INVALID", "app_id", "app_id 无效", "error"));
            return result;
        }
        Optional<DataAppManifest> existingOptional = readManifest(appId);
        DataAppManifest existing = existingOptional.orElse(null);
        if (existing == null && hasLegacyContent(appId)) {
            diagnostics.add(new DataAppDiagnostic(
                    "LEGACY_APP_READ_ONLY", "app_id",
                    "该 app_id 下已有无 _app.json 的历史应用，只能只读查看，不能自动接管或覆盖", "error"));
        }
        DataAppSpec candidate = changeSet == null
                ? requestedSpec
                : mergeChangeSet(existing, changeSet, appId, diagnostics);
        DataAppValidator.Result validation = validator.validate(candidate);
        diagnostics.addAll(validation.diagnostics());

        if (existing != null) {
            if (changeSet == null) {
                diagnostics.add(new DataAppDiagnostic(
                        "CHANGESET_REQUIRED", "changeset", "更新已有应用必须提供 changeset", "error"));
            } else {
                if (changeSet.getBaseAppVersion() == null
                        || !changeSet.getBaseAppVersion().equals(existing.getAppVersion())) {
                    diagnostics.add(new DataAppDiagnostic(
                            "APP_VERSION_STALE", "base_app_version", "应用版本已变化，请重新读取应用后再修改", "error"));
                }
                if (StringUtils.isBlank(changeSet.getBaseSpecDigest())) {
                    diagnostics.add(new DataAppDiagnostic(
                            "APP_SPEC_DIGEST_REQUIRED", "base_spec_digest",
                            "更新应用必须携带当前 base_spec_digest", "error"));
                } else if (!changeSet.getBaseSpecDigest().equals(existing.getSpecDigest())) {
                    diagnostics.add(new DataAppDiagnostic(
                            "APP_SPEC_STALE", "base_spec_digest", "应用规格已变化，请重新读取应用后再修改", "error"));
                }
                driftDiagnostics(existing, diagnostics);
            }
        } else if (changeSet != null) {
            diagnostics.add(new DataAppDiagnostic(
                    "APP_NOT_FOUND", "app_id", "目标应用不存在；新建应用请直接提供 spec", "error"));
        }

        if (validation.spec() == null
                || diagnostics.stream().anyMatch(item -> "error".equals(item.getSeverity()))) {
            DataAppValidationResult result = failed("数据应用校验未通过");
            result.setWorkflowId(workflowId);
            result.setAppId(appId);
            result.setDiagnostics(diagnostics);
            return result;
        }

        DataAppSpec spec = validation.spec();
        boolean single = existing == null
                ? spec.getPages().size() == 1
                : "LOW_CODE_PAGE".equals(existing.getMenuType()) && spec.getPages().size() == 1;
        String mode = single ? "LOW_CODE_PAGE" : "LOW_CODE_APP";
        DataAppQuerySampler.SampleResult querySample = querySampler.sample(spec);
        diagnostics.addAll(querySample.diagnostics());
        if (querySample.diagnostics().stream()
                .anyMatch(item -> "error".equals(item.getSeverity()))) {
            DataAppValidationResult result = failed("代表性真实查询未通过");
            result.setWorkflowId(workflowId);
            result.setAppId(spec.getAppId());
            result.setDiagnostics(diagnostics);
            return result;
        }

        DataAppCompiler.CompiledFiles compiled = compiler.compile(spec, single, false);

        String metaDigest = metaDigest();
        DataAppManifest manifest = buildManifest(spec, existing, compiled, single, mode);
        manifest.setMetaDigest(metaDigest);
        Map<String, String> files = new LinkedHashMap<>(compiled.files());
        files.put(MANIFEST_FILE, JacksonUtil.toJson(manifest));
        List<String> deleted = deletedFiles(existing, manifest);
        DataAppPackage packageValue = new DataAppPackage(
                spec, manifest, files, deleted, existing);

        String specDigest = DataAppSupport.digestObject(spec);
        String candidateDigest = DataAppSupport.digest(specDigest + "\n"
                + DataAppSupport.sortedFileDigest(compiled.files()));
        String validationDigest = DataAppSupport.digest(candidateDigest + "\n" + metaDigest);

        DataAppValidationResult result = new DataAppValidationResult();
        result.setPassed(true);
        result.setStatus("validated");
        result.setWorkflowId(workflowId);
        result.setAppId(spec.getAppId());
        result.setAppVersion(manifest.getAppVersion());
        result.setMode(mode);
        result.setPreviousMode(existing == null ? null : existing.getMenuType());
        result.setCandidateDigest(candidateDigest);
        result.setValidationDigest(validationDigest);
        result.setSpecDigest(specDigest);
        result.setMetaDigest(metaDigest);
        result.setSpec(spec);
        result.setFiles(artifacts(compiled.files(), manifest));
        result.setDeletedFiles(deleted);
        result.setDiagnostics(diagnostics);
        result.setMenu(manifest);
        result.setPreview(previewMetadata(
                workflowId, chatId, previewToken, packageValue, querySample.samples()));
        result.setMessage("候选已通过服务端编译、Meta 校验和清单检查；请确认真实数据预览后发布。");
        return result;
    }

    public Map<String, String> previewFiles(DataAppPackage packageValue) {
        return previewFiles(packageValue, null, null, null);
    }

    public Map<String, String> previewFiles(DataAppPackage packageValue, String workflowId) {
        return previewFiles(packageValue, workflowId, null, null);
    }

    public Map<String, String> previewFiles(DataAppPackage packageValue,
                                            String workflowId,
                                            String previewToken) {
        return previewFiles(packageValue, workflowId, null, previewToken);
    }

    public Map<String, String> previewFiles(DataAppPackage packageValue,
                                            String workflowId,
                                            String chatId,
                                            String previewToken) {
        boolean single = "LOW_CODE_PAGE".equals(packageValue.getManifest().getMenuType());
        String previewApi = StringUtils.isBlank(workflowId) ? null
                : "/api/v1/dih/chat/workflows/" + workflowId + "/app-preview/files"
                + previewQuery(chatId, previewToken);
        return compiler.compile(packageValue.getSpec(), single, true, previewApi).files();
    }

    public DataAppPackage packageForValidation(DataAppValidationResult validation,
                                               String workflowId) {
        if (validation == null || validation.getSpec() == null || validation.getMenu() == null) {
            throw new IllegalArgumentException("校验结果缺少锁定候选");
        }
        boolean single = "LOW_CODE_PAGE".equals(validation.getMenu().getMenuType());
        DataAppCompiler.CompiledFiles compiled = compiler.compile(
                validation.getSpec(), single, false);
        Map<String, String> files = new LinkedHashMap<>(compiled.files());
        files.put(MANIFEST_FILE, JacksonUtil.toJson(validation.getMenu()));
        return new DataAppPackage(
                validation.getSpec(),
                validation.getMenu(),
                files,
                new ArrayList<>(validation.getDeletedFiles()),
                readManifest(validation.getAppId()).orElse(null));
    }

    public DataAppPackage revalidateSnapshot(DataAppPackage packageValue) {
        DataAppValidator.Result validation = validator.validate(packageValue.getSpec());
        if (!validation.passed()) {
            throw new IllegalStateException("应用候选已无法通过 Meta 校验："
                    + validation.diagnostics().stream()
                    .map(DataAppDiagnostic::getMessage).findFirst().orElse("未知错误"));
        }
        List<DataAppDiagnostic> drift = new ArrayList<>();
        driftDiagnostics(packageValue.getPreviousManifest(), drift);
        if (!drift.isEmpty()) {
            throw new IllegalStateException(drift.get(0).getMessage());
        }
        if (StringUtils.isNotBlank(packageValue.getManifest().getMetaDigest())
                && !Objects.equals(packageValue.getManifest().getMetaDigest(), metaDigest())) {
            throw new IllegalStateException("已发布 Meta 在确认后发生变化，必须重新生成应用方案并校验");
        }
        return packageValue;
    }

    public List<DataAppManifest> listManifests() {
        java.nio.file.Path base = java.nio.file.Paths.get(
                configService.configPath("data-app-discovery")).getParent();
        if (base == null || !java.nio.file.Files.isDirectory(base)) {
            return List.of();
        }
        try (java.util.stream.Stream<java.nio.file.Path> directories = java.nio.file.Files.list(base)) {
            return directories.filter(java.nio.file.Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("_config"))
                    .map(name -> name.substring(0, name.length() - "_config".length()))
                    .filter(DataAppSupport::validAppId)
                    .map(this::readManifest)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(DataAppManifest::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private DataAppSpec mergeChangeSet(DataAppManifest existing,
                                       DataAppChangeSet changes,
                                       String appId,
                                       List<DataAppDiagnostic> diagnostics) {
        DataAppSpec base = existing != null && existing.getSpec() != null
                ? existing.getSpec() : new DataAppSpec();
        DataAppSpec result = JacksonConfig.OBJECT_MAPPER.convertValue(
                JacksonUtil.toMap(base), DataAppSpec.class);
        result.setSchemaVersion(DataAppSpec.SCHEMA_VERSION);
        result.setAppId(appId);
        if (StringUtils.isNotBlank(changes.getName())) {
            result.setName(changes.getName());
        }
        if (changes.getDescription() != null) {
            result.setDescription(changes.getDescription());
        }
        if (changes.getMenu() != null) {
            result.setMenu(changes.getMenu());
        }
        if (StringUtils.isNotBlank(changes.getHomePageId())) {
            result.setHomePageId(changes.getHomePageId());
        }
        Map<String, DataAppSpec.DataAppPage> pages = new LinkedHashMap<>();
        if (result.getPages() != null) {
            result.getPages().forEach(page -> pages.put(page.getPageId(), page));
        }
        if (changes.getPutPages() != null) {
            changes.getPutPages().forEach(page -> pages.put(page.getPageId(), page));
        }
        if (changes.getDeletePageIds() != null) {
            changes.getDeletePageIds().forEach(pages::remove);
        }
        result.setPages(new ArrayList<>(pages.values()));
        if (StringUtils.isBlank(result.getHomePageId()) && !result.getPages().isEmpty()) {
            result.setHomePageId(result.getPages().get(0).getPageId());
        }
        if (changes.getDeletePageIds() != null
                && changes.getDeletePageIds().contains(result.getHomePageId())) {
            diagnostics.add(new DataAppDiagnostic(
                    "HOME_PAGE_DELETED", "home_page_id", "删除首页时必须同时指定新的 home_page_id", "error"));
        }
        if (result.getPages().isEmpty()) {
            diagnostics.add(new DataAppDiagnostic(
                    "LAST_PAGE_DELETE_FORBIDDEN", "pages", "应用至少保留一个页面；删除整个应用暂不支持", "error"));
        }
        return result;
    }

    private DataAppManifest buildManifest(DataAppSpec spec,
                                          DataAppManifest existing,
                                          DataAppCompiler.CompiledFiles compiled,
                                          boolean single,
                                          String mode) {
        DataAppManifest manifest = new DataAppManifest();
        manifest.setAppId(spec.getAppId());
        manifest.setAppVersion(existing == null || existing.getAppVersion() == null
                ? 1 : existing.getAppVersion() + 1);
        manifest.setMode(mode);
        manifest.setMenuType(compiled.menuType());
        manifest.setName(spec.getName());
        manifest.setDescription(spec.getDescription());
        manifest.setHomePageId(spec.getHomePageId());
        manifest.setSpec(spec);
        manifest.setSpecDigest(DataAppSupport.digestObject(spec));
        manifest.setPublishStatus("validated");
        manifest.setMenuId(existing == null ? null : existing.getMenuId());
        manifest.setMenuName(spec.getMenu() == null ? spec.getName() : spec.getMenu().getName());
        String now = Instant.now().toString();
        manifest.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        manifest.setUpdatedAt(now);
        List<DataAppManifest.DataAppPageRef> pages = new ArrayList<>();
        for (DataAppSpec.DataAppPage page : spec.getPages()) {
            DataAppManifest.DataAppPageRef ref = new DataAppManifest.DataAppPageRef();
            ref.setPageId(page.getPageId());
            ref.setTitle(page.getTitle());
            ref.setPath(DataAppSupport.fileNameForPage(spec.getAppId(), page.getPageId(), single));
            pages.add(ref);
        }
        manifest.setPages(pages);
        Map<String, String> digests = new LinkedHashMap<>();
        compiled.files().forEach((path, content) ->
                digests.put(path, DataAppSupport.digest(content)));
        manifest.setFiles(digests);
        if (existing != null && "LOW_CODE_PAGE".equals(existing.getMenuType()) && !single
                && configService.fileExistsInConfigPath(spec.getAppId(), "index.json")) {
            manifest.setCompatibilityFiles(new ArrayList<>(List.of("index.json")));
        }
        return manifest;
    }

    private List<String> deletedFiles(DataAppManifest existing, DataAppManifest next) {
        if (existing == null) {
            return List.of();
        }
        List<String> deleted = new ArrayList<>();
        for (DataAppManifest.DataAppPageRef page : existing.getPages()) {
            if (page.getPath() != null
                    && !next.getFiles().containsKey(page.getPath())
                    && !next.getCompatibilityFiles().contains(page.getPath())) {
                deleted.add(page.getPath());
            }
        }
        return deleted;
    }

    private void driftDiagnostics(DataAppManifest manifest,
                                  List<DataAppDiagnostic> diagnostics) {
        if (manifest == null) {
            return;
        }
        for (Map.Entry<String, String> entry : manifest.getFiles().entrySet()) {
            if (!configService.fileExistsInConfigPath(manifest.getAppId(), entry.getKey())) {
                diagnostics.add(new DataAppDiagnostic(
                        "APP_FILE_MISSING", entry.getKey(), "受管文件缺失，拒绝覆盖", "error"));
                continue;
            }
            String content = configService.readFile(manifest.getAppId(), entry.getKey());
            if (!entry.getValue().equals(DataAppSupport.digest(content))) {
                diagnostics.add(new DataAppDiagnostic(
                        "APP_FILE_DRIFT", entry.getKey(), "受管文件已被人工修改，拒绝覆盖", "error"));
            }
        }
    }

    private boolean hasLegacyContent(String appId) {
        java.nio.file.Path directory = java.nio.file.Paths.get(configService.configPath(appId));
        if (!java.nio.file.Files.isDirectory(directory)) {
            return false;
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(directory)) {
            return files.anyMatch(java.nio.file.Files::isRegularFile);
        } catch (Exception ignored) {
            return true;
        }
    }

    private List<DataAppArtifact> artifacts(Map<String, String> files, DataAppManifest manifest) {
        List<DataAppArtifact> artifacts = new ArrayList<>();
        files.forEach((path, content) -> artifacts.add(new DataAppArtifact(
                path, DataAppSupport.digest(content), content.getBytes(StandardCharsets.UTF_8).length, true)));
        artifacts.add(new DataAppArtifact(
                MANIFEST_FILE,
                manifest.getFiles().isEmpty() ? DataAppSupport.digest(JacksonUtil.toJson(manifest))
                        : DataAppSupport.digest(JacksonUtil.toJson(manifest)),
                JacksonUtil.toJson(manifest).getBytes(StandardCharsets.UTF_8).length,
                false));
        return artifacts;
    }

    private Map<String, Object> previewMetadata(String workflowId,
                                                String chatId,
                                                String previewToken,
                                                DataAppPackage packageValue,
                                                Map<String, Object> samples) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("workflowId", workflowId);
        preview.put("siteUrl", "/api/v1/dih/chat/workflows/" + workflowId
                + "/app-preview/site"
                + previewQuery(chatId, previewToken));
        preview.put("expiresInSeconds", 1800);
        preview.put("appId", packageValue.getSpec().getAppId());
        preview.put("pages", packageValue.getSpec().getPages().stream()
                .map(page -> Map.of("pageId", page.getPageId(), "title", page.getTitle()))
                .toList());
        preview.put("mode", packageValue.getManifest().getMenuType());
        preview.put("querySamples", samples);
        return preview;
    }

    private String previewQuery(String chatId, String previewToken) {
        List<String> query = new ArrayList<>();
        if (StringUtils.isNotBlank(chatId)) {
            query.add("chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8));
        }
        if (StringUtils.isNotBlank(previewToken)) {
            query.add("token=" + URLEncoder.encode(previewToken, StandardCharsets.UTF_8));
        }
        return query.isEmpty() ? "" : "?" + String.join("&", query);
    }

    private DataAppValidationResult failed(String message) {
        DataAppValidationResult result = new DataAppValidationResult();
        result.setPassed(false);
        result.setStatus("invalid");
        result.setMessage(message);
        return result;
    }

    private String metaDigest() {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (DataEntity entity : metaDataService.getAllDataEntity().stream()
                .sorted(Comparator.comparing(DataEntity::getName)).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entity.getName());
            item.put("table", entity.getTableName());
            List<Map<String, Object>> attributes = new ArrayList<>();
            for (DataAttribute attribute : metaDataService.getAllDataAttributeByEntity(entity).stream()
                    .sorted(Comparator.comparing(DataAttribute::getName)).toList()) {
                Map<String, Object> attributeItem = new LinkedHashMap<>();
                attributeItem.put("name", attribute.getName());
                attributeItem.put("column", attribute.getColumnName());
                attributeItem.put("type", attribute.getColumnType());
                attributeItem.put("operators", attribute.getOperators());
                attributeItem.put("label", attribute.getLabel());
                attributeItem.put("display_type", attribute.getDisplayType());
                attributeItem.put("search_type", attribute.getSearchType());
                attributeItem.put("retrieval_type", attribute.getRetrievalType());
                attributeItem.put("enums", attribute.getEnums());
                attributeItem.put("mapping", attribute.getMapping());
                attributeItem.put("required", attribute.isRequired());
                attributeItem.put("must_candidate", attribute.isMustCandidate());
                attributeItem.put("auto_complete", attribute.isAutoComplete());
                attributeItem.put("copyable", attribute.isCopyable());
                attributeItem.put("link_template", attribute.getLinkTemplate());
                attributeItem.put("link_condition", attribute.getLinkCondition());
                attributes.add(attributeItem);
            }
            item.put("attributes", attributes);
            entities.add(item);
        }
        return DataAppSupport.digestObject(entities);
    }
}
