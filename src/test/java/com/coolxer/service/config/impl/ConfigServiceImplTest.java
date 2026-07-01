package com.coolxer.service.config.impl;

import com.coolxer.configuration.CustomWebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServiceImplTest {

    @TempDir
    Path configRoot;

    @Test
    void ensureRootPathCreatesConfigDirectoryIdempotently() {
        ConfigServiceImpl service = new ConfigServiceImpl();
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "configPath", configRoot.toString());
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);

        Path expectedPath = configRoot.resolve("inspection-dashboard_config");

        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
        assertThat(service.ensureRootPath("inspection-dashboard")).isTrue();
        assertThat(Files.isDirectory(expectedPath)).isTrue();
    }
}
