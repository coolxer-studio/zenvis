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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickhouseSchemeServiceImplTest {

    @Test
    void createsAndUpgradesBuiltInInsertTimeColumnWithServerDefault() {
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
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(List.of(id, insertTime));

        MetaData metaData = new MetaData();
        metaData.setEntity(List.of(entity));

        service.loadSchemeFromMetaData(metaData);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues().get(0)).contains(
                "CREATE TABLE IF NOT EXISTS zenvis.asset",
                "zenvis_insert_time DateTime64(3) DEFAULT now64(3)");
        assertThat(sql.getAllValues().get(1)).isEqualTo(
                "ALTER TABLE zenvis.asset ADD COLUMN IF NOT EXISTS "
                        + "zenvis_insert_time DateTime64(3) DEFAULT now64(3)");
    }
}
