package com.coolxer.service.impl;

import com.coolxer.service.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VectorServiceImplTest {

    @TempDir
    Path tempDir;

    private Path workspace;
    private VectorServiceImpl service;

    @BeforeEach
    void setUp() {
        workspace = tempDir.resolve("workspace");
        service = new VectorServiceImpl(
                mock(MonitorService.class),
                tempDir.resolve("vector").toString(),
                workspace.toString());
    }

    @Test
    void writesConfigWithExplicitYamlTomlAndJsonExtensions() {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();
        assertThat(workspace.resolve("10/push.yaml")).isRegularFile();

        assertThat(service.createProcess("10", "[sources.demo]", null, "toml")).isNull();
        assertThat(workspace.resolve("10/push.yaml")).doesNotExist();
        assertThat(workspace.resolve("10/push.toml")).isRegularFile();

        assertThat(service.createProcess("10", "{\"sources\":{}}", null, "json")).isNull();
        assertThat(workspace.resolve("10/push.toml")).doesNotExist();
        assertThat(workspace.resolve("10/push.json")).isRegularFile();
    }

    @Test
    void ymlAliasWritesYamlAndRemovesLegacyExtension() throws Exception {
        Path legacy = workspace.resolve("10/push.yml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy");

        assertThat(service.createProcess("10", "sources: {}", null, "yml")).isNull();

        assertThat(legacy).doesNotExist();
        assertThat(workspace.resolve("10/push.yaml")).isRegularFile();
        assertThat(Files.readString(workspace.resolve("10/push.yaml"))).isEqualTo("sources: {}");
    }

    @Test
    void invalidFormatFailsWithoutDestroyingExistingConfig() throws Exception {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();

        assertThat(service.createProcess("10", "sources: {changed: true}", null, "ini"))
                .contains("不支持的配置格式");
        assertThat(Files.readString(workspace.resolve("10/push.yaml")))
                .isEqualTo("sources: {}");
    }

    @Test
    void updateProcessSwitchesFormatAndCleansPreviousFile() {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();

        assertThat(service.updateProcess("10", "[sources.demo]", null, "toml")).isNull();

        assertThat(workspace.resolve("10/push.yaml")).doesNotExist();
        assertThat(workspace.resolve("10/push.toml")).isRegularFile();
    }
}
