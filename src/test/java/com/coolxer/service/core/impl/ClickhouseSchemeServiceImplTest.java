package com.coolxer.service.core.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.retrieval.MetaDataService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickhouseSchemeServiceImplTest {

    @Test
    void createsAndUpgradesBuiltInColumnsWithServerDefaults() {
        ClickhouseSchemeServiceImpl service = new ClickhouseSchemeServiceImpl();
        MetaDataService metaDataService = mock(MetaDataService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        DataEntity entity = new DataEntity();
        entity.setName("asset");
        entity.setTableName("zenvis.asset");
        entity.setDataSource("clickhouse");
        DataEntity.DbCreate autoCreate = entity.new DbCreate();
        autoCreate.setEngine("MergeTree()");
        autoCreate.setOrderBy(List.of("id"));
        entity.setAutoCreate(autoCreate);

        DataAttribute id = new DataAttribute();
        id.setEntity("asset");
        id.setName("id");
        id.setColumnName("id");
        id.setColumnType("String");
        DataAttribute insertTime = new DataAttribute();
        insertTime.setEntity("asset");
        insertTime.setName(MetaDataConstants.INSERT_TIME_ATTRIBUTE);
        insertTime.setColumnName(MetaDataConstants.INSERT_TIME_COLUMN);
        insertTime.setColumnType(MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        DataAttribute recordId = new DataAttribute();
        recordId.setEntity("asset");
        recordId.setName(MetaDataConstants.RECORD_ID_ATTRIBUTE);
        recordId.setColumnName(MetaDataConstants.RECORD_ID_COLUMN);
        recordId.setColumnType(MetaDataConstants.RECORD_ID_COLUMN_TYPE);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(List.of(id, recordId, insertTime));

        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));

        service.loadSchemeFromMetaData(metaData);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(6)).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues().get(0)).contains(
                "CREATE TABLE IF NOT EXISTS zenvis.asset",
                "zenvis_id Nullable(UUID) DEFAULT generateUUIDv4()",
                "zenvis_insert_time DateTime64(3) DEFAULT now64(3)");
        assertThat(sql.getAllValues().get(1)).isEqualTo(
                "ALTER TABLE zenvis.asset ADD COLUMN IF NOT EXISTS "
                        + "zenvis_insert_time DateTime64(3) DEFAULT now64(3)");
        assertThat(sql.getAllValues().get(2)).isEqualTo(
                "ALTER TABLE zenvis.asset ADD COLUMN IF NOT EXISTS "
                        + "zenvis_id Nullable(UUID) DEFAULT NULL");
        assertThat(sql.getAllValues().get(3)).isEqualTo(
                "ALTER TABLE zenvis.asset MATERIALIZE COLUMN zenvis_id SETTINGS mutations_sync = 1");
        assertThat(sql.getAllValues().get(4)).isEqualTo(
                "ALTER TABLE zenvis.asset MODIFY COLUMN "
                        + "zenvis_id Nullable(UUID) DEFAULT generateUUIDv4()");
        assertThat(sql.getAllValues().get(5)).startsWith("SELECT engine_full FROM system.tables");
    }

    @Test
    void recordIdMigrationStopsBeforeModifyWhenMaterializationFails() {
        ClickhouseSchemeServiceImpl service = new ClickhouseSchemeServiceImpl();
        MetaDataService metaDataService = mock(MetaDataService.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(query.getResultList()).thenReturn(List.of());
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            if (statement.contains("MATERIALIZE COLUMN zenvis_id")) {
                throw new IllegalStateException("materialization failed");
            }
            return query;
        });

        DataEntity entity = new DataEntity();
        entity.setName("asset");
        entity.setTableName("zenvis.asset");
        entity.setDataSource("clickhouse");
        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));

        assertThatThrownBy(() -> service.loadSchemeFromMetaData(metaData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("记录ID字段迁移失败")
                .hasMessageContaining("MATERIALIZE COLUMN zenvis_id");
        verify(entityManager, never()).createNativeQuery(
                org.mockito.ArgumentMatchers.contains("MODIFY COLUMN zenvis_id"));
    }

    @Test
    void additiveUpgradeCreatesAndAddsButNeverDrops() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ClickhouseSchemeServiceImpl service = new ClickhouseSchemeServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        DataEntity entity = new DataEntity();
        entity.setName("event");
        entity.setTableName("zenvis.event");
        entity.setDataSource("clickhouse");
        DataEntity.DbCreate autoCreate = entity.new DbCreate();
        autoCreate.setEngine("MergeTree()");
        autoCreate.setOrderBy(List.of("event_id"));
        autoCreate.setPartitionBy("toYYYYMM(zenvis_insert_time)");
        entity.setAutoCreate(autoCreate);

        DataAttribute eventId = new DataAttribute();
        eventId.setEntity("event");
        eventId.setName("event_id");
        eventId.setColumnName("event_id");
        eventId.setColumnType("String");
        DataAttribute severity = new DataAttribute();
        severity.setEntity("event");
        severity.setName("severity");
        severity.setColumnName("severity");
        severity.setColumnType("UInt8");
        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));
        metaData.setAttribute(List.of(eventId, severity));

        service.applyAdditiveScheme(metaData);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues()).anyMatch(statement -> statement.startsWith("CREATE TABLE IF NOT EXISTS"));
        assertThat(sql.getAllValues()).anyMatch(statement -> statement.contains("ADD COLUMN IF NOT EXISTS severity UInt8"));
        assertThat(sql.getAllValues()).noneMatch(statement -> statement.toUpperCase().contains("DROP TABLE"));
    }

    @Test
    void additiveUpgradeAllowsEntityWithoutPartitionKey() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ClickhouseSchemeServiceImpl service = new ClickhouseSchemeServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        DataEntity entity = new DataEntity();
        entity.setName("asset");
        entity.setTableName("onesoc_asset_inventory");
        entity.setDataSource("clickhouse");
        DataEntity.DbCreate autoCreate = entity.new DbCreate();
        autoCreate.setEngine("ReplacingMergeTree(updated_at)");
        autoCreate.setOrderBy(List.of("asset_id"));
        entity.setAutoCreate(autoCreate);

        DataAttribute assetId = new DataAttribute();
        assetId.setEntity("asset");
        assetId.setName("asset_id");
        assetId.setColumnName("asset_id");
        assetId.setColumnType("String");
        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));
        metaData.setAttribute(List.of(assetId));

        service.applyAdditiveScheme(metaData);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues())
                .anyMatch(statement -> statement.startsWith(
                        "CREATE TABLE IF NOT EXISTS onesoc_asset_inventory"));
        assertThat(sql.getAllValues())
                .noneMatch(statement -> statement.toUpperCase().contains("PARTITION BY"));
    }

    @Test
    void createsTableWithStructuredTtl() {
        EntityManager entityManager = metadataAwareEntityManager(null);
        ClickhouseSchemeServiceImpl service = service(entityManager);
        MetaData metaData = ttlMeta(30, DataEntity.TtlUnit.DAY);
        metaData.setAttribute(List.of(attribute("event", "event_time", "DateTime64(3)")));

        service.applyAdditiveScheme(metaData);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues()).anyMatch(statement -> statement.contains(
                "TTL event_time + INTERVAL 30 DAY"));
        assertThat(sql.getAllValues()).noneMatch(statement -> statement.contains("MATERIALIZE TTL"));
    }

    @Test
    void modifiesExistingTableWhenTtlDiffersOrCannotBeParsed() {
        EntityManager entityManager = metadataAwareEntityManager(
                "MergeTree() ORDER BY event_time TTL event_time + INTERVAL 7 DAY DELETE WHERE severity > 1");
        ClickhouseSchemeServiceImpl service = service(entityManager);

        service.synchronizeTableTtl(ttlMeta(30, DataEntity.TtlUnit.DAY));

        verify(entityManager).createNativeQuery(
                "ALTER TABLE zenvis.event MODIFY TTL event_time + INTERVAL 30 DAY");
        verify(entityManager, never()).createNativeQuery(
                org.mockito.ArgumentMatchers.contains("MATERIALIZE TTL"));
    }

    @Test
    void removesExistingTtlWhenMetaOmitsIt() {
        EntityManager entityManager = metadataAwareEntityManager(
                "MergeTree() ORDER BY event_time TTL event_time + INTERVAL 30 DAY");
        ClickhouseSchemeServiceImpl service = service(entityManager);
        MetaData metaData = ttlMeta(30, DataEntity.TtlUnit.DAY);
        metaData.getEntity().get(0).getAutoCreate().setTtl(null);

        service.synchronizeTableTtl(metaData);

        verify(entityManager).createNativeQuery("ALTER TABLE zenvis.event REMOVE TTL");
    }

    @Test
    void skipsDdlWhenFunctionFormattedTtlIsSemanticallyEqual() {
        EntityManager entityManager = metadataAwareEntityManager(
                "MergeTree() ORDER BY event_time TTL `event_time` + toIntervalDay(30) SETTINGS index_granularity = 8192");
        ClickhouseSchemeServiceImpl service = service(entityManager);

        service.synchronizeTableTtl(ttlMeta(30, DataEntity.TtlUnit.DAY));

        verify(entityManager, never()).createNativeQuery(
                org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
    }

    private ClickhouseSchemeServiceImpl service(EntityManager entityManager) {
        ClickhouseSchemeServiceImpl service = new ClickhouseSchemeServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    private EntityManager metadataAwareEntityManager(String engineFull) {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            Query query = mock(Query.class);
            when(query.getResultList()).thenReturn(statement.startsWith("SELECT engine_full")
                    ? engineFull == null ? List.of() : List.of(engineFull)
                    : List.of());
            return query;
        });
        return entityManager;
    }

    private MetaData ttlMeta(long expireAfter, DataEntity.TtlUnit unit) {
        DataEntity entity = new DataEntity();
        entity.setName("event");
        entity.setTableName("zenvis.event");
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
        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));
        metaData.setAttribute(List.of());
        return metaData;
    }

    private DataAttribute attribute(String entity, String column, String type) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity(entity);
        attribute.setName(column);
        attribute.setColumnName(column);
        attribute.setColumnType(type);
        return attribute;
    }
}
