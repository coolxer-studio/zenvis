package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityCoreServiceImplTest {

    private static final String RECORD_ID = "8e388586-24b2-4d4b-aecc-a33151326f4d";
    private static final String OTHER_RECORD_ID = "53a29b77-9e5f-4c33-80cb-1b1a4c10940b";

    private EntityCoreServiceImpl service;
    private MetaDataService metaDataService;
    private QueryEngine queryEngine;

    @BeforeEach
    void setUp() {
        service = new EntityCoreServiceImpl();
        metaDataService = mock(MetaDataService.class);
        queryEngine = mock(QueryEngine.class);
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);
        ReflectionTestUtils.setField(service, "queryEngine", queryEngine);

        DataEntity entity = new DataEntity();
        entity.setName("asset");
        entity.setTableName("zenvis.asset");
        when(metaDataService.getDataEntityByName("asset")).thenReturn(entity);

        DataAttribute name = attribute("name", "name", "String");
        name.setRequired(true);
        DataAttribute insertTime = attribute(
                MetaDataConstants.INSERT_TIME_ATTRIBUTE,
                MetaDataConstants.INSERT_TIME_COLUMN,
                MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        DataAttribute recordId = attribute(
                MetaDataConstants.RECORD_ID_ATTRIBUTE,
                MetaDataConstants.RECORD_ID_COLUMN,
                MetaDataConstants.RECORD_ID_COLUMN_TYPE);
        DataAttribute businessId = attribute("id", "id", "String");
        when(metaDataService.getDataAttributeByName("asset", "name")).thenReturn(name);
        when(metaDataService.getDataAttributeByName("asset", "id")).thenReturn(businessId);
        when(metaDataService.getDataAttributeByName(
                "asset", MetaDataConstants.RECORD_ID_ATTRIBUTE)).thenReturn(recordId);
        when(metaDataService.getDataAttributeByName(
                "asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE)).thenReturn(insertTime);
        when(metaDataService.getAllDataAttributeByEntity(entity))
                .thenReturn(List.of(name, businessId, recordId, insertTime));
    }

    @Test
    void addOmitsSystemMaintainedInsertTimeAndLetsClickHouseDefaultFillIt() {
        service.add("asset", new LinkedHashMap<>(Map.of("name", "router")));
        verify(queryEngine).save("zenvis.asset", List.of("name"), List.of("'router'"));
    }

    @Test
    void addRejectsSystemMaintainedValues() {
        assertThatThrownBy(() -> service.add("asset", Map.of(
                MetaDataConstants.INSERT_TIME_ATTRIBUTE, "2026-07-15 09:00:00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("系统自动维护");
        assertThatThrownBy(() -> service.add("asset", Map.of(
                MetaDataConstants.RECORD_ID_ATTRIBUTE, RECORD_ID)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("系统自动维护");
    }

    @Test
    void addRejectsMissingRequiredMetaAttribute() {
        assertThatThrownBy(() -> service.add("asset", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("name");
        verify(queryEngine, never()).save(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void crudUsesPlatformRecordIdAndKeepsBusinessIdAsOrdinaryData() {
        service.getOne("asset", RECORD_ID);
        service.update("asset", RECORD_ID, Map.of("id", "business-002"));
        service.delete("asset", RECORD_ID);
        service.deleteALL("asset", List.of(RECORD_ID, OTHER_RECORD_ID));

        verify(queryEngine).findById(
                "zenvis.asset", MetaDataConstants.RECORD_ID_COLUMN, RECORD_ID,
                metaDataService.getAllDataAttributeByEntity(
                        metaDataService.getDataEntityByName("asset")));
        verify(queryEngine).update(
                "zenvis.asset", Map.of("id", "'business-002'"),
                MetaDataConstants.RECORD_ID_COLUMN, RECORD_ID);
        verify(queryEngine).delete(
                "zenvis.asset", MetaDataConstants.RECORD_ID_COLUMN, RECORD_ID);
        verify(queryEngine).deleteIn(
                "zenvis.asset", MetaDataConstants.RECORD_ID_COLUMN,
                List.of(RECORD_ID, OTHER_RECORD_ID));
    }

    @Test
    void crudRejectsInvalidPlatformRecordIdsBeforeWriting() {
        assertThatThrownBy(() -> service.getOne("asset", "not-a-uuid"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标准UUID格式");
        assertThatThrownBy(() -> service.updateALL(
                "asset", List.of(RECORD_ID, "not-a-uuid"), Map.of("name", "router")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标准UUID格式");
        verify(queryEngine, never()).update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateRejectsClearingARequiredMetaAttribute() {
        assertThatThrownBy(() -> service.update("asset", RECORD_ID, Map.of("name", "")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("name");
        verify(queryEngine, never()).update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private DataAttribute attribute(String name, String columnName, String columnType) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("asset");
        attribute.setName(name);
        attribute.setColumnName(columnName);
        attribute.setColumnType(columnType);
        return attribute;
    }
}
