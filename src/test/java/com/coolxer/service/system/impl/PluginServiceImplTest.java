package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.model.system.vo.PluginVo;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginServiceImplTest {

    @TempDir
    Path pluginRoot;

    @Test
    void uploadFileRejectsIllegalPackageName() throws Exception {
        PluginServiceImpl service = newService();
        byte[] packageBytes = tarGz(Map.of(
                "index.json", """
                        {
                          "name": "Bad",
                          "package_name": "../bad",
                          "version": "1.0.0",
                          "description": "bad",
                          "author": "tester",
                          "icon": "data:image/png;base64,AA=="
                        }
                        """
        ));

        assertThatThrownBy(() -> service.uploadFile(packageFile("bad.tar.gz", packageBytes)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("非法插件包名");
    }

    @Test
    void uploadFileRejectsEscapingIconPath() throws Exception {
        PluginServiceImpl service = newService();
        byte[] packageBytes = tarGz(Map.of(
                "index.json", """
                        {
                          "name": "Bad Icon",
                          "package_name": "com.acme.badicon",
                          "version": "1.0.0",
                          "description": "bad icon",
                          "author": "tester",
                          "icon": "../icon.png"
                        }
                        """
        ));

        assertThatThrownBy(() -> service.uploadFile(packageFile("bad-icon.tar.gz", packageBytes)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Bad entry");
    }

    @Test
    void uploadFileRejectsTraversalArchiveEntry() throws Exception {
        PluginServiceImpl service = newService();
        byte[] packageBytes = tarGz(Map.of(
                "index.json", """
                        {
                          "name": "Traversal",
                          "package_name": "com.acme.traversal",
                          "version": "1.0.0",
                          "description": "traversal",
                          "author": "tester",
                          "icon": "data:image/png;base64,AA=="
                        }
                        """,
                "../evil.txt", "evil"
        ));

        assertThatThrownBy(() -> service.uploadFile(packageFile("traversal.tar.gz", packageBytes)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Bad entry");
    }

    @Test
    void uploadFileStoresValidPackageUnderPluginRoot() throws Exception {
        PluginServiceImpl service = newService();
        byte[] packageBytes = tarGz(Map.of(
                "index.json", """
                        {
                          "name": "Demo",
                          "package_name": "com.acme.demo",
                          "version": "1.0.0",
                          "description": "demo",
                          "author": "tester",
                          "icon": "data:image/png;base64,AA=="
                        }
                        """
        ));

        String pluginPath = service.uploadFile(packageFile("demo.tar.gz", packageBytes)).getPluginPath();

        assertThat(Path.of(pluginPath).toAbsolutePath().normalize()).startsWith(pluginRoot.toAbsolutePath().normalize());
    }

    @Test
    void uploadFileAcceptsDashboardAndMcpConfigFolders() throws Exception {
        PluginServiceImpl service = newService();
        byte[] packageBytes = tarGz(Map.of(
                "index.json", """
                        {
                          "name": "Dashboard MCP",
                          "package_name": "com.acme.dashboardmcp",
                          "version": "1.0.0",
                          "description": "dashboard and mcp",
                          "author": "tester",
                          "icon": "data:image/png;base64,AA=="
                        }
                        """,
                "05_dashboard/config.json", "[]",
                "06_mcp/config.json", "[]"
        ));

        String pluginPath = service.uploadFile(packageFile("dashboard-mcp.tar.gz", packageBytes)).getPluginPath();

        assertThat(Path.of(pluginPath).toAbsolutePath().normalize()).startsWith(pluginRoot.toAbsolutePath().normalize());
    }

    @Test
    void dashboardHtmlRelativePathRejectsTraversal() {
        PluginServiceImpl service = newService();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "normalizeRelativePath", "../evil.html", "HTML看板路径"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTML看板路径不合法");
    }

    @Test
    void dashboardHtmlPathUsesPluginRelativePrefix() {
        PluginServiceImpl service = newService();

        Path relativePath = ReflectionTestUtils.invokeMethod(
                service,
                "exportHtmlPagePath",
                "com.acme.demo",
                "com.acme.demo/nested/dashboard.html"
        );

        assertThat(relativePath).isEqualTo(Path.of("nested/dashboard.html"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "exportHtmlPagePath",
                "com.acme.demo",
                "/html-page/com.acme.demo/nested/dashboard.html"
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void pluginDashboardCannotClaimDefaultAndReinstallPreservesExistingDefault() {
        PluginServiceImpl service = newService();
        DashboardDto newDashboardDto = linkDashboardDto();
        newDashboardDto.setIsDefault(true);

        DashboardDto normalizedNew = ReflectionTestUtils.invokeMethod(
                service,
                "normalizePluginDashboard",
                "com.acme.demo",
                newDashboardDto,
                null,
                new ArrayList<Path>(),
                null
        );

        assertThat(normalizedNew.getIsDefault()).isFalse();

        Dashboard existing = new Dashboard().setIsDefault(true);
        DashboardDto reinstallDto = linkDashboardDto();
        reinstallDto.setIsDefault(false);
        DashboardDto normalizedReinstall = ReflectionTestUtils.invokeMethod(
                service,
                "normalizePluginDashboard",
                "com.acme.demo",
                reinstallDto,
                null,
                new ArrayList<Path>(),
                existing
        );
        existing.updateFromDto(normalizedReinstall);

        assertThat(normalizedReinstall.getIsDefault()).isNull();
        assertThat(existing.getIsDefault()).isTrue();
    }

    @Test
    void mcpCodeNormalizationMatchesMcpServiceRules() {
        PluginServiceImpl service = newService();

        String code = ReflectionTestUtils.invokeMethod(service, "normalizeMcpCode", "risk system");

        assertThat(code).isEqualTo("risk_system");
    }

    @Test
    void installPluginUiSupportsLegacyFilesAndIndependentBundles() throws Exception {
        PluginServiceImpl service = newService();
        Path source = pluginRoot.resolve("source-ui");
        Files.createDirectories(source.resolve("app"));
        Files.createDirectories(source.resolve("ip-statistics"));
        Files.writeString(source.resolve("legacy.json"), "{\"legacy\":true}");
        Files.writeString(source.resolve("app/site.json"), "{\"data\":{\"pages\":[]}}");
        Files.writeString(source.resolve("app/index.json"), "{\"type\":\"page\"}");
        Files.writeString(source.resolve("ip-statistics/index.json"), "{\"type\":\"page\"}");

        List<Path> copiedPaths = ReflectionTestUtils.invokeMethod(
                service,
                "installPluginUi",
                "com.acme.demo",
                source
        );

        Path configRoot = pluginRoot.resolve("config");
        assertThat(copiedPaths).hasSize(3);
        assertThat(configRoot.resolve("com.acme.demo_config/legacy.json")).exists();
        assertThat(configRoot.resolve("com.acme.demo.app_config/site.json")).exists();
        assertThat(configRoot.resolve("com.acme.demo.app_config/index.json")).exists();
        assertThat(configRoot.resolve("com.acme.demo.ip-statistics_config/index.json")).exists();
    }

    @Test
    void installPluginUiRejectsInvalidOrIncompleteBundle() throws Exception {
        PluginServiceImpl service = newService();
        Path incomplete = pluginRoot.resolve("incomplete-ui");
        Files.createDirectories(incomplete.resolve("detail-0001"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "installPluginUi",
                "com.acme.demo",
                incomplete
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("缺少 site.json 或 index.json");

        Path invalid = pluginRoot.resolve("invalid-ui");
        Files.createDirectories(invalid.resolve("detail_config"));
        Files.writeString(invalid.resolve("detail_config/index.json"), "{\"type\":\"page\"}");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "installPluginUi",
                "com.acme.demo",
                invalid
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("不能包含 _config 后缀");
    }

    @Test
    void exportAndCleanupPluginUiPreserveBundleLayoutAndUnrelatedConfigs() throws Exception {
        PluginServiceImpl service = newService();
        Path source = pluginRoot.resolve("installed-package/04_ui");
        Files.createDirectories(source.resolve("app"));
        Files.createDirectories(source.resolve("detail-0001"));
        Files.writeString(source.resolve("legacy.json"), "{\"legacy\":true}");
        Files.writeString(source.resolve("app/site.json"), "{\"data\":{\"pages\":[]}}");
        Files.writeString(source.resolve("detail-0001/index.json"), "{\"type\":\"page\"}");
        ReflectionTestUtils.invokeMethod(service, "installPluginUi", "com.acme.demo", source);

        Path configRoot = pluginRoot.resolve("config");
        Files.writeString(
                configRoot.resolve("com.acme.demo.detail-0001_config/index.json"),
                "{\"type\":\"page\",\"title\":\"updated\"}"
        );
        Path unrelatedDashboard = configRoot.resolve("com.acme.demo.dashboard_config");
        Files.createDirectories(unrelatedDashboard);
        Files.writeString(unrelatedDashboard.resolve("index.json"), "{\"type\":\"page\"}");

        Path exported = pluginRoot.resolve("exported/04_ui");
        Files.createDirectories(exported);
        ReflectionTestUtils.invokeMethod(
                service,
                "exportPluginUi",
                "com.acme.demo",
                source,
                exported
        );

        assertThat(exported.resolve("legacy.json")).exists();
        assertThat(exported.resolve("app/site.json")).exists();
        assertThat(Files.readString(exported.resolve("detail-0001/index.json"))).contains("updated");

        ReflectionTestUtils.invokeMethod(service, "cleanupPluginUi", "com.acme.demo", source);

        assertThat(configRoot.resolve("com.acme.demo_config")).doesNotExist();
        assertThat(configRoot.resolve("com.acme.demo.app_config")).doesNotExist();
        assertThat(configRoot.resolve("com.acme.demo.detail-0001_config")).doesNotExist();
        assertThat(unrelatedDashboard).exists();
    }

    @Test
    void pluginUiRollbackDeletesEveryCopiedConfigPath() throws Exception {
        PluginServiceImpl service = newService();
        Path first = pluginRoot.resolve("config/com.acme.demo.app_config");
        Path second = pluginRoot.resolve("config/com.acme.demo.detail_config");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.writeString(first.resolve("site.json"), "{}");
        Files.writeString(second.resolve("index.json"), "{}");

        ReflectionTestUtils.invokeMethod(service, "deletePluginUiPaths", List.of(first, second));

        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
    }

    @Test
    void additiveMetaUpgradeAllowsNewFieldsAndEntities() {
        PluginServiceImpl service = newService();
        MetaData current = metaData(entity(1, "event", "zenvis.event"),
                attribute(1, "event", "event_id", "event_id", "String"));
        MetaData candidate = metaData(
                List.of(entity(1, "event", "zenvis.event"), entity(2, "asset", "zenvis.asset")),
                List.of(
                        attribute(1, "event", "event_id", "event_id", "String"),
                        attribute(2, "event", "severity", "severity", "UInt8"),
                        attribute(3, "asset", "asset_id", "asset_id", "String")
                ));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service, "validateAdditiveMetaChange", current, candidate)).doesNotThrowAnyException();
    }

    @Test
    void additiveMetaUpgradeRejectsDeletionRenameAndTypeChanges() {
        PluginServiceImpl service = newService();
        MetaData current = metaData(entity(1, "event", "zenvis.event"),
                attribute(1, "event", "event_id", "event_id", "String"));
        MetaData deleted = metaData(new ArrayList<>(), new ArrayList<>());
        MetaData renamedTable = metaData(entity(1, "event", "zenvis.event_v2"),
                attribute(1, "event", "event_id", "event_id", "String"));
        MetaData changedType = metaData(entity(1, "event", "zenvis.event"),
                attribute(1, "event", "event_id", "event_id", "UInt64"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateAdditiveMetaChange", current, deleted))
                .isInstanceOf(ApiException.class).hasMessageContaining("删除或重命名实体");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateAdditiveMetaChange", current, renamedTable))
                .isInstanceOf(ApiException.class).hasMessageContaining("表名");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateAdditiveMetaChange", current, changedType))
                .isInstanceOf(ApiException.class).hasMessageContaining("字段类型");
    }

    @Test
    void upgradeSnapshotIsPersistedAndReadableAfterRestart() throws Exception {
        PluginServiceImpl service = newService();
        MenuService menuService = mock(MenuService.class);
        DashboardRepository dashboardRepository = mock(DashboardRepository.class);
        McpServerConfigRepository mcpRepository = mock(McpServerConfigRepository.class);
        PushTaskService pushTaskService = mock(PushTaskService.class);
        SkillService skillService = mock(SkillService.class);
        ReflectionTestUtils.setField(service, "menuService", menuService);
        ReflectionTestUtils.setField(service, "dashboardRepository", dashboardRepository);
        ReflectionTestUtils.setField(service, "mcpServerConfigRepository", mcpRepository);
        ReflectionTestUtils.setField(service, "pushTaskService", pushTaskService);
        ReflectionTestUtils.setField(service, "skillService", skillService);

        String packageName = "com.acme.snapshot";
        Plugin plugin = new Plugin();
        plugin.setId(7);
        plugin.setName("Snapshot");
        plugin.setPackageName(packageName);
        plugin.setVersion("1.0.0");
        plugin.setPluginPath(pluginRoot.resolve("snapshot.tar.gz").toString());
        plugin.setUpgradeOperationId("operation-1");
        Path installed = pluginRoot.resolve(packageName);
        Files.createDirectories(installed);
        Files.writeString(installed.resolve("index.json"), "{}");
        when(menuService.findBySource(packageName)).thenReturn(List.of());
        when(dashboardRepository.findBySource(packageName)).thenReturn(List.of());
        when(mcpRepository.findBySource(packageName)).thenReturn(List.of());
        when(pushTaskService.findBySourceMark(packageName)).thenReturn(List.of());
        when(skillService.getInstalledPluginSkillPath(packageName))
                .thenReturn(pluginRoot.resolve("no-installed-skill"));

        ReflectionTestUtils.invokeMethod(service, "createUpgradeSnapshot", plugin, "operation-1");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "readUpgradeSnapshot", plugin))
                .doesNotThrowAnyException();
        assertThat(pluginRoot.resolve("upgrade/7/operation-1/snapshot/snapshot.json")).isRegularFile();
        assertThat(pluginRoot.resolve("upgrade/7/operation-1/snapshot/installed/index.json")).exists();
    }

    @Test
    void upgradeIdentityRequiresSamePackageAndStrictlyHigherSemVer() {
        PluginServiceImpl service = newService();
        Plugin current = new Plugin();
        current.setPackageName("com.acme.demo");
        current.setVersion("1.2.3");

        PluginVo higher = new PluginVo();
        higher.setPackageName("com.acme.demo");
        higher.setVersion("1.2.4");
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service, "validateUpgradeIdentity", current, higher)).doesNotThrowAnyException();

        for (String invalidVersion : List.of("1.2.3", "1.2.2", "v2", "1.02.4")) {
            PluginVo candidate = new PluginVo();
            candidate.setPackageName("com.acme.demo");
            candidate.setVersion(invalidVersion);
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service, "validateUpgradeIdentity", current, candidate))
                    .isInstanceOf(ApiException.class);
        }

        PluginVo wrongPackage = new PluginVo();
        wrongPackage.setPackageName("com.acme.other");
        wrongPackage.setVersion("2.0.0");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateUpgradeIdentity", current, wrongPackage))
                .isInstanceOf(ApiException.class).hasMessageContaining("包名");
    }

    private PluginServiceImpl newService() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "pluginPath", pluginRoot.toString());
        ReflectionTestUtils.setField(customWebConfig, "configPath", pluginRoot.resolve("config").toString());
        ReflectionTestUtils.setField(customWebConfig, "htmlPagePath", pluginRoot.resolve("html-page").toString());
        PluginServiceImpl service = new PluginServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);
        return service;
    }

    private DashboardDto linkDashboardDto() {
        DashboardDto dto = new DashboardDto();
        dto.setName("Demo dashboard");
        dto.setCode("demo-dashboard");
        dto.setType(DashboardType.LINK);
        dto.setUrl("https://example.com/dashboard");
        return dto;
    }

    private MetaData metaData(DataEntity entity, DataAttribute attribute) {
        return metaData(List.of(entity), List.of(attribute));
    }

    private MetaData metaData(List<DataEntity> entities, List<DataAttribute> attributes) {
        MetaData metaData = new MetaData();
        metaData.setEntity(new ArrayList<>(entities));
        metaData.setAttribute(new ArrayList<>(attributes));
        return metaData;
    }

    private DataEntity entity(int id, String name, String tableName) {
        DataEntity entity = new DataEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setTableName(tableName);
        entity.setDataSource("clickhouse");
        DataEntity.DbCreate autoCreate = entity.new DbCreate();
        autoCreate.setEngine("MergeTree()");
        autoCreate.setOrderBy(List.of(name + "_id"));
        autoCreate.setPartitionBy("toYYYYMM(zenvis_insert_time)");
        entity.setAutoCreate(autoCreate);
        return entity;
    }

    private DataAttribute attribute(int id,
                                    String entity,
                                    String name,
                                    String columnName,
                                    String columnType) {
        DataAttribute attribute = new DataAttribute();
        attribute.setId(id);
        attribute.setEntity(entity);
        attribute.setName(name);
        attribute.setColumnName(columnName);
        attribute.setColumnType(columnType);
        return attribute;
    }

    private MockMultipartFile packageFile(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "application/gzip", bytes);
    }

    private byte[] tarGz(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                byte[] content = file.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(content.length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(content);
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
        }
        return bytes.toByteArray();
    }
}
