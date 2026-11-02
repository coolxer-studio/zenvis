package com.coolxer.service.dih.dashboard;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.dih.dashboard.DashboardArtifact;
import com.coolxer.model.dih.dashboard.DashboardChangeSet;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class DashboardPackageService {

    private final ConfigService configService;
    private final MetaDataService metaDataService;
    private final DashboardRepository dashboardRepository;
    private final DashboardValidator validator;
    private final DashboardCompiler compiler;
    private final DashboardQuerySampler querySampler;

    public DashboardPackageService(ConfigService configService,
                                   MetaDataService metaDataService,
                                   DashboardRepository dashboardRepository,
                                   DashboardValidator validator,
                                   DashboardCompiler compiler,
                                   DashboardQuerySampler querySampler) {
        this.configService = configService;
        this.metaDataService = metaDataService;
        this.dashboardRepository = dashboardRepository;
        this.validator = validator;
        this.compiler = compiler;
        this.querySampler = querySampler;
    }

    public Optional<DashboardManifest> readManifest(String dashboardId) {
        String normalized = DashboardSupport.normalizeDashboardId(dashboardId);
        if (!DashboardSupport.validDashboardId(normalized)) {
            return Optional.empty();
        }
        for (String type : List.of("html-page", normalized)) {
            String path = "html-page".equals(type) ? normalized + ".manifest.json" : "_dashboard.json";
            if (!configService.fileExistsInConfigPath(type, path)) {
                continue;
            }
            String content = configService.readFile(type, path);
            if (StringUtils.isBlank(content)) {
                return Optional.empty();
            }
            try {
                return Optional.of(JacksonConfig.OBJECT_MAPPER.readValue(content, DashboardManifest.class));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean manifestFileExists(String dashboardId) {
        String normalized = DashboardSupport.normalizeDashboardId(dashboardId);
        return DashboardSupport.validDashboardId(normalized)
                && (configService.fileExistsInConfigPath("html-page", normalized + ".manifest.json")
                || configService.fileExistsInConfigPath(normalized, "_dashboard.json"));
    }

    public DashboardValidationResult build(DashboardSpec requestedSpec,
                                           DashboardChangeSet changeSet,
                                           String workflowId) {
        return build(requestedSpec, changeSet, workflowId, null, null);
    }

    public DashboardValidationResult build(DashboardSpec requestedSpec,
                                           DashboardChangeSet changeSet,
                                           String workflowId,
                                           String chatId,
                                           String previewToken) {
        List<DashboardDiagnostic> diagnostics = new ArrayList<>();
        String dashboardId = DashboardSupport.normalizeDashboardId(
                changeSet != null && StringUtils.isNotBlank(changeSet.getDashboardId())
                        ? changeSet.getDashboardId()
                        : requestedSpec == null ? null : requestedSpec.getDashboardId());
        if (!DashboardSupport.validDashboardId(dashboardId)) {
            DashboardValidationResult result = failed("dashboard_id 无效");
            result.setWorkflowId(workflowId);
            result.getDiagnostics().add(new DashboardDiagnostic(
                    "DASHBOARD_ID_INVALID", "dashboard_id", "dashboard_id 无效", "error"));
            return result;
        }
        boolean manifestExists = manifestFileExists(dashboardId);
        Optional<DashboardManifest> existingOptional = readManifest(dashboardId);
        DashboardManifest existing = existingOptional.orElse(null);
        if (manifestExists && existing == null) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_MANIFEST_INVALID", "manifest",
                    "大屏清单存在但无法解析，拒绝自动接管或覆盖", "error"));
        } else if (!manifestExists && hasLegacyContent(dashboardId)) {
            diagnostics.add(new DashboardDiagnostic(
                    "LEGACY_DASHBOARD_READ_ONLY", "dashboard_id",
                    "该 dashboard_id 下已有无清单的历史看板，只能只读查看，不能自动接管或覆盖", "error"));
        }

        DashboardSpec candidate = changeSet == null
                ? requestedSpec : changeSet.getSpec();
        if (candidate == null) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_SPEC_REQUIRED", "spec", "必须提供完整 DashboardSpec", "error"));
        } else if (changeSet != null
                && StringUtils.isNotBlank(changeSet.getDashboardId())
                && !dashboardId.equals(DashboardSupport.normalizeDashboardId(candidate.getDashboardId()))) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_ID_MISMATCH", "spec.dashboard_id",
                    "change_set.dashboard_id 必须与 spec.dashboard_id 一致", "error"));
        }

        DashboardValidator.Result validation = validator.validate(candidate);
        diagnostics.addAll(validation.diagnostics());
        if (existing != null) {
            validateExisting(existing, candidate, changeSet, diagnostics);
        } else if (changeSet != null) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_NOT_FOUND", "dashboard_id",
                    "目标大屏不存在；新建大屏请直接提供 spec", "error"));
        }

        if (validation.spec() == null || hasErrors(diagnostics)) {
            DashboardValidationResult result = failed("数据大屏校验未通过");
            result.setWorkflowId(workflowId);
            result.setDashboardId(dashboardId);
            result.setDiagnostics(diagnostics);
            return result;
        }

        DashboardSpec spec = validation.spec();
        DashboardQuerySampler.SampleResult sampled = querySampler.sample(spec);
        diagnostics.addAll(sampled.diagnostics());
        if (sampled.diagnostics().stream().anyMatch(item -> "error".equals(item.getSeverity()))) {
            DashboardValidationResult result = failed("代表性真实查询未通过");
            result.setWorkflowId(workflowId);
            result.setDashboardId(spec.getDashboardId());
            result.setDiagnostics(diagnostics);
            return result;
        }

        DashboardCompiler.CompiledFiles compiled = compiler.compile(spec, false);
        String specDigest = DashboardSupport.digestObject(spec);
        String metaDigest = metaDigest();
        String fileDigest = DashboardSupport.sortedFileDigest(
                digestMap(compiled.files()));
        DashboardManifest manifest = manifest(spec, existing, compiled, specDigest, metaDigest, fileDigest);
        Map<String, String> files = new LinkedHashMap<>(compiled.files());
        files.put(manifestPath(spec), JacksonUtil.toJson(manifest));
        DashboardPackage packageValue = new DashboardPackage(
                spec, manifest, files, manifestPath(spec), existing);

        String candidateDigest = DashboardSupport.digestObject(Map.of(
                "spec_digest", specDigest,
                "file_digest", fileDigest,
                "base_version", existing == null ? 0 : existing.getDashboardVersion(),
                "base_spec_digest", existing == null ? "" : existing.getSpecDigest()));
        String validationDigest = DashboardSupport.digestObject(Map.of(
                "candidate_digest", candidateDigest,
                "meta_digest", metaDigest,
                "samples", sampled.samples(),
                "diagnostics", diagnostics.stream()
                        .map(item -> item.getCode() + ":" + item.getPath()).toList()));

        DashboardValidationResult result = new DashboardValidationResult();
        result.setPassed(true);
        result.setStatus("validated");
        result.setWorkflowId(workflowId);
        result.setDashboardId(spec.getDashboardId());
        result.setDashboardVersion(manifest.getDashboardVersion());
        result.setMode(spec.getMode());
        result.setPreviousMode(existing == null ? null : existing.getMode());
        result.setCandidateDigest(candidateDigest);
        result.setValidationDigest(validationDigest);
        result.setSpecDigest(specDigest);
        result.setMetaDigest(metaDigest);
        result.setFileDigest(fileDigest);
        result.setSpec(spec);
        result.setFiles(artifacts(compiled.files(), manifest, manifestPath(spec)));
        result.setDiagnostics(diagnostics);
        result.setManifest(manifest);
        result.setPreview(previewMetadata(workflowId, chatId, previewToken, spec, sampled.samples()));
        result.setMessage("候选已通过服务端编译、Meta 校验、真实查询取样和摘要锁定；请确认真实数据预览后发布。");
        return result;
    }

    public Map<String, String> previewFiles(DashboardPackage packageValue,
                                            String workflowId,
                                            String chatId,
                                            String previewToken) {
        String filesApi = "/api/v1/dih/chat/workflows/" + workflowId
                + "/dashboard-preview/files" + previewQuery(chatId, previewToken);
        return compiler.previewFiles(packageValue.getSpec(), filesApi);
    }

    public String previewContentPath(DashboardSpec spec) {
        return compiler.previewContentPath(spec);
    }

    public DashboardPackage packageForValidation(DashboardValidationResult validation) {
        if (validation == null || validation.getSpec() == null || validation.getManifest() == null) {
            throw new IllegalArgumentException("校验结果缺少锁定候选");
        }
        DashboardCompiler.CompiledFiles compiled = compiler.compile(validation.getSpec(), false);
        Map<String, String> files = new LinkedHashMap<>(compiled.files());
        files.put(manifestPath(validation.getSpec()), JacksonUtil.toJson(validation.getManifest()));
        return new DashboardPackage(validation.getSpec(), validation.getManifest(), files,
                manifestPath(validation.getSpec()),
                readManifest(validation.getDashboardId()).orElse(null));
    }

    public DashboardPackage revalidateSnapshot(DashboardPackage packageValue) {
        DashboardValidator.Result validation = validator.validate(packageValue.getSpec());
        if (!validation.passed()) {
            throw new IllegalStateException("大屏候选已无法通过 Meta 校验："
                    + validation.diagnostics().stream()
                    .map(DashboardDiagnostic::getMessage).findFirst().orElse("未知错误"));
        }
        List<DashboardDiagnostic> drift = new ArrayList<>();
        driftDiagnostics(packageValue.getPreviousManifest(), drift);
        if (!drift.isEmpty()) {
            throw new IllegalStateException(drift.get(0).getMessage());
        }
        DashboardManifest previous = packageValue.getPreviousManifest();
        if (previous != null) {
            DashboardManifest current = readManifest(packageValue.getSpec().getDashboardId())
                    .orElseThrow(() -> new IllegalStateException(
                            "大屏清单在确认后发生变化，必须重新读取并生成新方案"));
            if (!Objects.equals(previous.getDashboardVersion(), current.getDashboardVersion())
                    || !Objects.equals(previous.getSpecDigest(), current.getSpecDigest())
                    || !Objects.equals(previous.getFileDigest(), current.getFileDigest())
                    || !Objects.equals(previous.getMode(), current.getMode())) {
                throw new IllegalStateException("大屏版本或摘要已变化，必须重新读取并生成新方案");
            }
            if (!Objects.equals(previous.getSpecDigest(),
                    DashboardSupport.digestObject(packageValue.getSpec()))) {
                throw new IllegalStateException("大屏规格在确认后发生变化，必须重新读取并生成新方案");
            }
            driftDiagnostics(current, drift);
            if (!drift.isEmpty()) {
                throw new IllegalStateException(drift.get(0).getMessage());
            }
        } else if (manifestFileExists(packageValue.getSpec().getDashboardId())
                || hasDashboardRecord(packageValue.getSpec().getDashboardId())) {
            throw new IllegalStateException("大屏在确认后已被创建，必须重新读取并生成新方案");
        }
        if (StringUtils.isNotBlank(packageValue.getManifest().getMetaDigest())
                && !Objects.equals(packageValue.getManifest().getMetaDigest(), metaDigest())) {
            throw new IllegalStateException("已发布 Meta 在确认后发生变化，必须重新生成大屏方案并校验");
        }
        return packageValue;
    }

    public List<DashboardManifest> listManifests() {
        List<DashboardManifest> result = new ArrayList<>();
        Path base = Paths.get(configService.configPath("dashboard-discovery")).getParent();
        if (base == null || !Files.isDirectory(base)) {
            return result;
        }
        try (var entries = Files.list(base)) {
            entries.filter(Files::isDirectory).forEach(directory -> {
                String name = directory.getFileName().toString();
                if ("html-page_config".equals(name)) {
                    try (var files = Files.list(directory)) {
                        files.filter(Files::isRegularFile)
                                .map(path -> path.getFileName().toString())
                                .filter(file -> file.endsWith(".manifest.json"))
                                .forEach(file -> readManifest(file.substring(0, file.length() - ".manifest.json".length()))
                                        .ifPresent(result::add));
                    } catch (Exception ignored) {
                    }
                } else if (name.endsWith("_config")) {
                    String id = name.substring(0, name.length() - "_config".length());
                    if (DashboardSupport.validDashboardId(id)) {
                        readManifest(id).ifPresent(result::add);
                    }
                }
            });
        } catch (Exception ignored) {
        }
        result.sort(Comparator.comparing(DashboardManifest::getDashboardId,
                Comparator.nullsLast(String::compareTo)));
        return result;
    }

    public Map<String, Object> legacyReadOnly(String dashboardId) {
        String normalized = DashboardSupport.normalizeDashboardId(dashboardId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard_id", normalized);
        result.put("read_only", true);
        result.put("managed", false);
        result.put("compatibility", "legacy");
        result.put("message", "该大屏没有受管清单，仅可只读查看；不会被新流程接管或覆盖。");
        Dashboard dashboard = dashboardRepository.findBySource("dashboard:" + normalized).stream()
                .findFirst().orElse(null);
        if (dashboard != null) {
            result.put("dashboard_record_id", dashboard.getId());
            result.put("dashboard_type", dashboard.getType() == null ? null : dashboard.getType().name());
            result.put("code", dashboard.getCode());
            result.put("html_path", dashboard.getHtmlPath());
            result.put("config_index", dashboard.getConfigIndex());
        }
        result.put("has_legacy_content", hasLegacyContent(normalized));
        return result;
    }

    private void validateExisting(DashboardManifest existing,
                                  DashboardSpec candidate,
                                  DashboardChangeSet changeSet,
                                  List<DashboardDiagnostic> diagnostics) {
        if (changeSet == null) {
            diagnostics.add(new DashboardDiagnostic(
                    "CHANGESET_REQUIRED", "change_set", "更新受管大屏必须提供 change_set", "error"));
            return;
        }
        if (candidate == null) {
            return;
        }
        if (!Objects.equals(existing.getMode(), candidate.getMode())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_MODE_LOCKED", "mode",
                    "大屏模式在首次发布后锁定；切换 HTML/低代码模式必须使用新的 dashboard_id", "error"));
        }
        if (changeSet.getBaseDashboardVersion() == null
                || !changeSet.getBaseDashboardVersion().equals(existing.getDashboardVersion())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_VERSION_STALE", "base_dashboard_version",
                    "大屏版本已变化，请重新读取后再修改", "error"));
        }
        if (StringUtils.isBlank(changeSet.getBaseSpecDigest())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_SPEC_DIGEST_REQUIRED", "base_spec_digest",
                    "更新大屏必须携带当前 base_spec_digest", "error"));
        } else if (!changeSet.getBaseSpecDigest().equals(existing.getSpecDigest())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_SPEC_STALE", "base_spec_digest",
                    "大屏规格已变化，请重新读取后再修改", "error"));
        }
        if (StringUtils.isBlank(changeSet.getBaseFileDigest())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_FILE_DIGEST_REQUIRED", "base_file_digest",
                    "更新大屏必须携带当前 base_file_digest", "error"));
        } else if (!changeSet.getBaseFileDigest().equals(existing.getFileDigest())) {
            diagnostics.add(new DashboardDiagnostic(
                    "DASHBOARD_FILE_STALE", "base_file_digest",
                    "大屏文件摘要已变化，请重新读取后再修改", "error"));
        }
        driftDiagnostics(existing, diagnostics);
    }

    private DashboardManifest manifest(DashboardSpec spec,
                                       DashboardManifest existing,
                                       DashboardCompiler.CompiledFiles compiled,
                                       String specDigest,
                                       String metaDigest,
                                       String fileDigest) {
        DashboardManifest manifest = new DashboardManifest();
        manifest.setDashboardId(spec.getDashboardId());
        manifest.setDashboardVersion(existing == null || existing.getDashboardVersion() == null
                ? 1 : existing.getDashboardVersion() + 1);
        manifest.setName(spec.getName());
        manifest.setDescription(spec.getDescription());
        manifest.setMode(spec.getMode());
        manifest.setTheme(spec.getTheme());
        manifest.setDashboardType(compiled.dashboardType());
        manifest.setSpecDigest(specDigest);
        manifest.setMetaDigest(metaDigest);
        manifest.setFileDigest(fileDigest);
        manifest.setPublishStatus("validated");
        manifest.setSpec(spec);
        manifest.setConfigIndex(DashboardSupport.lowCodeMode(spec) ? DashboardSupport.configIndex(spec) : null);
        manifest.setHtmlPath(DashboardSupport.htmlMode(spec) ? DashboardSupport.htmlPath(spec) : null);
        manifest.setFiles(digestMap(compiled.files()));
        Dashboard dashboard = dashboardRepository.findBySource("dashboard:" + spec.getDashboardId())
                .stream().findFirst().orElse(null);
        if (dashboard != null) {
            manifest.setDashboardRecordId(dashboard.getId());
            manifest.setDashboardCode(dashboard.getCode());
        } else {
            manifest.setDashboardCode(spec.getDashboardId());
        }
        String now = Instant.now().toString();
        manifest.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        manifest.setUpdatedAt(now);
        return manifest;
    }

    private Map<String, String> digestMap(Map<String, String> files) {
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((path, content) -> result.put(path, DashboardSupport.digest(content)));
        return result;
    }

    private List<DashboardArtifact> artifacts(Map<String, String> files,
                                              DashboardManifest manifest,
                                              String manifestPath) {
        List<DashboardArtifact> result = new ArrayList<>();
        files.forEach((path, content) -> result.add(new DashboardArtifact(
                path, DashboardSupport.digest(content),
                content.getBytes(StandardCharsets.UTF_8).length, true)));
        String manifestJson = JacksonUtil.toJson(manifest);
        result.add(new DashboardArtifact(manifestPath, DashboardSupport.digest(manifestJson),
                manifestJson.getBytes(StandardCharsets.UTF_8).length, false));
        return result;
    }

    private Map<String, Object> previewMetadata(String workflowId,
                                                String chatId,
                                                String previewToken,
                                                DashboardSpec spec,
                                                Map<String, Object> samples) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("workflowId", workflowId);
        preview.put("htmlUrl", "/api/v1/dih/chat/workflows/" + workflowId
                + "/dashboard-preview" + previewQuery(chatId, previewToken));
        preview.put("filesUrl", "/api/v1/dih/chat/workflows/" + workflowId
                + "/dashboard-preview/files" + previewQuery(chatId, previewToken));
        preview.put("expiresInSeconds", 1800);
        preview.put("dashboardId", spec.getDashboardId());
        preview.put("mode", spec.getMode());
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

    private void driftDiagnostics(DashboardManifest manifest,
                                  List<DashboardDiagnostic> diagnostics) {
        if (manifest == null) {
            return;
        }
        String type = DashboardSupport.htmlMode(manifest.getSpec())
                ? "html-page" : manifest.getDashboardId();
        for (Map.Entry<String, String> entry : manifest.getFiles().entrySet()) {
            if (!configService.fileExistsInConfigPath(type, entry.getKey())) {
                diagnostics.add(new DashboardDiagnostic(
                        "DASHBOARD_FILE_MISSING", entry.getKey(), "受管文件缺失，拒绝覆盖", "error"));
                continue;
            }
            String content = configService.readFile(type, entry.getKey());
            if (!entry.getValue().equals(DashboardSupport.digest(content))) {
                diagnostics.add(new DashboardDiagnostic(
                        "DASHBOARD_FILE_DRIFT", entry.getKey(), "受管文件已被人工修改，拒绝覆盖", "error"));
            }
        }
    }

    private boolean hasLegacyContent(String dashboardId) {
        Path html = Paths.get(configService.configPath("html-page"))
                .resolve(dashboardId + ".html").toAbsolutePath().normalize();
        if (Files.isRegularFile(html)) {
            return true;
        }
        Path lowCode = Paths.get(configService.configPath(dashboardId)).toAbsolutePath().normalize();
        if (Files.isDirectory(lowCode)) {
            try (var entries = Files.list(lowCode)) {
                if (entries.anyMatch(Files::isRegularFile)) {
                    return true;
                }
            } catch (Exception ignored) {
                return true;
            }
        }
        return hasDashboardRecord(dashboardId);
    }

    private boolean hasDashboardRecord(String dashboardId) {
        try {
            return dashboardRepository.findByCode(dashboardId).isPresent()
                    || dashboardRepository.findBySource("dashboard:" + dashboardId).stream()
                    .findFirst().isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String manifestPath(DashboardSpec spec) {
        return DashboardSupport.manifestPath(spec);
    }

    private boolean hasErrors(List<DashboardDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(item -> "error".equals(item.getSeverity()));
    }

    private DashboardValidationResult failed(String message) {
        DashboardValidationResult result = new DashboardValidationResult();
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
                attributes.add(attributeItem);
            }
            item.put("attributes", attributes);
            entities.add(item);
        }
        return DashboardSupport.digestObject(entities);
    }
}
