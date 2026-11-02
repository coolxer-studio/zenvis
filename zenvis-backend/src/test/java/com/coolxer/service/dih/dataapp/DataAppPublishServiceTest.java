package com.coolxer.service.dih.dataapp;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.dih.dataapp.DataAppApplyResult;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.MenuService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataAppPublishServiceTest {

    @TempDir
    Path tempDir;

    private InMemoryConfigService configService;
    private MenuService menuService;
    private DataAppPackageService packageService;
    private DataAppPublishService service;
    private final AtomicBoolean failMenuCreate = new AtomicBoolean(false);
    private MenuVo currentMenu;

    @BeforeEach
    void setUp() {
        configService = new InMemoryConfigService(tempDir);
        menuService = mock(MenuService.class);
        packageService = mock(DataAppPackageService.class);
        service = new DataAppPublishService(configService, menuService, packageService);

        when(packageService.revalidateSnapshot(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(menuService.findBySource(anyString())).thenReturn(List.of());
        when(menuService.create(any())).thenAnswer(invocation -> {
            if (failMenuCreate.get()) {
                throw new IllegalStateException("菜单服务不可用");
            }
            MenuDto dto = invocation.getArgument(0);
            Menu created = new Menu();
            created.setId(34);
            created.setName(dto.getName());
            created.setType(dto.getType());
            created.setRoute("/service/" + dto.getType().getRoute() + "/" + dto.getParams());
            created.setParams(dto.getParams());
            created.setParentId(dto.getParentId());
            created.setOrderNumber(dto.getOrderNumber());
            created.setLevel(dto.getLevel());
            created.setSource(dto.getSource());
            currentMenu = toVo(created);
            return created;
        });
        when(menuService.info(anyLong())).thenAnswer(invocation ->
                currentMenu != null && currentMenu.getId().longValue() == invocation.<Long>getArgument(0)
                        ? currentMenu : null);
        when(menuService.update(anyLong(), any())).thenAnswer(invocation -> {
            MenuDto dto = invocation.getArgument(1);
            currentMenu.setName(dto.getName());
            currentMenu.setType(dto.getType());
            currentMenu.setParams(dto.getParams());
            currentMenu.setParentId(dto.getParentId());
            currentMenu.setOrderNumber(dto.getOrderNumber());
            currentMenu.setLevel(dto.getLevel());
            currentMenu.setSource(dto.getSource());
            return true;
        });
    }

    @Test
    void appliesConfigurationAndMenuOnceThenReadsEverythingBack() throws Exception {
        DataAppPackage packageValue = packageValue(
                "user-event-analysis", 1, "LOW_CODE_PAGE", null, Map.of("index.json", "{}"));
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(result.isApplied()).isTrue();
        assertThat(result.getMenu()).containsEntry("id", 34).containsEntry("operation", "created");
        assertThat(configService.read("user-event-analysis", "index.json")).isEqualTo("{}");
        DataAppManifest manifest = JacksonConfig.OBJECT_MAPPER.readValue(
                configService.read("user-event-analysis", "_app.json"), DataAppManifest.class);
        assertThat(manifest.getPublishStatus()).isEqualTo("published");
        assertThat(manifest.getMenuId()).isEqualTo(34);
        assertThat(configService.operations).containsSubsequence(
                "modify:index.json", "modify:_app.json", "modify:_app.json");
    }

    @Test
    void writesPagesBeforeSiteAndManifestAndDeletesPagesAfterNavigation() {
        DataAppSpec spec = spec("user-event-analysis", 1, "LOW_CODE_APP",
                page("overview", "总览"), page("details", "明细"));
        DataAppManifest previous = manifest(spec, "LOW_CODE_APP", 1);
        previous.getPages().remove(1);
        previous.getFiles().keySet().removeIf(path -> path.contains("details"));
        configService.write("user-event-analysis", "pages/overview.json", "old-overview");
        configService.write("user-event-analysis", "pages/details.json", "old-details");
        configService.write("user-event-analysis", "site.json", "old-site");
        DataAppPackage packageValue = packageValue(spec, previous, Map.of(
                "pages/overview.json", "new-overview",
                "site.json", "new-site"));
        packageValue.setDeletedFiles(new ArrayList<>(List.of("pages/details.json")));
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(configService.operations).containsSubsequence(
                "modify:pages/overview.json",
                "modify:site.json",
                "delete:pages/details.json",
                "modify:_app.json");
        assertThat(configService.exists("user-event-analysis", "pages/details.json")).isFalse();
    }

    @Test
    void restoresPreviousFilesWhenConfigurationWriteFails() {
        DataAppSpec oldSpec = spec("user-event-analysis", 1, "LOW_CODE_PAGE", page("overview", "总览"));
        DataAppManifest previous = manifest(oldSpec, "LOW_CODE_PAGE", 3);
        configService.write("user-event-analysis", "index.json", "old-index");
        configService.write("user-event-analysis", "_app.json", JacksonUtil.toJson(previous));
        DataAppPackage packageValue = packageValue(oldSpec, previous, Map.of("index.json", "new-index"));
        configService.failNextModify("index.json");
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("blocked");
        assertThat(result.getDiagnostics()).extracting(item -> item.getCode())
                .contains("APP_WRITE_FAILED");
        assertThat(configService.read("user-event-analysis", "index.json")).isEqualTo("old-index");
        assertThat(configService.read("user-event-analysis", "_app.json"))
                .isEqualTo(JacksonUtil.toJson(previous));
    }

    @Test
    void keepsConfigurationAndReturnsPartialMenuWhenMenuPublicationFails() throws Exception {
        DataAppPackage packageValue = packageValue(
                "user-event-analysis", 1, "LOW_CODE_PAGE", null, Map.of("index.json", "{}"));
        failMenuCreate.set(true);
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("partial_menu");
        assertThat(result.isPartialSuccess()).isTrue();
        assertThat(configService.read("user-event-analysis", "index.json")).isEqualTo("{}");
        DataAppManifest manifest = JacksonConfig.OBJECT_MAPPER.readValue(
                configService.read("user-event-analysis", "_app.json"), DataAppManifest.class);
        assertThat(manifest.getPublishStatus()).isEqualTo("partial_menu");
    }

    @Test
    void retriesOnlyTheMenuAfterPartialSuccessWithoutRewritingFiles() throws Exception {
        DataAppPackage packageValue = packageValue(
                "user-event-analysis", 1, "LOW_CODE_PAGE", null, Map.of("index.json", "{}"));
        failMenuCreate.set(true);
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);
        service.apply(session, session.candidateDigest(), session.validationDigest());
        int writesBeforeRetry = configService.operations.stream()
                .filter(operation -> operation.startsWith("modify:index.json"))
                .toList().size();

        failMenuCreate.set(false);
        DataAppApplyResult result = service.retryMenu(session);

        assertThat(result.getStatus()).as(result.getMessage()).isEqualTo("verified");
        assertThat(configService.operations.stream()
                .filter(operation -> operation.startsWith("modify:index.json"))
                .toList()).hasSize(writesBeforeRetry);
        DataAppManifest manifest = JacksonConfig.OBJECT_MAPPER.readValue(
                configService.read("user-event-analysis", "_app.json"), DataAppManifest.class);
        assertThat(manifest.getPublishStatus()).isEqualTo("published");
        assertThat(manifest.getMenuId()).isEqualTo(34);
    }

    @Test
    void leavesAnAlreadyMatchingMenuUntouched() {
        DataAppSpec previousSpec = spec(
                "user-event-analysis", 1, "LOW_CODE_PAGE", page("overview", "总览"));
        DataAppManifest previous = manifest(previousSpec, "LOW_CODE_PAGE", 1);
        previous.setMenuId(9);
        DataAppSpec nextSpec = spec(
                "user-event-analysis", 2, "LOW_CODE_PAGE", page("overview", "总览"));
        DataAppManifest next = manifest(nextSpec, "LOW_CODE_PAGE", 2);
        next.setMenuId(9);
        DataAppPackage packageValue = new DataAppPackage(
                nextSpec, next, files(next, Map.of("index.json", "{}")), List.of(), previous);
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);
        currentMenu = menuVo(9, "用户事件分析", MenuType.LOW_CODE_PAGE, 0, null);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(result.getMenu()).containsEntry("operation", "unchanged");
        verify(menuService, never()).update(anyLong(), any());
    }

    @Test
    void removesTheCompatibilityIndexOnlyAfterMenuReadBackSucceeds() {
        DataAppSpec oldSpec = spec("user-event-analysis", 1, "LOW_CODE_PAGE", page("overview", "总览"));
        DataAppManifest previous = manifest(oldSpec, "LOW_CODE_PAGE", 1);
        configService.write("user-event-analysis", "index.json", "legacy-index");
        DataAppSpec spec = spec("user-event-analysis", 2, "LOW_CODE_APP",
                page("overview", "总览"), page("details", "明细"));
        DataAppManifest next = manifest(spec, "LOW_CODE_APP", 2);
        next.setCompatibilityFiles(new ArrayList<>(List.of("index.json")));
        DataAppPackage packageValue = new DataAppPackage(spec, next, files(next, Map.of(
                "pages/overview.json", "overview",
                "pages/details.json", "details",
                "site.json", "site")), List.of(), previous);
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(
                session, session.candidateDigest(), session.validationDigest());

        assertThat(result.getStatus()).isEqualTo("verified");
        assertThat(configService.exists("user-event-analysis", "index.json")).isFalse();
        assertThat(configService.operations).containsSubsequence(
                "modify:_app.json", "delete:index.json", "modify:_app.json");
    }

    @Test
    void rejectsDigestMismatchBeforeAnyWrite() {
        DataAppPackage packageValue = packageValue(
                "user-event-analysis", 1, "LOW_CODE_PAGE", null, Map.of("index.json", "{}"));
        DataAppPreviewRegistry.PreviewSession session = session(packageValue);

        DataAppApplyResult result = service.apply(session, "wrong-candidate", "wrong-validation");

        assertThat(result.getStatus()).isEqualTo("blocked");
        assertThat(configService.operations).isEmpty();
    }

    private DataAppPreviewRegistry.PreviewSession session(DataAppPackage packageValue) {
        String candidate = "candidate-" + packageValue.getManifest().getAppVersion();
        String validation = "validation-" + packageValue.getManifest().getAppVersion();
        DataAppValidationResult validationResult = new DataAppValidationResult();
        validationResult.setCandidateDigest(candidate);
        validationResult.setValidationDigest(validation);
        return new DataAppPreviewRegistry.PreviewSession(
                "wf-1", "chat-1", 7, "token-1", candidate, validation,
                validationResult, packageValue, Map.of(), Instant.now().plusSeconds(600));
    }

    private DataAppPackage packageValue(String appId,
                                        int version,
                                        String menuType,
                                        Integer menuId,
                                        Map<String, String> pageFiles) {
        DataAppSpec spec = spec(appId, version, menuType, page("overview", "总览"));
        DataAppManifest manifest = manifest(spec, menuType, version);
        manifest.setMenuId(menuId);
        return new DataAppPackage(spec, manifest, files(manifest, pageFiles), List.of(), null);
    }

    private DataAppPackage packageValue(DataAppSpec spec,
                                        DataAppManifest previous,
                                        Map<String, String> pageFiles) {
        DataAppManifest manifest = manifest(spec, previous.getMenuType(),
                previous.getAppVersion() + 1);
        manifest.setMenuId(previous.getMenuId());
        return new DataAppPackage(spec, manifest, files(manifest, pageFiles), List.of(), previous);
    }

    private Map<String, String> files(DataAppManifest manifest, Map<String, String> pageFiles) {
        Map<String, String> files = new LinkedHashMap<>(pageFiles);
        Map<String, String> digests = new LinkedHashMap<>();
        pageFiles.forEach((path, content) -> digests.put(path, DataAppSupport.digest(content)));
        manifest.setFiles(digests);
        files.put(DataAppPackageService.MANIFEST_FILE, JacksonUtil.toJson(manifest));
        return files;
    }

    private DataAppManifest manifest(DataAppSpec spec, String menuType, int version) {
        DataAppManifest manifest = new DataAppManifest();
        manifest.setAppId(spec.getAppId());
        manifest.setAppVersion(version);
        manifest.setMode(menuType);
        manifest.setMenuType(menuType);
        manifest.setName(spec.getName());
        manifest.setHomePageId(spec.getHomePageId());
        manifest.setSpec(spec);
        manifest.setSpecDigest(DataAppSupport.digestObject(spec));
        manifest.setPublishStatus("validated");
        manifest.setCreatedAt("2026-01-01T00:00:00Z");
        manifest.setUpdatedAt("2026-01-02T00:00:00Z");
        List<DataAppManifest.DataAppPageRef> pages = new ArrayList<>();
        for (DataAppSpec.DataAppPage page : spec.getPages()) {
            DataAppManifest.DataAppPageRef ref = new DataAppManifest.DataAppPageRef();
            ref.setPageId(page.getPageId());
            ref.setTitle(page.getTitle());
            ref.setPath(DataAppSupport.fileNameForPage(
                    spec.getAppId(), page.getPageId(), "LOW_CODE_PAGE".equals(menuType)));
            pages.add(ref);
        }
        manifest.setPages(pages);
        return manifest;
    }

    private DataAppSpec spec(String appId, int version, String menuType, DataAppSpec.DataAppPage... pages) {
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId(appId);
        spec.setName("用户事件分析");
        spec.setDescription("版本 " + version);
        spec.setHomePageId(pages[0].getPageId());
        spec.setPages(new ArrayList<>(List.of(pages)));
        return spec;
    }

    private DataAppSpec.DataAppPage page(String id, String title) {
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId(id);
        page.setTitle(title);
        page.setBlocks(new ArrayList<>());
        return page;
    }

    private MenuVo menuVo(int id, String name, MenuType type, int parentId, Integer order) {
        MenuVo menu = new MenuVo();
        menu.setId(id);
        menu.setName(name);
        menu.setType(type);
        menu.setRoute("/service/" + type.getRoute() + "/user-event-analysis");
        menu.setParams("user-event-analysis");
        menu.setParentId(parentId);
        menu.setOrderNumber(order);
        menu.setLevel(parentId == 0 ? MenuLevel.LEVEL_1 : MenuLevel.LEVEL_2);
        menu.setSource("data-app:user-event-analysis");
        return menu;
    }

    private MenuVo toVo(Menu menu) {
        MenuVo vo = new MenuVo();
        vo.setId(menu.getId());
        vo.setName(menu.getName());
        vo.setType(menu.getType());
        vo.setRoute(menu.getRoute());
        vo.setParams(menu.getParams());
        vo.setParentId(menu.getParentId());
        vo.setOrderNumber(menu.getOrderNumber());
        vo.setLevel(menu.getLevel());
        vo.setSource(menu.getSource());
        return vo;
    }

    private static final class InMemoryConfigService implements ConfigService {

        private final Path root;
        private final Map<String, Map<String, String>> files = new LinkedHashMap<>();
        private final List<String> operations = new ArrayList<>();
        private final Map<String, AtomicInteger> failures = new LinkedHashMap<>();

        private InMemoryConfigService(Path root) {
            this.root = root;
        }

        private void write(String type, String path, String content) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(path, content);
        }

        private String read(String type, String path) {
            return files.getOrDefault(type, Map.of()).get(path);
        }

        private boolean exists(String type, String path) {
            return read(type, path) != null;
        }

        private void failNextModify(String path) {
            failures.put(path, new AtomicInteger(1));
        }

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            return List.of();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            return null;
        }

        @Override
        public String readFile(String type, String fileName) {
            return read(type, fileName);
        }

        @Override
        public void modifyConfig(String type, ConfigDto configDto) {
            AtomicInteger remaining = failures.get(configDto.getFileName());
            if (remaining != null && remaining.getAndDecrement() > 0) {
                throw new IllegalStateException("模拟配置写入失败");
            }
            operations.add("modify:" + configDto.getFileName());
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>())
                    .put(configDto.getFileName(), configDto.getText());
        }

        @Override
        public boolean addFile(String type, String fileName) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(fileName, "");
            return true;
        }

        @Override
        public boolean renameFile(String type, String originalFile, String newFile) {
            return false;
        }

        @Override
        public ConfigPathOperationResult renameEntry(String type, String sourcePath, String targetName) {
            return ConfigPathOperationResult.failure("rename", sourcePath, "unsupported");
        }

        @Override
        public boolean deleteFile(String type, String fileName) {
            return files.getOrDefault(type, Map.of()).remove(fileName) != null;
        }

        @Override
        public ConfigPathOperationResult deleteEntry(String type, String sourcePath, boolean recursive) {
            boolean removed = files.getOrDefault(type, Map.of()).remove(sourcePath) != null;
            operations.add("delete:" + sourcePath);
            return new ConfigPathOperationResult()
                    .setSuccess(removed)
                    .setOperation("delete")
                    .setSourcePath(sourcePath)
                    .setChanged(removed)
                    .setApplied(removed);
        }

        @Override
        public String configPath(String type) {
            return root.resolve(type + "_config").toString();
        }

        @Override
        public void applyConfig(String type, ConfigDto configDto) {
            modifyConfig(type, configDto);
        }

        @Override
        public boolean addRootPath(String type) {
            files.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
            return true;
        }

        @Override
        public boolean ensureRootPath(String type) {
            return addRootPath(type);
        }

        @Override
        public boolean fileExistsInConfigPath(String type, String fileName) {
            return exists(type, fileName);
        }
    }
}
