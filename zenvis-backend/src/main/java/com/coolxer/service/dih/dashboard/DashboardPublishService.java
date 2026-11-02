package com.coolxer.service.dih.dashboard;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardArtifact;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.service.config.ConfigService;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class DashboardPublishService {

    private final ConfigService configService;
    private final DashboardRepository dashboardRepository;
    private final DashboardPackageService packageService;

    public DashboardPublishService(ConfigService configService,
                                   DashboardRepository dashboardRepository,
                                   DashboardPackageService packageService) {
        this.configService = configService;
        this.dashboardRepository = dashboardRepository;
        this.packageService = packageService;
    }

    public DashboardApplyResult apply(DashboardPreviewRegistry.PreviewSession session,
                                      String candidateDigest,
                                      String validationDigest) {
        DashboardApplyResult result = new DashboardApplyResult();
        DashboardPackage packageValue = session.packageValue();
        if (!Objects.equals(candidateDigest, session.candidateDigest())
                || !Objects.equals(validationDigest, session.validationDigest())) {
            return blocked(result, "CANDIDATE_MISMATCH", "candidate_digest",
                    "发布摘要与工作流锁定候选不一致");
        }
        try {
            packageService.revalidateSnapshot(packageValue);
        } catch (RuntimeException e) {
            return blocked(result, "DASHBOARD_SNAPSHOT_CHANGED", "spec", e.getMessage());
        }

        DashboardSpec spec = packageValue.getSpec();
        String configType = DashboardSupport.configType(spec);
        Map<String, Snapshot> fileSnapshots = snapshot(configType, packageValue);
        Dashboard dashboard = findSourceDashboard(spec.getDashboardId());
        DashboardSnapshot dashboardSnapshot = DashboardSnapshot.of(dashboard);
        try {
            conflictCheck(spec, dashboard);
            writeFiles(configType, packageValue);
            readBackFiles(configType, packageValue);
            dashboard = upsertDashboard(spec, dashboard);
            readBackDashboard(spec, dashboard);

            DashboardManifest published = packageValue.getManifest();
            published.setPublishStatus("published");
            published.setDashboardRecordId(dashboard.getId());
            published.setDashboardCode(dashboard.getCode());
            published.setUpdatedAt(Instant.now().toString());
            writeFile(configType, packageValue.getManifestPath(), JacksonUtil.toJson(published));
            String expectedManifest = JacksonUtil.toJson(published);
            String actualManifest = configService.readFile(configType, packageValue.getManifestPath());
            if (!jsonEquals(expectedManifest, actualManifest)) {
                throw new IllegalStateException("发布清单读回不一致");
            }
            packageValue.getFiles().put(packageValue.getManifestPath(), expectedManifest);
        } catch (RuntimeException e) {
            restore(configType, fileSnapshots);
            restoreDashboard(dashboardSnapshot, "dashboard:" + spec.getDashboardId());
            log.warn("数据大屏发布失败并已回滚，dashboardId={}", spec.getDashboardId(), e);
            return blocked(result, "DASHBOARD_WRITE_FAILED", "files",
                    "大屏发布失败，配置和 Dashboard 已回滚：" + e.getMessage());
        }

        DashboardManifest manifest = packageValue.getManifest();
        result.setStatus("verified");
        result.setApplied(true);
        result.setDashboardId(spec.getDashboardId());
        result.setDashboardVersion(manifest.getDashboardVersion());
        result.setMode(spec.getMode());
        result.setFileDigest(manifest.getFileDigest());
        result.setDashboardRecordId(dashboard.getId());
        result.setDashboardType(manifest.getDashboardType());
        result.setDashboardCode(dashboard.getCode());
        result.getFiles().addAll(artifacts(packageValue));
        Map<String, Object> dashboardValue = new LinkedHashMap<>();
        dashboardValue.put("id", dashboard.getId());
        dashboardValue.put("name", dashboard.getName());
        dashboardValue.put("code", dashboard.getCode());
        dashboardValue.put("type", dashboard.getType().name());
        dashboardValue.put("source", dashboard.getSource());
        dashboardValue.put("html_path", dashboard.getHtmlPath());
        dashboardValue.put("config_index", dashboard.getConfigIndex());
        dashboardValue.put("is_default", Boolean.TRUE.equals(dashboard.getIsDefault()));
        result.setDashboard(dashboardValue);
        result.setMessage("大屏配置与 Dashboard 已一次发布并通过读回校验。");
        return result;
    }

    private void conflictCheck(DashboardSpec spec, Dashboard dashboard) {
        Dashboard byCode = dashboardRepository.findByCode(spec.getDashboardId()).orElse(null);
        if (byCode != null && (dashboard == null || !Objects.equals(byCode.getId(), dashboard.getId()))) {
            throw new IllegalStateException("dashboard code 已被其他看板占用：" + spec.getDashboardId());
        }
        if (dashboard != null && dashboard.getType() != dashboardType(spec.getMode())) {
            throw new IllegalStateException("Dashboard 类型与锁定模式不一致，拒绝覆盖");
        }
    }

    private Dashboard findSourceDashboard(String dashboardId) {
        List<Dashboard> dashboards = dashboardRepository.findBySource("dashboard:" + dashboardId);
        if (dashboards.size() > 1) {
            throw new IllegalStateException("存在多个同源 Dashboard，拒绝自动发布");
        }
        return dashboards.isEmpty() ? null : dashboards.get(0);
    }

    private Dashboard upsertDashboard(DashboardSpec spec, Dashboard existing) {
        Dashboard dashboard = existing == null ? new Dashboard() : existing;
        dashboard.setName(spec.getName());
        dashboard.setCode(spec.getDashboardId());
        dashboard.setSource("dashboard:" + spec.getDashboardId());
        dashboard.setType(dashboardType(spec.getMode()));
        if (DashboardSupport.htmlMode(spec)) {
            dashboard.setHtmlPath(DashboardSupport.htmlPath(spec));
            dashboard.setConfigIndex(null);
        } else {
            dashboard.setConfigIndex(DashboardSupport.configIndex(spec));
            dashboard.setHtmlPath(null);
        }
        if (dashboard.getIsDefault() == null) {
            dashboard.setIsDefault(false);
        }
        return dashboardRepository.save(dashboard);
    }

    private void readBackDashboard(DashboardSpec spec, Dashboard dashboard) {
        Dashboard persisted = dashboardRepository.findById(dashboard.getId())
                .orElseThrow(() -> new IllegalStateException("Dashboard 读回不存在"));
        if (!Objects.equals(spec.getName(), persisted.getName())
                || !Objects.equals(spec.getDashboardId(), persisted.getCode())
                || !Objects.equals("dashboard:" + spec.getDashboardId(), persisted.getSource())
                || persisted.getType() != dashboardType(spec.getMode())) {
            throw new IllegalStateException("Dashboard 身份字段读回不一致");
        }
        if (DashboardSupport.htmlMode(spec)
                && !Objects.equals(DashboardSupport.htmlPath(spec), persisted.getHtmlPath())) {
            throw new IllegalStateException("Dashboard HTML 路径读回不一致");
        }
        if (DashboardSupport.lowCodeMode(spec)
                && !Objects.equals(DashboardSupport.configIndex(spec), persisted.getConfigIndex())) {
            throw new IllegalStateException("Dashboard 低代码配置索引读回不一致");
        }
    }

    private DashboardType dashboardType(String mode) {
        return DashboardSpec.MODE_HTML.equals(mode) ? DashboardType.HTML_PAGE : DashboardType.LOW_CODE_PAGE;
    }

    private void writeFiles(String configType, DashboardPackage packageValue) {
        for (Map.Entry<String, String> entry : packageValue.getFiles().entrySet()) {
            writeFile(configType, entry.getKey(), entry.getValue());
        }
    }

    private void writeFile(String configType, String path, String content) {
        if (StringUtils.isBlank(path) || path.contains("..") || path.startsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("非法配置路径：" + path);
        }
        ConfigDto dto = new ConfigDto();
        dto.setFileName(path);
        dto.setText(content);
        if (configService.fileExistsInConfigPath(configType, path)) {
            configService.modifyConfig(configType, dto);
            if (!Objects.equals(content, configService.readFile(configType, path))) {
                throw new IllegalStateException("配置写入失败：" + path);
            }
            return;
        }
        if (!configService.ensureRootPath(configType)) {
            throw new IllegalStateException("无法创建配置根目录：" + configType);
        }
        ensureParentDirectory(configType, path);
        if (!configService.addFile(configType, path)) {
            throw new IllegalStateException("无法创建文件：" + path);
        }
        configService.modifyConfig(configType, dto);
        if (!Objects.equals(content, configService.readFile(configType, path))) {
            throw new IllegalStateException("配置写入失败：" + path);
        }
    }

    private void ensureParentDirectory(String configType, String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return;
        }
        String parent = path.substring(0, index);
        try {
            Files.createDirectories(Paths.get(configService.configPath(configType)).resolve(parent));
        } catch (Exception e) {
            throw new IllegalStateException("无法创建配置目录：" + parent, e);
        }
    }

    private void readBackFiles(String configType, DashboardPackage packageValue) {
        for (Map.Entry<String, String> entry : packageValue.getFiles().entrySet()) {
            String content = configService.readFile(configType, entry.getKey());
            boolean valid = entry.getKey().endsWith(".json")
                    ? jsonEquals(entry.getValue(), content)
                    : Objects.equals(entry.getValue(), content);
            if (!valid) {
                throw new IllegalStateException("配置文件读回不一致：" + entry.getKey());
            }
        }
    }

    private Map<String, Snapshot> snapshot(String configType, DashboardPackage packageValue) {
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>(packageValue.getFiles().keySet());
        paths.add(packageValue.getManifestPath());
        for (String path : paths) {
            if (configService.fileExistsInConfigPath(configType, path)) {
                snapshots.put(path, new Snapshot(true, configService.readFile(configType, path)));
            } else {
                snapshots.put(path, new Snapshot(false, null));
            }
        }
        return snapshots;
    }

    private void restore(String configType, Map<String, Snapshot> snapshots) {
        for (Map.Entry<String, Snapshot> entry : snapshots.entrySet()) {
            try {
                if (entry.getValue().exists()) {
                    writeFile(configType, entry.getKey(), entry.getValue().content());
                } else if (configService.fileExistsInConfigPath(configType, entry.getKey())) {
                    configService.deleteEntry(configType, entry.getKey(), false);
                }
            } catch (RuntimeException restoreError) {
                log.error("恢复大屏配置失败：{}", entry.getKey(), restoreError);
            }
        }
    }

    private void restoreDashboard(DashboardSnapshot snapshot, String source) {
        try {
            if (snapshot == null) {
                dashboardRepository.findBySource(source).stream()
                        .findFirst().ifPresent(dashboardRepository::delete);
                return;
            }
            if (snapshot.exists()) {
                Dashboard current = dashboardRepository.findById(snapshot.id()).orElse(null);
                if (current != null) {
                    current.setName(snapshot.name());
                    current.setCode(snapshot.code());
                    current.setType(snapshot.type());
                    current.setUrl(snapshot.url());
                    current.setConfigIndex(snapshot.configIndex());
                    current.setHtmlPath(snapshot.htmlPath());
                    current.setIsDefault(snapshot.isDefault());
                    current.setSource(snapshot.source());
                    dashboardRepository.save(current);
                }
            } else {
                dashboardRepository.findBySource(source).stream()
                        .findFirst().ifPresent(dashboardRepository::delete);
            }
        } catch (RuntimeException e) {
            log.error("恢复大屏 Dashboard 失败", e);
        }
    }

    private List<DashboardArtifact> artifacts(DashboardPackage packageValue) {
        List<DashboardArtifact> result = new ArrayList<>();
        packageValue.getFiles().forEach((path, content) -> result.add(new DashboardArtifact(
                path, DashboardSupport.digest(content),
                content.getBytes(StandardCharsets.UTF_8).length, true)));
        return result;
    }

    private DashboardApplyResult blocked(DashboardApplyResult result,
                                         String code,
                                         String path,
                                         String message) {
        result.setStatus("blocked");
        result.setApplied(false);
        result.setMessage(message);
        result.getDiagnostics().add(new DashboardDiagnostic(code, path, message, "error"));
        return result;
    }

    private boolean jsonEquals(String left, String right) {
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(StringUtils.defaultString(left))
                    .equals(JacksonConfig.OBJECT_MAPPER.readTree(StringUtils.defaultString(right)));
        } catch (Exception e) {
            return Objects.equals(left, right);
        }
    }

    private record Snapshot(boolean exists, String content) {
    }

    private record DashboardSnapshot(boolean exists,
                                     Integer id,
                                     String name,
                                     String code,
                                     DashboardType type,
                                     String url,
                                     String configIndex,
                                     String htmlPath,
                                     Boolean isDefault,
                                     String source) {
        private static DashboardSnapshot of(Dashboard dashboard) {
            return dashboard == null ? null : new DashboardSnapshot(
                    true, dashboard.getId(), dashboard.getName(), dashboard.getCode(),
                    dashboard.getType(), dashboard.getUrl(), dashboard.getConfigIndex(),
                    dashboard.getHtmlPath(), dashboard.getIsDefault(), dashboard.getSource());
        }
    }
}
