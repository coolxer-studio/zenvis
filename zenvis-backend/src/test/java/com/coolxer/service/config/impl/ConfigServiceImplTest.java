package com.coolxer.service.config.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigServiceImplTest {

    @TempDir
    Path configRoot;

    @Test
    void ensureRootPathCreatesConfigDirectoryIdempotently() {
        ConfigServiceImpl service = createService();

        Path expectedPath = configRoot.resolve("inspection-dashboard_config");

        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
    }

    @Test
    void supportsNestedConfigFileLifecycle() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path rootFile = typeRoot.resolve("index.html");
        Path nestedFile = typeRoot.resolve("com.coolxer.plugin.probe/pages/overview.html");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(rootFile, "root");
        Files.writeString(nestedFile, "nested");

        assertThat(service.fileExistsInConfigPath("html-page", "index.html")).isTrue();
        assertThat(service.fileExistsInConfigPath(
                "html-page", "com.coolxer.plugin.probe/pages/overview.html")).isTrue();
        assertThat(service.readFile(
                "html-page", "com.coolxer.plugin.probe/pages/overview.html")).isEqualTo("nested");

        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("com.coolxer.plugin.probe/pages/overview.html");
        configDto.setText("updated");
        service.modifyConfig("html-page", configDto);
        assertThat(Files.readString(nestedFile)).isEqualTo("updated");

        assertThat(service.renameFile(
                "html-page",
                "com.coolxer.plugin.probe/pages/overview.html",
                "com.coolxer.plugin.probe/pages/dashboard.html")).isTrue();
        Path renamedFile = nestedFile.resolveSibling("dashboard.html");
        assertThat(renamedFile).exists();
        assertThat(service.deleteFile(
                "html-page", "com.coolxer.plugin.probe/pages/dashboard.html")).isTrue();
        assertThat(renamedFile).doesNotExist();
    }

    @Test
    void configTreeContainsStableRelativePaths() throws IOException {
        ConfigServiceImpl service = createService();
        Path nestedFile = configRoot.resolve(
                "html-page_config/com.coolxer.plugin.probe/probe-ingestion-overview.html");
        Files.createDirectories(nestedFile.getParent());
        Files.writeString(nestedFile, "overview");

        ConfigVo root = service.getConfigFileTree("html-page").get(0);
        ConfigVo directory = findByRelativePath(root.getNodes(), "com.coolxer.plugin.probe");
        ConfigVo file = findByRelativePath(
                directory.getNodes(),
                "com.coolxer.plugin.probe/probe-ingestion-overview.html");

        assertThat(root.getRelativePath()).isEmpty();
        assertThat(directory.getRelativePath()).isEqualTo("com.coolxer.plugin.probe");
        assertThat(file.getRelativePath())
                .isEqualTo("com.coolxer.plugin.probe/probe-ingestion-overview.html")
                .doesNotStartWith(configRoot.toString());
    }

    @Test
    void rejectsPathsOutsideConfigRoot() throws IOException {
        ConfigServiceImpl service = createService();
        Path typeRoot = configRoot.resolve("html-page_config");
        Path outsideDirectory = configRoot.resolve("outside");
        Files.createDirectories(typeRoot);
        Files.createDirectories(outsideDirectory);
        Files.writeString(outsideDirectory.resolve("secret.html"), "secret");
        Files.createSymbolicLink(typeRoot.resolve("external"), outsideDirectory);

        assertThat(service.fileExistsInConfigPath("html-page", "../outside/secret.html")).isFalse();
        assertThat(service.fileExistsInConfigPath(
                "html-page", outsideDirectory.resolve("secret.html").toString())).isFalse();
        assertThat(service.fileExistsInConfigPath("html-page", "external/secret.html")).isFalse();
        assertThatThrownBy(() -> service.readFile("html-page", "../outside/secret.html"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.readFile("html-page", "external/secret.html"))
                .isInstanceOf(ApiException.class);
    }

    private ConfigServiceImpl createService() {
        ConfigServiceImpl service = new ConfigServiceImpl();
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "configPath", configRoot.toString());
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);
        return service;
    }

    private ConfigVo findByRelativePath(List<ConfigVo> nodes, String relativePath) {
        return nodes.stream()
                .filter(node -> relativePath.equals(node.getRelativePath()))
                .findFirst()
                .orElseThrow();
    }
}
