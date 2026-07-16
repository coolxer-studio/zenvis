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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityCoreServiceImplTest {

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
        DataAttribute insertTime = attribute(
                MetaDataConstants.INSERT_TIME_ATTRIBUTE,
                MetaDataConstants.INSERT_TIME_COLUMN,
                MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        when(metaDataService.getDataAttributeByName("asset", "name")).thenReturn(name);
        when(metaDataService.getDataAttributeByName(
                "asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE)).thenReturn(insertTime);
    }

    @Test
    void addOmitsSystemMaintainedInsertTimeAndLetsClickHouseDefaultFillIt() {
        service.add("asset", new LinkedHashMap<>(Map.of("name", "router")));

        verify(queryEngine).save("zenvis.asset", List.of("name"), List.of("'router'"));
    }

    @Test
    void addRejectsManualInsertTimeValue() {
        assertThatThrownBy(() -> service.add("asset", Map.of(
                MetaDataConstants.INSERT_TIME_ATTRIBUTE, "2026-07-15 09:00:00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("系统自动维护");
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
