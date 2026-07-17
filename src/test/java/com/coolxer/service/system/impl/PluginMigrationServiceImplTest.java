package com.coolxer.service.system.impl;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginMigrationServiceImplTest {

    @TempDir
    Path tempDir;

    private PluginMigrationServiceImpl service;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:plugin-migration-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        service = new PluginMigrationServiceImpl(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void appliesMigrationOnceAndRecordsChecksum() throws Exception {
        Path migration = tempDir.resolve("V001__create_demo.sql");
        Files.writeString(migration, "CREATE TABLE plugin_demo (id INT PRIMARY KEY);");

        service.migrateMysql("com.coolxer.plugin.demo", List.of(migration));
        service.migrateMysql("com.coolxer.plugin.demo", List.of(migration));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_sys_plugin_migration", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PLUGIN_DEMO'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsChangedAppliedMigration() throws Exception {
        Path migration = tempDir.resolve("V001__create_demo.sql");
        Files.writeString(migration, "CREATE TABLE plugin_demo (id INT PRIMARY KEY);");
        service.migrateMysql("com.coolxer.plugin.demo", List.of(migration));
        Files.writeString(migration, "CREATE TABLE plugin_demo (id BIGINT PRIMARY KEY);");

        assertThatThrownBy(() ->
                service.migrateMysql("com.coolxer.plugin.demo", List.of(migration)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("校验和不一致");
    }

    @Test
    void rejectsDuplicateVersions() throws Exception {
        Path first = tempDir.resolve("V001__first.sql");
        Path secondDir = Files.createDirectory(tempDir.resolve("nested"));
        Path second = secondDir.resolve("V001__second.sql");
        Files.writeString(first, "SELECT 1;");
        Files.writeString(second, "SELECT 1;");

        assertThatThrownBy(() ->
                service.migrateMysql("com.coolxer.plugin.demo", List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本重复");
    }
}
