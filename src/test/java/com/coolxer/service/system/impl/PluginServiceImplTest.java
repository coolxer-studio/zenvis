package com.coolxer.service.system.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    void mcpCodeNormalizationMatchesMcpServiceRules() {
        PluginServiceImpl service = newService();

        String code = ReflectionTestUtils.invokeMethod(service, "normalizeMcpCode", "risk system");

        assertThat(code).isEqualTo("risk_system");
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
