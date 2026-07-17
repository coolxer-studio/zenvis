package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.model.system.dto.DashboardDto;
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
