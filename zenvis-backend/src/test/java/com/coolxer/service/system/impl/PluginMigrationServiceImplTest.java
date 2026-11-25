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

    @Test
    void validatesCompleteAppliedHistoryWithoutExecutingNewMigration() throws Exception {
        Path first = tempDir.resolve("V001__create_demo.sql");
        Path second = tempDir.resolve("V002__add_name.sql");
        Files.writeString(first, "CREATE TABLE plugin_demo (id INT PRIMARY KEY);");
        Files.writeString(second, "ALTER TABLE plugin_demo ADD COLUMN name VARCHAR(32);");
        service.migrateMysql("com.coolxer.plugin.demo", List.of(first));

        service.validateMysql("com.coolxer.plugin.demo", List.of(first, second));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_sys_plugin_migration", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsCandidateThatOmitsAppliedMigration() throws Exception {
        Path first = tempDir.resolve("V001__create_demo.sql");
        Files.writeString(first, "CREATE TABLE plugin_demo (id INT PRIMARY KEY);");
        service.migrateMysql("com.coolxer.plugin.demo", List.of(first));

        assertThatThrownBy(() -> service.validateMysql("com.coolxer.plugin.demo", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少已执行迁移");
    }

    @Test
    void rejectsLateMigrationBelowLatestAppliedVersion() throws Exception {
        Path first = tempDir.resolve("V001__create_demo.sql");
        Path third = tempDir.resolve("V003__add_name.sql");
        Path second = tempDir.resolve("V002__late.sql");
        Files.writeString(first, "CREATE TABLE plugin_demo (id INT PRIMARY KEY);");
        Files.writeString(third, "ALTER TABLE plugin_demo ADD COLUMN name VARCHAR(32);");
        Files.writeString(second, "ALTER TABLE plugin_demo ADD COLUMN late_name VARCHAR(32);");
        service.migrateMysql("com.coolxer.plugin.demo", List.of(first, third));

        assertThatThrownBy(() -> service.validateMysql(
                "com.coolxer.plugin.demo", List.of(first, second, third)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须高于已执行版本");
    }

    @Test
    void cleanupDropsOwnedTablesInReverseOrderAndDeletesHistory() throws Exception {
        String packageName = "com.coolxer.plugin.cleanup";
        Path migration = tempDir.resolve("V001__create_tables.sql");
        Files.writeString(migration, """
                CREATE TABLE plugin_parent (id INT PRIMARY KEY);
                CREATE TABLE plugin_child (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_plugin_child_parent FOREIGN KEY (parent_id) REFERENCES plugin_parent(id)
                );
                """);
        service.migrateMysql(packageName, List.of(migration));
        Files.writeString(tempDir.resolve("owned-tables.json"),
                "{\"tables\":[\"plugin_parent\",\"plugin_child\"]}");

        service.cleanupMysql(packageName, tempDir);

        assertThat(tableCount("PLUGIN_PARENT")).isZero();
        assertThat(tableCount("PLUGIN_CHILD")).isZero();
        assertThat(historyCount()).isZero();
    }

    @Test
    void cleanupFallsBackToCreateTableStatementsWhenManifestIsMissing() throws Exception {
        String packageName = "com.coolxer.plugin.fallback";
        Path migration = tempDir.resolve("V001__create_demo.sql");
        Files.writeString(migration, """
                -- create demo table
                CREATE TABLE IF NOT EXISTS plugin_demo_fallback (id INT PRIMARY KEY);
                """);
        service.migrateMysql(packageName, List.of(migration));

        service.cleanupMysql(packageName, tempDir);

        assertThat(tableCount("PLUGIN_DEMO_FALLBACK")).isZero();
        assertThat(historyCount()).isZero();
    }

    @Test
    void cleanupDeletesHistoryForSeedOnlyMigrations() throws Exception {
        String packageName = "com.coolxer.plugin.seed";
        Path migration = tempDir.resolve("V001__seed.sql");
        Files.writeString(migration, "SELECT 1;");
        service.migrateMysql(packageName, List.of(migration));
        Files.writeString(tempDir.resolve("owned-tables.json"), "{\"tables\":[]}");

        service.cleanupMysql(packageName, tempDir);

        assertThat(historyCount()).isZero();
    }

    @Test
    void cleanupFailsWhenMigrationDirectoryMissingButHistoryExists() throws Exception {
        String packageName = "com.coolxer.plugin.missingdir";
        Path migration = tempDir.resolve("V001__create_demo.sql");
        Files.writeString(migration, "CREATE TABLE missingdir_demo (id INT PRIMARY KEY);");
        service.migrateMysql(packageName, List.of(migration));

        assertThatThrownBy(() -> service.cleanupMysql(packageName, tempDir.resolve("missing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("迁移目录不存在");

        assertThat(tableCount("MISSINGDIR_DEMO")).isOne();
        assertThat(historyCount()).isOne();
    }

    @Test
    void cleanupSkipsMissingDirectoryWithoutHistory() {
        service.cleanupMysql("com.coolxer.plugin.nodb", tempDir.resolve("missing"));

        assertThat(historyCount()).isZero();
    }

    @Test
    void cleanupRejectsUnsafeOrDuplicateOwnedTables() throws Exception {
        String packageName = "com.coolxer.plugin.unsafe";
        Path migration = tempDir.resolve("V001__create_system.sql");
        Files.writeString(migration, "CREATE TABLE t_sys_plugin (id INT PRIMARY KEY);");
        service.migrateMysql(packageName, List.of(migration));

        Files.writeString(tempDir.resolve("owned-tables.json"),
                "{\"tables\":[\"t_sys_plugin\"]}");
        assertThatThrownBy(() -> service.cleanupMysql(packageName, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止删除平台系统表");
        assertThat(tableCount("T_SYS_PLUGIN")).isOne();
        assertThat(historyCount()).isOne();

        Files.writeString(tempDir.resolve("owned-tables.json"),
                "{\"tables\":[\"bad-name\"]}");
        assertThatThrownBy(() -> service.cleanupMysql(packageName, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表名不合法");

        Files.writeString(tempDir.resolve("owned-tables.json"),
                "{\"tables\":[\"plugin_dup\",\"plugin_dup\"]}");
        assertThatThrownBy(() -> service.cleanupMysql(packageName, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表清单重复");
    }

    @Test
    void cleanupKeepsHistoryWhenAnyDropFails() throws Exception {
        String packageName = "com.coolxer.plugin.dropfail";
        Path migration = tempDir.resolve("V001__create_tables.sql");
        Files.writeString(migration, """
                CREATE TABLE dropfail_parent (id INT PRIMARY KEY);
                CREATE TABLE dropfail_child (
                    id INT PRIMARY KEY,
                    parent_id INT,
                    CONSTRAINT fk_dropfail_child_parent FOREIGN KEY (parent_id) REFERENCES dropfail_parent(id)
                );
                """);
        service.migrateMysql(packageName, List.of(migration));
        // 错误顺序会在逆序后先 DROP 父表，被外键阻止，用于验证失败时保留迁移历史。
        Files.writeString(tempDir.resolve("owned-tables.json"),
                "{\"tables\":[\"dropfail_child\",\"dropfail_parent\"]}");

        assertThatThrownBy(() -> service.cleanupMysql(packageName, tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("删除插件 MySQL 表失败");

        assertThat(tableCount("DROPFAIL_PARENT")).isOne();
        assertThat(tableCount("DROPFAIL_CHILD")).isOne();
        assertThat(historyCount()).isOne();
    }

    private int tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                Integer.class,
                tableName);
    }

    private int historyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_sys_plugin_migration", Integer.class);
    }
}
