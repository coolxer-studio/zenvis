package com.coolxer.service.core.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClickhouseRecordIdMigrationIntegrationTest {

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    @Test
    void migrationKeepsHistoricalRowsNullAndGeneratesStableIdsForNewRows() throws Exception {
        String tableName = "zenvis.zenvis_id_migration_it_"
                + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = clickHouseDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TABLE " + tableName
                        + " (event_id String) ENGINE = MergeTree ORDER BY event_id");
                statement.execute("INSERT INTO " + tableName
                        + " (event_id) VALUES ('legacy-1'), ('legacy-2')");

                migrateRecordId(statement, tableName);
                statement.execute("INSERT INTO " + tableName
                        + " (event_id) VALUES ('current-1'), ('current-2')");

                Map<String, String> firstIds = readIds(statement, tableName);
                assertThat(firstIds.get("legacy-1")).isNull();
                assertThat(firstIds.get("legacy-2")).isNull();
                assertThat(firstIds.get("current-1")).isNotBlank();
                assertThat(firstIds.get("current-2")).isNotBlank()
                        .isNotEqualTo(firstIds.get("current-1"));

                migrateRecordId(statement, tableName);

                assertThat(readIds(statement, tableName)).isEqualTo(firstIds);
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + tableName);
            }
        }
    }

    private void migrateRecordId(Statement statement, String tableName) throws SQLException {
        statement.execute("ALTER TABLE " + tableName
                + " ADD COLUMN IF NOT EXISTS zenvis_id Nullable(UUID) DEFAULT NULL");
        statement.execute("ALTER TABLE " + tableName
                + " MATERIALIZE COLUMN zenvis_id SETTINGS mutations_sync = 1");
        statement.execute("ALTER TABLE " + tableName
                + " MODIFY COLUMN zenvis_id Nullable(UUID) DEFAULT generateUUIDv4()");
    }

    private Map<String, String> readIds(Statement statement, String tableName) throws SQLException {
        Map<String, String> ids = new LinkedHashMap<>();
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT event_id, toString(zenvis_id) FROM " + tableName + " ORDER BY event_id")) {
            while (resultSet.next()) {
                ids.put(resultSet.getString(1), resultSet.getString(2));
            }
        }
        return ids;
    }
}
