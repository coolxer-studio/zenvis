package com.coolxer.service.system.impl;

import com.coolxer.service.system.PluginMigrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PluginMigrationServiceImpl implements PluginMigrationService {

    private static final Pattern MIGRATION_FILE =
            Pattern.compile("^V([0-9]+(?:\\.[0-9]+)*)__([A-Za-z0-9_-]+)\\.sql$");
    private static final String HISTORY_TABLE = "t_sys_plugin_migration";

    private final DataSource mysqlDataSource;
    private final JdbcTemplate jdbcTemplate;

    public PluginMigrationServiceImpl(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        this.mysqlDataSource = mysqlDataSource;
        this.jdbcTemplate = new JdbcTemplate(mysqlDataSource);
    }

    @Override
    public synchronized void migrateMysql(String packageName, List<Path> migrations) {
        if (migrations == null || migrations.isEmpty()) {
            return;
        }
        ensureHistoryTable();
        List<Migration> ordered = parseMigrations(migrations);
        for (Migration migration : ordered) {
            applyMigration(packageName, migration);
        }
    }

    private void ensureHistoryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_sys_plugin_migration (
                    package_name VARCHAR(255) NOT NULL,
                    migration_version VARCHAR(100) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    checksum CHAR(64) NOT NULL,
                    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (package_name, migration_version)
                )
                """);
    }

    private List<Migration> parseMigrations(List<Path> paths) {
        List<Migration> migrations = new ArrayList<>();
        Set<String> versions = new HashSet<>();
        for (Path path : paths) {
            String fileName = path.getFileName().toString();
            Matcher matcher = MIGRATION_FILE.matcher(fileName);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "插件 MySQL 迁移文件名不合法: " + fileName
                                + "，应使用 Vnnn__description.sql");
            }
            String version = matcher.group(1);
            if (!versions.add(version)) {
                throw new IllegalArgumentException("插件 MySQL 迁移版本重复: " + version);
            }
            try {
                migrations.add(new Migration(
                        version,
                        matcher.group(2).replace('_', ' '),
                        checksum(path),
                        path
                ));
            } catch (Exception e) {
                throw new IllegalStateException("读取插件 MySQL 迁移失败: " + fileName, e);
            }
        }
        migrations.sort(Comparator.comparing(Migration::version, this::compareVersion));
        return migrations;
    }

    private void applyMigration(String packageName, Migration migration) {
        List<String> checksums = jdbcTemplate.query(
                "SELECT checksum FROM " + HISTORY_TABLE
                        + " WHERE package_name = ? AND migration_version = ?",
                (rs, rowNum) -> rs.getString(1),
                packageName,
                migration.version()
        );
        if (!checksums.isEmpty()) {
            if (!checksums.get(0).equals(migration.checksum())) {
                throw new IllegalStateException(
                        "插件迁移校验和不一致: " + packageName + " V" + migration.version());
            }
            return;
        }

        try (Connection connection = mysqlDataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(migration.path()));
            jdbcTemplate.update(
                    "INSERT INTO " + HISTORY_TABLE
                            + " (package_name, migration_version, description, checksum, installed_on)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    packageName,
                    migration.version(),
                    migration.description(),
                    migration.checksum(),
                    LocalDateTime.now()
            );
            log.info("插件 MySQL 迁移完成: package={}, version={}", packageName, migration.version());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "执行插件 MySQL 迁移失败: " + packageName + " V" + migration.version(), e);
        }
    }

    private String checksum(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest);
    }

    private int compareVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            int compared = Integer.compare(leftValue, rightValue);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private record Migration(String version, String description, String checksum, Path path) {
    }
}
