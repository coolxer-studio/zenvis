package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.dao.mysql.repository.MenuRepository;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.dao.mysql.repository.PluginRepository;
import com.coolxer.dao.mysql.repository.RolePermissionRepository;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.McpClientService;
import com.coolxer.service.dih.rag.VectorStoreInitializerService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.model.system.vo.PluginVo;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void pluginMcpRegistrationKeepsDisconnectedEnabledServicesAndWarns() {
        PluginServiceImpl service = newService();
        McpServerConfigRepository repository = mock(McpServerConfigRepository.class);
        McpClientService mcpClientService = mock(McpClientService.class);
        ReflectionTestUtils.setField(service, "mcpServerConfigRepository", repository);
        ReflectionTestUtils.setField(service, "mcpClientService", mcpClientService);
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());

        McpServerDto disconnected = mcpDefinition("offline", true);
        McpServerDto secondDisconnected = mcpDefinition("offline-two", true);
        McpServerDto disabled = mcpDefinition("disabled", false);
        McpServerDto connected = mcpDefinition("connected", true);
        String longError = "SSE\nstream closed " + "x".repeat(600);
        when(mcpClientService.create(disconnected))
                .thenReturn(mcpState("offline", true, false, longError));
        when(mcpClientService.create(secondDisconnected))
                .thenReturn(mcpState("offline-two", true, false, "connection refused"));
        when(mcpClientService.create(disabled))
                .thenReturn(mcpState("disabled", false, false, "MCP服务未启用"));
        when(mcpClientService.create(connected))
                .thenReturn(mcpState("connected", true, true, null));

        List<String> disconnectedCodes = ReflectionTestUtils.invokeMethod(
                service,
                "registerPluginMcpServers",
                46L,
                "com.acme.demo",
                List.of(disconnected, secondDisconnected, disabled, connected)
        );
        List<String> warnings = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(service, "addMcpConnectionWarning", warnings, disconnectedCodes);

        assertThat(disconnectedCodes).containsExactly("offline", "offline-two");
        assertThat(warnings).containsExactly("MCP服务连接失败：offline、offline-two");
        String firstInstallLog = service.getLogs(46L);
        assertThat(firstInstallLog)
                .contains("MCP服务连接失败，已保留配置并继续：offline")
                .contains("SSE stream closed")
                .doesNotContain("\n");
        assertThat(firstInstallLog.length()).isLessThan(600);
        assertThat(service.getLogs(46L))
                .contains("MCP服务连接失败，已保留配置并继续：offline-two")
                .contains("connection refused");
        assertThat(disconnected.getSource()).isEqualTo("com.acme.demo");
    }

    @Test
    void pluginMcpRegistrationReadsUpdatedConnectionState() {
        PluginServiceImpl service = newService();
        McpServerConfigRepository repository = mock(McpServerConfigRepository.class);
        McpClientService mcpClientService = mock(McpClientService.class);
        ReflectionTestUtils.setField(service, "mcpServerConfigRepository", repository);
        ReflectionTestUtils.setField(service, "mcpClientService", mcpClientService);
        McpServerConfig existing = new McpServerConfig()
                .setCode("existing")
                .setSource("com.acme.demo");
        existing.setId(52);
        McpServerDto definition = mcpDefinition("existing", true);
        when(repository.findByCode("existing")).thenReturn(Optional.of(existing));
        when(mcpClientService.update(52, definition)).thenReturn(true);
        when(mcpClientService.info(52))
                .thenReturn(mcpState("existing", true, false, "connection refused"));

        List<String> disconnectedCodes = ReflectionTestUtils.invokeMethod(
                service,
                "registerPluginMcpServers",
                47L,
                "com.acme.demo",
                List.of(definition)
        );

        assertThat(disconnectedCodes).containsExactly("existing");
        verify(mcpClientService).update(52, definition);
        verify(mcpClientService).info(52);
    }

    @Test
    void pluginMcpRegistrationStillRejectsConfigurationConflictsAndUpdateFailures() {
        PluginServiceImpl service = newService();
        McpServerConfigRepository repository = mock(McpServerConfigRepository.class);
        McpClientService mcpClientService = mock(McpClientService.class);
        ReflectionTestUtils.setField(service, "mcpServerConfigRepository", repository);
        ReflectionTestUtils.setField(service, "mcpClientService", mcpClientService);
        McpServerConfig conflicting = new McpServerConfig()
                .setCode("conflict")
                .setSource("another.plugin");
        conflicting.setId(53);
        when(repository.findByCode("conflict")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "registerPluginMcpServers",
                48L,
                "com.acme.demo",
                List.of(mcpDefinition("conflict", true))
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("已被其他来源占用");
        verify(mcpClientService, never()).create(any(McpServerDto.class));

        McpServerConfig existing = new McpServerConfig()
                .setCode("update-failed")
                .setSource("com.acme.demo");
        existing.setId(54);
        McpServerDto updateFailed = mcpDefinition("update-failed", true);
        when(repository.findByCode("update-failed")).thenReturn(Optional.of(existing));
        when(mcpClientService.update(54, updateFailed)).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "registerPluginMcpServers",
                49L,
                "com.acme.demo",
                List.of(updateFailed)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("更新MCP服务失败");
        verify(mcpClientService, never()).info(54);

        McpServerDto createFailed = mcpDefinition("create-failed", true);
        when(repository.findByCode("create-failed")).thenReturn(Optional.empty());
        when(mcpClientService.create(createFailed)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "registerPluginMcpServers",
                50L,
                "com.acme.demo",
                List.of(createFailed)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
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
    void additiveMetaUpgradeAllowsAddingChangingAndRemovingTtl() {
        PluginServiceImpl service = newService();
        DataEntity currentEntity = entity(1, "event", "zenvis.event");
        MetaData current = metaData(currentEntity,
                attribute(1, "event", "event_id", "event_id", "String"));
        for (DataEntity.Ttl ttl : List.of(ttl(30, DataEntity.TtlUnit.DAY),
                ttl(2, DataEntity.TtlUnit.MONTH))) {
            DataEntity candidateEntity = entity(1, "event", "zenvis.event");
            candidateEntity.getAutoCreate().setTtl(ttl);
            MetaData candidate = metaData(candidateEntity,
                    attribute(1, "event", "event_id", "event_id", "String"));
            MetaData previous = current;
            assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                    service, "validateAdditiveMetaChange", previous, candidate)).doesNotThrowAnyException();
            current = candidate;
        }
        DataEntity removedEntity = entity(1, "event", "zenvis.event");
        MetaData removed = metaData(removedEntity,
                attribute(1, "event", "event_id", "event_id", "String"));
        MetaData currentWithTtl = current;
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service, "validateAdditiveMetaChange", currentWithTtl, removed)).doesNotThrowAnyException();
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
    void upgradeMenuPreservesExistingPlacementWhileUpdatingPluginDefinition() {
        PluginServiceImpl service = newService();
        MenuService menuService = mock(MenuService.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        ReflectionTestUtils.setField(service, "menuService", menuService);
        ReflectionTestUtils.setField(service, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(service, "rolePermissionRepository", rolePermissionRepository);

        String packageName = "com.acme.demo";
        Menu existing = new Menu()
                .setName("旧菜单名称")
                .setType(MenuType.LOW_CODE_APP)
                .setRoute(MenuType.LOW_CODE_APP.getRoute())
                .setParams("com.acme.demo.app")
                .setParentId(91)
                .setOrderNumber(4)
                .setLevel(MenuLevel.LEVEL_2)
                .setSuperscript("旧角标")
                .setSource(packageName);
        existing.setId(10);
        when(menuService.findBySource(packageName)).thenReturn(List.of(existing));

        MenuDto definition = new MenuDto();
        definition.setName("新菜单名称");
        definition.setType(MenuType.LOW_CODE_APP);
        definition.setParams("com.acme.demo.app");
        definition.setSuperscript("新角标");

        ReflectionTestUtils.invokeMethod(
                service, "reconcilePluginMenus", packageName, List.of(definition));

        assertThat(existing.getId()).isEqualTo(10);
        assertThat(existing.getName()).isEqualTo("新菜单名称");
        assertThat(existing.getType()).isEqualTo(MenuType.LOW_CODE_APP);
        assertThat(existing.getRoute()).isEqualTo(MenuType.LOW_CODE_APP.getRoute());
        assertThat(existing.getParams()).isEqualTo("com.acme.demo.app");
        assertThat(existing.getSuperscript()).isEqualTo("新角标");
        assertThat(existing.getSource()).isEqualTo(packageName);
        assertThat(existing.getLevel()).isEqualTo(MenuLevel.LEVEL_2);
        assertThat(existing.getParentId()).isEqualTo(91);
        assertThat(existing.getOrderNumber()).isEqualTo(4);
        verify(menuRepository).save(existing);
        verify(menuService, never()).create(any(MenuDto.class));
        verify(rolePermissionRepository, never()).findByPermissionId(any());
        verify(menuRepository, never()).deleteById(any());
    }

    @Test
    void upgradeMenusKeepsRootPlacementCreatesNewMenuAtRootAndDeletesObsoleteMenu() {
        PluginServiceImpl service = newService();
        MenuService menuService = mock(MenuService.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        ReflectionTestUtils.setField(service, "menuService", menuService);
        ReflectionTestUtils.setField(service, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(service, "rolePermissionRepository", rolePermissionRepository);

        String packageName = "com.acme.demo";
        Menu retained = new Menu()
                .setName("保留菜单")
                .setType(MenuType.LOW_CODE_PAGE)
                .setRoute(MenuType.LOW_CODE_PAGE.getRoute())
                .setParams("com.acme.demo.retained")
                .setParentId(0)
                .setOrderNumber(2)
                .setLevel(MenuLevel.LEVEL_1)
                .setSource(packageName);
        retained.setId(20);
        Menu obsolete = new Menu()
                .setName("废弃菜单")
                .setType(MenuType.LOW_CODE_PAGE)
                .setRoute(MenuType.LOW_CODE_PAGE.getRoute())
                .setParams("com.acme.demo.obsolete")
                .setParentId(0)
                .setOrderNumber(3)
                .setLevel(MenuLevel.LEVEL_1)
                .setSource(packageName);
        obsolete.setId(21);
        when(menuService.findBySource(packageName)).thenReturn(List.of(retained, obsolete));
        when(rolePermissionRepository.findByPermissionId(21)).thenReturn(List.of());
        when(menuService.create(any(MenuDto.class))).thenAnswer(invocation -> {
            Menu created = new Menu();
            created.setId(30);
            return created;
        });

        MenuDto retainedDefinition = new MenuDto();
        retainedDefinition.setName("保留菜单");
        retainedDefinition.setType(MenuType.LOW_CODE_PAGE);
        retainedDefinition.setParams("com.acme.demo.retained");
        MenuDto newDefinition = new MenuDto();
        newDefinition.setName("新增菜单");
        newDefinition.setType(MenuType.LOW_CODE_PAGE);
        newDefinition.setParams("com.acme.demo.new");

        ReflectionTestUtils.invokeMethod(
                service,
                "reconcilePluginMenus",
                packageName,
                List.of(retainedDefinition, newDefinition)
        );

        assertThat(retained.getLevel()).isEqualTo(MenuLevel.LEVEL_1);
        assertThat(retained.getParentId()).isZero();
        assertThat(retained.getOrderNumber()).isEqualTo(2);
        verify(menuRepository).save(retained);

        ArgumentCaptor<MenuDto> createdMenuCaptor = ArgumentCaptor.forClass(MenuDto.class);
        verify(menuService).create(createdMenuCaptor.capture());
        assertThat(createdMenuCaptor.getValue().getName()).isEqualTo("新增菜单");
        assertThat(createdMenuCaptor.getValue().getLevel()).isEqualTo(MenuLevel.LEVEL_1);
        assertThat(createdMenuCaptor.getValue().getParentId()).isZero();
        assertThat(createdMenuCaptor.getValue().getSource()).isEqualTo(packageName);

        verify(rolePermissionRepository).findByPermissionId(21);
        verify(menuRepository).deleteById(21);
        verify(menuRepository, never()).deleteById(20);
        verify(menuRepository, never()).deleteById(30);
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

    @Test
    void installContinuesWhenEmbeddingOrRagServiceIsUnavailable() {
        PluginServiceImpl service = newService();
        Runnable ragAction = mock(Runnable.class);
        doThrow(new RuntimeException("HTTP 502")).when(ragAction).run();

        Boolean succeeded = ReflectionTestUtils.invokeMethod(
                service,
                "runPluginRagAction",
                41L,
                "com.acme.demo",
                "加载RAG文档",
                "安装",
                ragAction
        );

        assertThat(succeeded).isFalse();
        assertThat(service.getLogs(41L)).contains("跳过RAG，继续安装");
    }

    @Test
    void upgradeSkipsRemainingRagWorkWhenOldRagCannotBeUnloaded() {
        PluginServiceImpl service = newService();
        VectorStoreInitializerService vectorStoreInitializerService = mock(VectorStoreInitializerService.class);
        ReflectionTestUtils.setField(service, "vectorStoreInitializerService", vectorStoreInitializerService);
        when(vectorStoreInitializerService.isRagAvailable()).thenReturn(true);
        doThrow(new RuntimeException("HTTP 502"))
                .when(vectorStoreInitializerService)
                .unloadDocFromRag("com_acme_demo");
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "updatePluginRag",
                42L,
                "com.acme.demo",
                pluginRoot.resolve("old-doc"),
                pluginRoot.resolve("new-doc"),
                warnings
        )).doesNotThrowAnyException();

        assertThat(warnings).containsExactly("Embedding/RAG服务不可用，已跳过RAG更新");
        assertThat(service.getLogs(42L)).contains("跳过RAG，继续升级");
        verify(vectorStoreInitializerService).unloadDocFromRag("com_acme_demo");
        verify(vectorStoreInitializerService, never()).loadDocToRag(anyString(), any(Path.class));
    }

    @Test
    void upgradeContinuesAndRestoresOldRagWhenLoadingNewRagFails() {
        PluginServiceImpl service = newService();
        VectorStoreInitializerService vectorStoreInitializerService = mock(VectorStoreInitializerService.class);
        ReflectionTestUtils.setField(service, "vectorStoreInitializerService", vectorStoreInitializerService);
        when(vectorStoreInitializerService.isRagAvailable()).thenReturn(true);
        Path oldDocPath = pluginRoot.resolve("old-doc");
        Path newDocPath = pluginRoot.resolve("new-doc");
        doThrow(new RuntimeException("HTTP 502"))
                .when(vectorStoreInitializerService)
                .loadDocToRag("com_acme_demo", newDocPath);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "updatePluginRag",
                43L,
                "com.acme.demo",
                oldDocPath,
                newDocPath,
                warnings
        )).doesNotThrowAnyException();

        assertThat(warnings).containsExactly("Embedding/RAG服务不可用，已跳过RAG更新");
        verify(vectorStoreInitializerService).loadDocToRag("com_acme_demo", newDocPath);
        verify(vectorStoreInitializerService).loadDocToRag("com_acme_demo", oldDocPath);
    }

    @Test
    void upgradeSkipsRagBeforeSpringAiRetryWhenHealthCheckFails() {
        PluginServiceImpl service = newService();
        VectorStoreInitializerService vectorStoreInitializerService = mock(VectorStoreInitializerService.class);
        ReflectionTestUtils.setField(service, "vectorStoreInitializerService", vectorStoreInitializerService);
        when(vectorStoreInitializerService.isRagAvailable()).thenReturn(false);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "updatePluginRag",
                44L,
                "com.acme.demo",
                pluginRoot.resolve("old-doc"),
                pluginRoot.resolve("new-doc"),
                warnings
        )).doesNotThrowAnyException();

        assertThat(warnings).containsExactly("Embedding/RAG服务不可用，已跳过RAG更新");
        assertThat(service.getLogs(44L)).contains("跳过RAG，继续升级");
        verify(vectorStoreInitializerService, never()).unloadDocFromRag(anyString());
        verify(vectorStoreInitializerService, never()).loadDocToRag(anyString(), any(Path.class));
    }

    @Test
    void unexpectedBackgroundFailureSettlesInProgressStatus() {
        PluginServiceImpl service = newService();
        PluginOperationExecutor executor = mock(PluginOperationExecutor.class);
        PluginRepository pluginRepository = mock(PluginRepository.class);
        Plugin plugin = new Plugin();
        plugin.setId(45);
        plugin.setStatus(PluginStatusType.INSTALLING);
        when(pluginRepository.findById(45L)).thenReturn(Optional.of(plugin));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        ReflectionTestUtils.setField(service, "pluginOperationExecutor", executor);
        ReflectionTestUtils.setField(service, "pluginRepository", pluginRepository);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service,
                "submitPluginOperation",
                45L,
                PluginStatusType.INSTALL_FAILED,
                "安装",
                (Runnable) () -> {
                    throw new RuntimeException("unexpected");
                }
        )).doesNotThrowAnyException();

        assertThat(plugin.getStatus()).isEqualTo(PluginStatusType.INSTALL_FAILED);
        assertThat(plugin.getOperationMessage()).contains("异常中止");
        assertThat(plugin.getOperationEndedAt()).isNotNull();
        verify(pluginRepository).save(plugin);
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

    private McpServerDto mcpDefinition(String code, boolean enabled) {
        McpServerDto dto = new McpServerDto();
        dto.setCode(code);
        dto.setName(code);
        dto.setBaseUrl("http://mcp.example.com");
        dto.setEnabled(enabled);
        return dto;
    }

    private McpServerVo mcpState(String code, boolean enabled, boolean connected, String lastError) {
        McpServerConfig config = new McpServerConfig()
                .setCode(code)
                .setName(code)
                .setBaseUrl("http://mcp.example.com")
                .setEnabled(enabled)
                .setConnected(connected)
                .setLastError(lastError);
        return new McpServerVo(config, 0);
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

    private DataEntity.Ttl ttl(long expireAfter, DataEntity.TtlUnit unit) {
        DataEntity.Ttl ttl = new DataEntity.Ttl();
        ttl.setColumn("zenvis_insert_time");
        ttl.setExpireAfter(expireAfter);
        ttl.setUnit(unit);
        return ttl;
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
