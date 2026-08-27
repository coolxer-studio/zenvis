package com.coolxer.service.core.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.service.core.ClickhouseSchemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClickhouseTableTtlIntegrationTest {

    @Autowired
    private ClickhouseSchemeService clickhouseSchemeService;

    @Autowired
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    @Test
    void createsModifiesAndRemovesTableTtl() throws Exception {
        String tableName = "zenvis.zenvis_ttl_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = clickHouseDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try {
                MetaData metaData = metaData(tableName, 30, DataEntity.TtlUnit.DAY);
                clickhouseSchemeService.applyAdditiveScheme(metaData);
                assertThat(engineFull(statement, tableName)).containsIgnoringCase("TTL");

                metaData.getEntity().get(0).getAutoCreate().getTtl().setExpireAfter(2);
                metaData.getEntity().get(0).getAutoCreate().getTtl().setUnit(DataEntity.TtlUnit.MONTH);
                clickhouseSchemeService.synchronizeTableTtl(metaData);
                assertThat(normalize(engineFull(statement, tableName)))
                        .containsAnyOf("INTERVAL 2 MONTH", "TOINTERVALMONTH(2)");

                String unchanged = engineFull(statement, tableName);
                clickhouseSchemeService.synchronizeTableTtl(metaData);
                assertThat(engineFull(statement, tableName)).isEqualTo(unchanged);

                metaData.getEntity().get(0).getAutoCreate().setTtl(null);
                clickhouseSchemeService.synchronizeTableTtl(metaData);
                assertThat(engineFull(statement, tableName).toUpperCase(Locale.ROOT)).doesNotContain(" TTL ");
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + tableName);
            }
        }
    }

    private MetaData metaData(String tableName, long expireAfter, DataEntity.TtlUnit unit) {
        DataEntity entity = new DataEntity();
        entity.setName("ttl_event");
        entity.setTableName(tableName);
        entity.setDataSource("clickhouse");
        DataEntity.DbCreate autoCreate = entity.new DbCreate();
        autoCreate.setEngine("MergeTree()");
        autoCreate.setOrderBy(List.of("event_time"));
        DataEntity.Ttl ttl = new DataEntity.Ttl();
        ttl.setColumn("event_time");
        ttl.setExpireAfter(expireAfter);
        ttl.setUnit(unit);
        autoCreate.setTtl(ttl);
        entity.setAutoCreate(autoCreate);

        DataAttribute eventTime = new DataAttribute();
        eventTime.setEntity(entity.getName());
        eventTime.setName("event_time");
        eventTime.setColumnName("event_time");
        eventTime.setColumnType("DateTime64(3)");

        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));
        metaData.setAttribute(List.of(eventTime));
        return metaData;
    }

    private String engineFull(Statement statement, String qualifiedTableName) throws Exception {
        String table = qualifiedTableName.substring(qualifiedTableName.indexOf('.') + 1);
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT engine_full FROM system.tables WHERE database = 'zenvis' AND name = '" + table + "'")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private String normalize(String value) {
        return value.replace('`', ' ').replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }
}
