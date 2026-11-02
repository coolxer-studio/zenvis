package com.coolxer.service.dih.dataapp;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.dih.dataapp.DataAppApplyResult;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class DataAppPublishService {

    private final ConfigService configService;
    private final MenuService menuService;
    private final DataAppPackageService packageService;

    public DataAppPublishService(ConfigService configService,
                                 MenuService menuService,
                                 DataAppPackageService packageService) {
        this.configService = configService;
        this.menuService = menuService;
        this.packageService = packageService;
    }

    public DataAppApplyResult apply(DataAppPreviewRegistry.PreviewSession session,
                                    String candidateDigest,
                                    String validationDigest) {
        DataAppApplyResult result = new DataAppApplyResult();
        DataAppPackage packageValue = session.packageValue();
        if (!Objects.equals(candidateDigest, session.candidateDigest())
                || !Objects.equals(validationDigest, session.validationDigest())) {
            result.setStatus("blocked");
            result.setMessage("发布摘要与工作流锁定候选不一致");
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "CANDIDATE_MISMATCH", "candidate_digest", result.getMessage(), "error"));
            return result;
        }
        try {
            packageService.revalidateSnapshot(packageValue);
        } catch (RuntimeException e) {
            result.setStatus("blocked");
            result.setMessage(e.getMessage());
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "APP_SNAPSHOT_CHANGED", "spec", e.getMessage(), "error"));
            return result;
        }

        String appId = packageValue.getSpec().getAppId();
        Map<String, Snapshot> snapshots = snapshot(appId, packageValue);
        try {
            writeFiles(appId, packageValue);
            for (String path : packageValue.getDeletedFiles()) {
                if (configService.fileExistsInConfigPath(appId, path)
                        && !deleteIfExists(appId, path)) {
                    throw new IllegalStateException("无法删除页面文件：" + path);
                }
            }
            writeFile(appId, DataAppPackageService.MANIFEST_FILE,
                    packageValue.getFiles().get(DataAppPackageService.MANIFEST_FILE));
            readBack(appId, packageValue);
        } catch (RuntimeException e) {
            restore(appId, snapshots);
            result.setStatus("blocked");
            result.setMessage("应用配置写入或读回失败：" + e.getMessage());
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "APP_WRITE_FAILED", "files", result.getMessage(), "error"));
            return result;
        }

        MenuOutcome menu = publishMenu(packageValue.getSpec(), packageValue.getManifest(),
                packageValue.getPreviousManifest());
        if (menu.success) {
            try {
                readBackMenu(menu);
            } catch (RuntimeException e) {
                menu.success = false;
                menu.error = e.getMessage();
                log.warn("数据应用菜单读回失败，appId={}", appId, e);
            }
        } else {
            log.warn("数据应用菜单发布失败，appId={}, error={}", appId, menu.error);
        }

        DataAppManifest manifest = packageValue.getManifest();
        manifest.setMenuId(menu.id);
        manifest.setMenuName(menu.name);
        manifest.setMenuType(menu.type);
        manifest.setPublishStatus(menu.success ? "published" : "partial_menu");
        if (menu.success) {
            for (String compatibility : new ArrayList<>(manifest.getCompatibilityFiles())) {
                if (deleteIfExists(appId, compatibility)) {
                    manifest.getCompatibilityFiles().remove(compatibility);
                }
            }
        }
        writeFile(appId, DataAppPackageService.MANIFEST_FILE, JacksonUtil.toJson(manifest));
        String manifestReadBack = configService.readFile(appId, DataAppPackageService.MANIFEST_FILE);
        if (!jsonEquals(JacksonUtil.toJson(manifest), manifestReadBack)) {
            result.setStatus("blocked");
            result.setMessage("应用清单读回不一致");
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "MANIFEST_READ_BACK_FAILED", DataAppPackageService.MANIFEST_FILE,
                    result.getMessage(), "error"));
            return result;
        }

        result.setAppId(appId);
        result.setAppVersion(manifest.getAppVersion());
        result.setMode(manifest.getMenuType());
        result.setApplied(true);
        result.setFiles(packageValue.getManifest().getFiles().entrySet().stream()
                .map(entry -> new com.coolxer.model.dih.dataapp.DataAppArtifact(
                        entry.getKey(), entry.getValue(), null, false))
                .toList());
        result.setManifestDigest(DataAppSupport.digest(manifestReadBack));
        result.setMenu(menu.toMap());
        if (menu.success) {
            result.setStatus("verified");
            result.setMessage("应用配置与菜单已发布并完成全量读回");
        } else {
            result.setStatus("partial_menu");
            result.setPartialSuccess(true);
            result.setMessage("应用配置已发布并读回；菜单发布失败，旧入口保持可用，可重试菜单"
                    + (StringUtils.isNotBlank(menu.error) ? "：" + menu.error : ""));
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "MENU_PUBLISH_FAILED", "menu", result.getMessage(), "warning"));
        }
        return result;
    }

    public DataAppApplyResult retryMenu(DataAppPreviewRegistry.PreviewSession session) {
        DataAppApplyResult result = new DataAppApplyResult();
        DataAppPackage packageValue = session.packageValue();
        try {
            readBackManagedFiles(packageValue.getSpec().getAppId(), packageValue);
            MenuOutcome menu = publishMenu(packageValue.getSpec(), packageValue.getManifest(),
                    packageValue.getPreviousManifest());
            if (!menu.success) {
                throw new IllegalStateException(menu.error);
            }
            readBackMenu(menu);
            DataAppManifest manifest = packageValue.getManifest();
            manifest.setMenuId(menu.id);
            manifest.setMenuName(menu.name);
            manifest.setMenuType(menu.type);
            manifest.setPublishStatus("published");
            if (manifest.getCompatibilityFiles().contains("index.json")
                    && deleteIfExists(packageValue.getSpec().getAppId(), "index.json")) {
                manifest.getCompatibilityFiles().remove("index.json");
            }
            writeFile(packageValue.getSpec().getAppId(), DataAppPackageService.MANIFEST_FILE,
                    JacksonUtil.toJson(manifest));
            String manifestReadBack = configService.readFile(
                    packageValue.getSpec().getAppId(), DataAppPackageService.MANIFEST_FILE);
            if (!jsonEquals(JacksonUtil.toJson(manifest), manifestReadBack)) {
                result.setStatus("blocked");
                result.setMessage("菜单已重试发布，但应用清单读回不一致");
                result.getDiagnostics().add(new DataAppDiagnostic(
                        "MANIFEST_READ_BACK_FAILED", DataAppPackageService.MANIFEST_FILE,
                        result.getMessage(), "error"));
                return result;
            }
            result.setApplied(true);
            result.setStatus("verified");
            result.setAppId(packageValue.getSpec().getAppId());
            result.setAppVersion(manifest.getAppVersion());
            result.setMode(manifest.getMenuType());
            result.setMenu(menu.toMap());
            result.setManifestDigest(DataAppSupport.digest(manifestReadBack));
            result.setMessage("菜单已重试发布并完成读回");
        } catch (RuntimeException e) {
            result.setStatus("partial_menu");
            result.setPartialSuccess(true);
            result.setMessage("菜单重试失败：" + e.getMessage());
            log.warn("数据应用菜单重试失败，appId={}", packageValue.getSpec().getAppId(), e);
            result.getDiagnostics().add(new DataAppDiagnostic(
                    "MENU_RETRY_FAILED", "menu", result.getMessage(), "warning"));
        }
        return result;
    }

    private void writeFiles(String appId, DataAppPackage packageValue) {
        configService.ensureRootPath(appId);
        Map<String, String> files = packageValue.getFiles();
        List<String> ordered = new ArrayList<>();
        files.keySet().stream().filter(path -> !path.equals(DataAppPackageService.MANIFEST_FILE))
                .sorted((left, right) -> {
                    if (left.equals("site.json")) return 1;
                    if (right.equals("site.json")) return -1;
                    return left.compareTo(right);
                }).forEach(ordered::add);
        for (String path : ordered) {
            writeFile(appId, path, files.get(path));
        }
    }

    private void readBack(String appId, DataAppPackage packageValue) {
        for (Map.Entry<String, String> entry : packageValue.getFiles().entrySet()) {
            if (!configService.fileExistsInConfigPath(appId, entry.getKey())) {
                throw new IllegalStateException("读回文件缺失：" + entry.getKey());
            }
            String content = configService.readFile(appId, entry.getKey());
            if (!jsonEquals(entry.getValue(), content)) {
                throw new IllegalStateException("读回内容不一致：" + entry.getKey());
            }
        }
    }

    private void readBackManagedFiles(String appId, DataAppPackage packageValue) {
        for (Map.Entry<String, String> entry : packageValue.getFiles().entrySet()) {
            if (DataAppPackageService.MANIFEST_FILE.equals(entry.getKey())) {
                continue;
            }
            if (!configService.fileExistsInConfigPath(appId, entry.getKey())) {
                throw new IllegalStateException("读回文件缺失：" + entry.getKey());
            }
            String content = configService.readFile(appId, entry.getKey());
            if (!jsonEquals(entry.getValue(), content)) {
                throw new IllegalStateException("读回内容不一致：" + entry.getKey());
            }
        }
    }

    private MenuOutcome publishMenu(DataAppSpec spec,
                                    DataAppManifest next,
                                    DataAppManifest previous) {
        MenuOutcome outcome = new MenuOutcome();
        outcome.appId = spec.getAppId();
        outcome.type = next.getMenuType();
        outcome.name = spec.getMenu() != null && StringUtils.isNotBlank(spec.getMenu().getName())
                ? spec.getMenu().getName() : spec.getName();
        try {
            Integer menuId = previous == null ? null : previous.getMenuId();
            if (menuId != null && menuService.info(menuId.longValue()) == null) {
                menuId = null;
            }
            MenuDto dto = new MenuDto();
            dto.setName(outcome.name);
            dto.setType(MenuType.valueOf(outcome.type));
            dto.setParams(spec.getAppId());
            dto.setSource("data-app:" + spec.getAppId());
            dto.setLevel(MenuLevel.LEVEL_1);
            dto.setParentId(0);
            if (spec.getMenu() != null && spec.getMenu().getParentId() != null) {
                dto.setParentId(spec.getMenu().getParentId());
                dto.setLevel(dto.getParentId() == 0 ? MenuLevel.LEVEL_1 : MenuLevel.LEVEL_2);
            }
            if (spec.getMenu() != null && spec.getMenu().getOrderNumber() != null) {
                dto.setOrderNumber(spec.getMenu().getOrderNumber());
            }
            if (menuId == null) {
                findMenuBySource(spec.getAppId()).ifPresent(menu -> outcome.id = menu.getId());
            } else {
                outcome.id = menuId;
            }
            if (outcome.id == null) {
                Menu created = menuService.create(dto);
                if (created == null || created.getId() == null) {
                    throw new IllegalStateException("菜单创建未返回真实 ID");
                }
                outcome.id = created.getId();
                outcome.operation = "created";
            } else {
                MenuVo current = menuService.info(outcome.id.longValue());
                if (current == null) {
                    throw new IllegalStateException("菜单更新目标不存在");
                }
                if (spec.getMenu() == null || spec.getMenu().getParentId() == null) {
                    dto.setParentId(current.getParentId());
                    dto.setLevel(current.getLevel());
                }
                if (spec.getMenu() == null || spec.getMenu().getOrderNumber() == null) {
                    dto.setOrderNumber(current.getOrderNumber());
                }
                if (menuMatches(dto, current)) {
                    outcome.operation = "unchanged";
                    outcome.route = current.getRoute();
                    outcome.params = current.getParams();
                    outcome.source = current.getSource();
                } else if (menuService.update(outcome.id.longValue(), dto)) {
                    outcome.operation = "updated";
                } else {
                    throw new IllegalStateException("菜单更新失败");
                }
            }
        } catch (RuntimeException e) {
            outcome.success = false;
            outcome.error = e.getMessage();
        }
        return outcome;
    }

    private boolean menuMatches(MenuDto dto, MenuVo current) {
        return Objects.equals(dto.getName(), current.getName())
                && dto.getType() == current.getType()
                && Objects.equals(dto.getParams(), current.getParams())
                && Objects.equals(dto.getSource(), current.getSource())
                && Objects.equals(dto.getParentId(), current.getParentId())
                && Objects.equals(dto.getOrderNumber(), current.getOrderNumber());
    }

    private void readBackMenu(MenuOutcome outcome) {
        MenuVo menu = menuService.info(outcome.id.longValue());
        if (menu == null) {
            throw new IllegalStateException("菜单读回缺失");
        }
        if (!Objects.equals(menu.getName(), outcome.name)
                || menu.getType() == null
                || !menu.getType().name().equals(outcome.type)
                || !Objects.equals(menu.getSource(), "data-app:" + outcome.appId)
                || !Objects.equals(menu.getParams(), outcome.appId)
                || StringUtils.isBlank(menu.getRoute())) {
            throw new IllegalStateException("菜单读回与锁定方案不一致");
        }
        outcome.source = menu.getSource();
        outcome.route = menu.getRoute();
        outcome.params = menu.getParams();
    }

    private java.util.Optional<Menu> findMenuBySource(String appId) {
        return menuService.findBySource("data-app:" + appId).stream()
                .filter(menu -> menu.getType() == MenuType.LOW_CODE_APP
                        || menu.getType() == MenuType.LOW_CODE_PAGE)
                .findFirst();
    }

    private void writeFile(String appId, String path, String content) {
        ConfigDto dto = new ConfigDto();
        dto.setFileName(path);
        dto.setText(content);
        if (configService.fileExistsInConfigPath(appId, path)) {
            configService.modifyConfig(appId, dto);
            return;
        }
        ensureParentDirectory(appId, path);
        if (!configService.addFile(appId, path)) {
            throw new IllegalStateException("无法创建文件：" + path);
        }
        configService.modifyConfig(appId, dto);
    }

    private void ensureParentDirectory(String appId, String path) {
        String relative = StringUtils.defaultString(path).replace('\\', '/');
        int index = relative.lastIndexOf('/');
        if (index < 0) {
            return;
        }
        String parent = relative.substring(0, index);
        if (parent.isBlank() || parent.contains("..") || parent.startsWith("/")) {
            throw new IllegalArgumentException("非法配置路径：" + path);
        }
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Paths.get(configService.configPath(appId)).resolve(parent));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建配置目录：" + parent, e);
        }
    }

    private boolean deleteIfExists(String appId, String path) {
        if (!configService.fileExistsInConfigPath(appId, path)) {
            return false;
        }
        return configService.deleteEntry(appId, path, false).isSuccess();
    }

    private Map<String, Snapshot> snapshot(String appId, DataAppPackage packageValue) {
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>();
        DataAppManifest manifest = packageValue.getPreviousManifest();
        if (manifest != null) {
            paths.addAll(manifest.getFiles().keySet());
            paths.addAll(manifest.getCompatibilityFiles());
        }
        paths.addAll(packageValue.getFiles().keySet());
        paths.addAll(packageValue.getDeletedFiles());
        paths.addAll(packageValue.getManifest().getCompatibilityFiles());
        paths.add(DataAppPackageService.MANIFEST_FILE);
        for (String path : paths) {
            if (configService.fileExistsInConfigPath(appId, path)) {
                snapshots.put(path, new Snapshot(true, configService.readFile(appId, path)));
            } else {
                snapshots.put(path, new Snapshot(false, null));
            }
        }
        return snapshots;
    }

    private void restore(String appId, Map<String, Snapshot> snapshots) {
        for (Map.Entry<String, Snapshot> entry : snapshots.entrySet()) {
            try {
                if (entry.getValue().exists) {
                    writeFile(appId, entry.getKey(), entry.getValue().content);
                } else {
                    deleteIfExists(appId, entry.getKey());
                }
            } catch (RuntimeException ignored) {
                // Preserve the original error and leave the file for operator inspection.
            }
        }
    }

    private boolean jsonEquals(String left, String right) {
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(left)
                    .equals(JacksonConfig.OBJECT_MAPPER.readTree(right));
        } catch (Exception e) {
            return Objects.equals(left, right);
        }
    }

    private static final class MenuOutcome {
        private Integer id;
        private String appId;
        private String name;
        private String type;
        private String operation;
        private String source;
        private String route;
        private String params;
        private boolean success = true;
        private String error;

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("type", type);
            map.put("operation", operation);
            map.put("source", source);
            map.put("route", route);
            map.put("params", params);
            map.put("success", success);
            if (error != null) {
                map.put("error", error);
            }
            return map;
        }
    }

    private record Snapshot(boolean exists, String content) {
    }
}
