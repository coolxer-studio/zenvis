package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest;
import com.coolxer.model.retrieval.query.IpEventTimelineQuerySource;
import com.coolxer.model.retrieval.query.IpRelationQueryRequest;
import com.coolxer.model.retrieval.query.IpRelationQuerySource;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
    void addRejectsManualInsertTimeValue() {
        assertThatThrownBy(() -> service.add("asset", Map.of(
                MetaDataConstants.INSERT_TIME_ATTRIBUTE, "2026-07-15 09:00:00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("系统自动维护");
    }

    @Test
    void addRejectsManualRecordIdValue() {
        assertThatThrownBy(() -> service.add("asset", Map.of(
                MetaDataConstants.RECORD_ID_ATTRIBUTE, RECORD_ID)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("zenvis_id")
                .hasMessageContaining("系统自动维护");
    }

    @Test
    void crudUsesPlatformRecordIdAndKeepsBusinessIdAsOrdinaryData() {
        service.getOne("asset", RECORD_ID);
        service.update("asset", RECORD_ID, Map.of("id", "business-002"));
        service.delete("asset", RECORD_ID);
        service.deleteALL("asset", List.of(RECORD_ID, OTHER_RECORD_ID));

        verify(queryEngine).findById(
                "zenvis.asset", MetaDataConstants.RECORD_ID_COLUMN, RECORD_ID,
                metaDataService.getAllDataAttributeByEntity(metaDataService.getDataEntityByName("asset")));
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
    void crudRejectsNonCanonicalOrMissingPlatformRecordIds() {
        assertThatThrownBy(() -> service.getOne("asset", "not-a-uuid"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标准UUID格式");
        assertThatThrownBy(() -> service.deleteALL("asset", List.of(RECORD_ID, "1-1-1-1-1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标准UUID格式");
        assertThatThrownBy(() -> service.update("asset", " ", Map.of("name", "router")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("记录ID不能为空");
    }

    @Test
    void bulkUpdateValidatesEveryRecordIdBeforeChangingAnyRow() {
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
    void ipStatisticsDeduplicatesEntitiesKeepsOrderAndIncludesEntitiesWithoutIpFields() {
        DataEntity traffic = entity("traffic", "网络流量", "zenvis.traffic");
        DataEntity withoutIp = entity("without_ip", "无 IP 数据", "zenvis.without_ip");
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getDataEntityByName("without_ip")).thenReturn(withoutIp);
        when(metaDataService.getDataEntityByName("missing")).thenReturn(null);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("src_ip", "source_address", "String"),
                attribute("dst_ip", "destination_address", "String"),
                attribute("unrelated", "unrelated", "String")));
        when(metaDataService.getAllDataAttributeByEntity(withoutIp)).thenReturn(List.of(
                attribute("device_id", "device_id", "String")));
        when(queryEngine.countAnyOf(
                "zenvis.traffic", List.of("source_address", "destination_address"), "192.0.2.1"))
                .thenReturn(BigDecimal.valueOf(5));

        Map<String, Object> result = service.ipStatistics(
                List.of(" traffic ", "without_ip", "traffic", "missing"), " 192.0.2.1 ");

        assertThat(result).containsEntry("ip", "192.0.2.1")
                .containsEntry("total", 5L)
                .containsEntry("entity_count", 2)
                .containsEntry("matched_entity_count", 1)
                .containsEntry("xaxis_data", List.of("网络流量", "无 IP 数据"))
                .containsEntry("series_data", List.of(5L, 0L));
        assertThat(result.get("rows")).isEqualTo(List.of(
                Map.of("entity", "traffic", "label", "网络流量",
                        "fields", List.of("src_ip", "dst_ip"), "total", 5L),
                Map.of("entity", "without_ip", "label", "无 IP 数据",
                        "fields", List.of(), "total", 0L)));
        verify(queryEngine).countAnyOf(
                "zenvis.traffic", List.of("source_address", "destination_address"), "192.0.2.1");
    }

    @Test
    void ipStatisticsRejectsBlankIpAndEmptyEntityList() {
        assertThatThrownBy(() -> service.ipStatistics(List.of("asset"), " "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("IP不能为空");
        assertThatThrownBy(() -> service.ipStatistics(List.of(" ", "  "), "192.0.2.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("实体列表不能为空");
    }

    @Test
    void ipRelationsResolvesLogicalFieldsAndEnrichesPeerEntityLabels() {
        DataEntity traffic = relationEntity();
        DataEntity status = entity("status", "设备状态", "zenvis.status");
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getDataEntityByName("status")).thenReturn(status);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "source_address", "Nullable(String)"),
                attribute("traffic", "dst_ip", "destination_address", "LowCardinality(String)"),
                attribute("traffic", "found_time", "detected_at", "DateTime64(3)")));
        when(metaDataService.getAllDataAttributeByEntity(status)).thenReturn(List.of(
                attribute("status", "device_id", "device_id", "String")));
        when(queryEngine.countAnyOfInTime(
                any(IpRelationQuerySource.class), eq("2001:db8::1"),
                eq("2026-07-18 00:00:00"), eq("2026-07-24 15:30:00")))
                .thenReturn(BigDecimal.valueOf(7));
        when(queryEngine.findIpRelations(
                any(), eq("2001:db8::1"),
                eq("2026-07-18 00:00:00"), eq("2026-07-24 15:30:00"), eq(50)))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "relation_total", 4L,
                        "peer_total", 1L,
                        "has_more", false,
                        "peers", List.of(new LinkedHashMap<>(Map.of(
                                "ip", "2001:db8::2",
                                "total", 4L,
                                "inbound", 1L,
                                "outbound", 3L,
                                "entities", List.of(new LinkedHashMap<>(Map.of(
                                        "entity", "traffic",
                                        "total", 4L,
                                        "inbound", 1L,
                                        "outbound", 3L)))))))));

        Map<String, Object> result = service.ipRelations(request(
                "2001:db8::1",
                "2026-07-18 00:00:00",
                "2026-07-24 15:30:00",
                50,
                List.of("traffic", "status"),
                List.of(mapping("traffic", "src_ip", "dst_ip", "found_time"))));

        assertThat(result)
                .containsEntry("ip", "2001:db8::1")
                .containsEntry("total", 7L)
                .containsEntry("entity_count", 2)
                .containsEntry("matched_entity_count", 1)
                .containsEntry("relation_total", 4L)
                .containsEntry("peer_total", 1L)
                .containsEntry("peer_count", 1)
                .containsEntry("has_more", false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peers = (List<Map<String, Object>>) result.get("peers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peerEntities =
                (List<Map<String, Object>>) peers.get(0).get("entities");
        assertThat(peerEntities.get(0)).containsEntry("label", "网络流量");

        org.mockito.ArgumentCaptor<IpRelationQuerySource> source =
                org.mockito.ArgumentCaptor.forClass(IpRelationQuerySource.class);
        verify(queryEngine).countAnyOfInTime(
                source.capture(), eq("2001:db8::1"),
                eq("2026-07-18 00:00:00"), eq("2026-07-24 15:30:00"));
        assertThat(source.getValue())
                .extracting(
                        IpRelationQuerySource::sourceColumn,
                        IpRelationQuerySource::targetColumn,
                        IpRelationQuerySource::timeColumn)
                .containsExactly("source_address", "destination_address", "detected_at");
    }

    @Test
    void ipRelationsRejectsPhysicalUnknownAndCrossEntityFields() {
        DataEntity traffic = relationEntity();
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "source_address", "String"),
                attribute("other", "dst_ip", "destination_address", "String"),
                attribute("traffic", "found_time", "detected_at", "DateTime")));

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("traffic"),
                List.of(mapping("traffic", "source_address", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("逻辑字段不存在");

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("traffic"),
                List.of(mapping("traffic", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("字段不属于映射实体");
    }

    @Test
    void ipRelationsRejectsInvalidTypesDuplicateFieldsAndDuplicateMappings() {
        DataEntity traffic = relationEntity();
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "src_ip", "Array(String)"),
                attribute("traffic", "dst_ip", "dst_ip", "String"),
                attribute("traffic", "found_time", "found_time", "UInt64")));

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("traffic"),
                List.of(mapping("traffic", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标量String");

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("traffic"),
                List.of(mapping("traffic", "src_ip", "src_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不得重复");

        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "src_ip", "String"),
                attribute("traffic", "dst_ip", "dst_ip", "String"),
                attribute("traffic", "found_time", "found_time", "DateTime")));
        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("traffic"),
                List.of(
                        mapping("traffic", "src_ip", "dst_ip", "found_time"),
                        mapping("traffic", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("最多配置一组");
    }

    @Test
    void ipRelationsEnforcesStrictTimeRangeLimitAndRequestedMappingEntity() {
        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026/07/18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("asset"),
                List.of(mapping("asset", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("yyyy-MM-dd HH:mm:ss");

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-01-01 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("asset"),
                List.of(mapping("asset", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不得超过90天");

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 30,
                List.of("asset"),
                List.of(mapping("asset", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("20、50或100");

        assertThatThrownBy(() -> service.ipRelations(request(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00", 50,
                List.of("asset"),
                List.of(mapping("not_requested", "src_ip", "dst_ip", "found_time")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不在请求实体列表");
    }

    @Test
    void ipEventTimelineResolvesLogicalFieldsAndUsesHourAtFortyEightHourBoundary() {
        DataEntity traffic = relationEntity();
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "source_address", "Nullable(String)"),
                attribute("traffic", "dst_ip", "destination_address", "LowCardinality(String)"),
                attribute("traffic", "found_time", "detected_at", "DateTime64(3)"),
                attribute("traffic", "event_id", "event_number", "String")));
        when(queryEngine.findIpEventTimeline(
                any(), eq("2001:db8::1"),
                eq("2026-07-23 15:30:00"), eq("2026-07-25 15:30:00"), eq(true)))
                .thenReturn(Map.of(
                        "total", 9L,
                        "inbound_total", 5L,
                        "outbound_total", 4L,
                        "buckets", List.of(Map.of(
                                "time", "2026-07-25 15:00:00",
                                "inbound_total", 5L,
                                "outbound_total", 4L,
                                "inbound", List.of(Map.of("event_type", "010901", "total", 5L)),
                                "outbound", List.of(Map.of("event_type", "030303", "total", 4L))))));

        Map<String, Object> result = service.ipEventTimeline(timelineRequest(
                " 2001:db8::1 ",
                "2026-07-23 15:30:00",
                "2026-07-25 15:30:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time", "event_id", 2, 6))));

        assertThat(result)
                .containsEntry("ip", "2001:db8::1")
                .containsEntry("time_zone", "Asia/Shanghai")
                .containsEntry("granularity", "hour")
                .containsEntry("total", 9L)
                .containsEntry("inbound_total", 5L)
                .containsEntry("outbound_total", 4L);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<IpEventTimelineQuerySource>> sources =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(queryEngine).findIpEventTimeline(
                sources.capture(), eq("2001:db8::1"),
                eq("2026-07-23 15:30:00"), eq("2026-07-25 15:30:00"), eq(true));
        assertThat(sources.getValue()).singleElement().satisfies(source -> {
            assertThat(source.sourceColumn()).isEqualTo("source_address");
            assertThat(source.targetColumn()).isEqualTo("destination_address");
            assertThat(source.timeColumn()).isEqualTo("detected_at");
            assertThat(source.eventTypeColumn()).isEqualTo("event_number");
            assertThat(source.eventTypeStart()).isEqualTo(2);
            assertThat(source.eventTypeLength()).isEqualTo(6);
        });
    }

    @Test
    void ipEventTimelineUsesDayBeyondFortyEightHoursAndReturnsEmptyAggregates() {
        DataEntity traffic = timelineEntityWithValidAttributes();
        when(queryEngine.findIpEventTimeline(
                any(), eq("192.0.2.1"),
                eq("2026-07-22 15:29:59"), eq("2026-07-24 15:30:00"), eq(false)))
                .thenReturn(Map.of());

        Map<String, Object> result = service.ipEventTimeline(timelineRequest(
                "192.0.2.1",
                "2026-07-22 15:29:59",
                "2026-07-24 15:30:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time", "event_id", 2, 6))));

        assertThat(result)
                .containsEntry("granularity", "day")
                .containsEntry("total", 0L)
                .containsEntry("inbound_total", 0L)
                .containsEntry("outbound_total", 0L)
                .containsEntry("buckets", List.of());
        verify(metaDataService).getAllDataAttributeByEntity(traffic);
    }

    @Test
    void ipEventTimelineRejectsPhysicalCrossEntityDuplicateAndInvalidTypeMappings() {
        DataEntity traffic = relationEntity();
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "source_address", "String"),
                attribute("other", "dst_ip", "destination_address", "String"),
                attribute("traffic", "found_time", "detected_at", "DateTime"),
                attribute("traffic", "event_id", "event_number", "String")));

        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "source_address", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("逻辑字段不存在");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("字段不属于映射实体");

        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "src_ip", "Array(String)"),
                attribute("traffic", "dst_ip", "dst_ip", "String"),
                attribute("traffic", "found_time", "found_time", "DateTime"),
                attribute("traffic", "event_id", "event_id", "String")));
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标量String");

        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "src_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不得重复");
    }

    @Test
    void ipEventTimelineEnforcesTimeExtractionAndUniqueEntityLimits() {
        timelineEntityWithValidAttributes();

        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026/07/18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("yyyy-MM-dd HH:mm:ss");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-25 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不得晚于");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-01-01 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不得超过90天");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 0, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1到64");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(timelineMapping(
                        "traffic", "src_ip", "dst_ip", "found_time",
                        "event_id", 2, 17)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1到16");
        assertThatThrownBy(() -> service.ipEventTimeline(timelineRequest(
                "192.0.2.1", "2026-07-18 00:00:00", "2026-07-24 00:00:00",
                List.of(
                        timelineMapping(
                                "traffic", "src_ip", "dst_ip", "found_time",
                                "event_id", 2, 6),
                        timelineMapping(
                                "traffic", "src_ip", "dst_ip", "found_time",
                                "event_id", 2, 6)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("最多配置一组");
    }

    private DataEntity entity(String name, String label, String tableName) {
        DataEntity entity = new DataEntity();
        entity.setName(name);
        entity.setLabel(label);
        entity.setTableName(tableName);
        return entity;
    }

    private DataAttribute attribute(String name, String columnName, String columnType) {
        return attribute("asset", name, columnName, columnType);
    }

    private DataAttribute attribute(String entityName, String name,
                                    String columnName, String columnType) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity(entityName);
        attribute.setName(name);
        attribute.setColumnName(columnName);
        attribute.setColumnType(columnType);
        return attribute;
    }

    private DataEntity relationEntity() {
        return entity("traffic", "网络流量", "zenvis.traffic");
    }

    private DataEntity timelineEntityWithValidAttributes() {
        DataEntity traffic = relationEntity();
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute("traffic", "src_ip", "source_address", "String"),
                attribute("traffic", "dst_ip", "destination_address", "String"),
                attribute("traffic", "found_time", "detected_at", "DateTime"),
                attribute("traffic", "event_id", "event_number", "String")));
        return traffic;
    }

    private IpRelationQueryRequest request(
            String ip, String startTime, String endTime, int limit,
            List<String> entities,
            List<IpRelationQueryRequest.RelationMapping> mappings) {
        return new IpRelationQueryRequest(
                ip, startTime, endTime, limit, entities, mappings);
    }

    private IpRelationQueryRequest.RelationMapping mapping(
            String entity, String sourceField, String targetField, String timeField) {
        return new IpRelationQueryRequest.RelationMapping(
                entity, sourceField, targetField, timeField);
    }

    private IpEventTimelineQueryRequest timelineRequest(
            String ip,
            String startTime,
            String endTime,
            List<IpEventTimelineQueryRequest.EventMapping> mappings) {
        return new IpEventTimelineQueryRequest(ip, startTime, endTime, mappings);
    }

    private IpEventTimelineQueryRequest.EventMapping timelineMapping(
            String entity,
            String sourceField,
            String targetField,
            String timeField,
            String eventTypeField,
            int eventTypeStart,
            int eventTypeLength) {
        return new IpEventTimelineQueryRequest.EventMapping(
                entity,
                sourceField,
                targetField,
                timeField,
                eventTypeField,
                eventTypeStart,
                eventTypeLength);
    }
}
