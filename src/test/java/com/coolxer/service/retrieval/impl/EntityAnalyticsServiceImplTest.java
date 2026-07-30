package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.analytics.AggregateQueryRequest;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.AnalyticsMetric;
import com.coolxer.model.retrieval.analytics.AnalyticsTimeRange;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.HistogramQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.model.retrieval.analytics.ScatterQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.analytics.ValueStatisticsQueryRequest;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.retrieval.AnalyticsQueryEngine;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.RetrievalAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityAnalyticsServiceImplTest {

    private AnalyticsQueryEngine queryEngine;
    private MetaDataService metaDataService;
    private RetrievalAccessPolicy accessPolicy;
    private EntityAnalyticsServiceImpl service;
    private DataEntity traffic;

    @BeforeEach
    void setUp() {
        queryEngine = mock(AnalyticsQueryEngine.class);
        metaDataService = mock(MetaDataService.class);
        accessPolicy = mock(RetrievalAccessPolicy.class);
        service = new EntityAnalyticsServiceImpl(queryEngine, metaDataService, accessPolicy);
        ReflectionTestUtils.setField(service, "retrievalTimeZone", "Asia/Shanghai");

        traffic = new DataEntity();
        traffic.setName("traffic");
        traffic.setLabel("网络流量");
        traffic.setTableName("zenvis.traffic");
        when(metaDataService.getDataEntityByName("traffic")).thenReturn(traffic);
        when(metaDataService.getAllDataAttributeByEntity(traffic)).thenReturn(List.of(
                attribute(MetaDataConstants.INSERT_TIME_ATTRIBUTE,
                        MetaDataConstants.INSERT_TIME_COLUMN, "DateTime64(3)"),
                attribute("addr_src", "source_address", "String"),
                attribute("addr_dst", "destination_address", "String"),
                attribute("event_code", "event_code", "String"),
                attribute("bytes", "bytes_total", "UInt64"),
                attribute("score", "score_value", "Float64"),
                attribute("payload", "payload", "json")));
        when(metaDataService.getDataOperatorByName("equal")).thenReturn(new DataOperator());
    }

    @Test
    void overviewSeparatesAllTimeAndCurrentCountsAndBuildsEchartsOption() {
        when(queryEngine.aggregate(any(), any(), isNull())).thenReturn(125L);
        when(queryEngine.aggregate(any(), any(), any(AnalyticsQueryEngine.TimeWindow.class)))
                .thenReturn(7L);

        AnalyticsResponse response = service.overview(new OverviewQueryRequest(
                List.of("traffic"), new AnalyticsTimeRange("TODAY", null, null),
                null, "NONE", List.of(), "and"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) response.result().get("rows");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("all_time_count", 125L);
            assertThat(row).containsEntry("current_value", 7L);
        });
        assertThat(response.echarts()).containsEntry("chart_type", "bar");
        assertThat(response.echarts().get("option")).isInstanceOf(Map.class);
        verify(accessPolicy).checkRead("traffic");
    }

    @Test
    void distributionSupportsArbitraryFieldCriteriaAndTop100() {
        RequestCriteriaDto criterion = new RequestCriteriaDto();
        criterion.setAttribute("addr_src");
        criterion.setOperator("equal");
        criterion.setValueList(List.of("192.0.2.1"));
        when(queryEngine.distribution(any(), isNull(), eq(100), eq(false))).thenReturn(List.of(
                Map.of("bucket", "192.0.2.1", "value", 9L),
                Map.of("bucket", "198.51.100.8", "value", 4L)));

        AnalyticsResponse response = service.distribution(new DistributionQueryRequest(
                "traffic", "addr_src", null, null,
                new AnalyticsTimeRange("ALL_TIME", null, null), null,
                List.of(criterion), "and", 100, false));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) response.result().get("rows");
        assertThat(rows).extracting(row -> row.get("bucket"))
                .containsExactly("192.0.2.1", "198.51.100.8");
        ArgumentCaptor<AnalyticsQueryEngine.DistributionSource> source =
                ArgumentCaptor.forClass(AnalyticsQueryEngine.DistributionSource.class);
        verify(queryEngine).distribution(source.capture(), isNull(), eq(100), eq(false));
        assertThat(source.getValue().dimensionColumn()).isEqualTo("source_address");
        assertThat(source.getValue().source().criteria()).singleElement()
                .satisfies(item -> assertThat(item.column()).isEqualTo("source_address"));
    }

    @Test
    void distributionRejectsTop101BeforeExecutingQuery() {
        assertThatThrownBy(() -> service.distribution(new DistributionQueryRequest(
                "traffic", "addr_src", null, null, null, null,
                List.of(), "and", 101, false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1到100");
    }

    @Test
    void distributionReturnsStableTopOneTopTenAndTopHundred() {
        List<Map<String, Object>> values = IntStream.rangeClosed(1, 100)
                .mapToObj(index -> Map.<String, Object>of(
                        "bucket", "value-" + String.format("%03d", index),
                        "value", 101L - index))
                .toList();
        when(queryEngine.distribution(any(), isNull(), eq(100), eq(false)))
                .thenReturn(values);

        for (int limit : List.of(1, 10, 100)) {
            AnalyticsResponse response = service.distribution(new DistributionQueryRequest(
                    "traffic", "addr_src", null, null,
                    new AnalyticsTimeRange("ALL_TIME", null, null), null,
                    List.of(), "and", limit, false));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows =
                    (List<Map<String, Object>>) response.result().get("rows");
            assertThat(rows).hasSize(limit);
            assertThat(rows.get(0)).containsEntry("bucket", "value-001");
        }
    }

    @Test
    void summarySupportsNumericSumAndRejectsSumOnString() {
        when(queryEngine.aggregate(any(), any(), isNull())).thenReturn(42L);

        AnalyticsResponse response = service.summary(new SummaryQueryRequest(
                "traffic", List.of(new AnalyticsMetric(
                        "bytes_sum", "SUM", "bytes", "流量总量")),
                new AnalyticsTimeRange("ALL_TIME", null, null), null,
                "NONE", List.of(), "and"));

        assertThat(response.echarts()).containsEntry("chart_type", "bar");
        assertThatThrownBy(() -> service.summary(new SummaryQueryRequest(
                "traffic", List.of(new AnalyticsMetric(
                        "invalid", "SUM", "addr_src", null)),
                null, null, "NONE", List.of(), "and")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("数字字段");
    }

    @Test
    void trendFillsEmptyBucketsAndReturnsDatasetLineOption() {
        when(queryEngine.trend(any(), any(), any(), eq("DAY"))).thenReturn(List.of(
                Map.of("bucket", "2026-07-01", "value", 3L)));

        AnalyticsResponse response = service.trend(new TrendQueryRequest(
                List.of("traffic"), null,
                new AnalyticsTimeRange("CUSTOM",
                        "2026-07-01 00:00:00", "2026-07-03 00:00:00"),
                "DAY", "NONE", List.of(), "and"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) response.result().get("rows");
        assertThat(rows).extracting(row -> row.get("value")).containsExactly(3L, 0L);
        assertThat(response.echarts()).containsEntry("chart_type", "line");
        @SuppressWarnings("unchecked")
        Map<String, Object> option = (Map<String, Object>) response.echarts().get("option");
        assertThat(option).containsKeys("dataset", "series");
    }

    @Test
    void autoGranularityUsesMinuteBucketsForShortRanges() {
        when(queryEngine.trend(any(), any(), any(), eq("MINUTE")))
                .thenReturn(List.of());

        AnalyticsResponse response = service.trend(new TrendQueryRequest(
                List.of("traffic"), null,
                new AnalyticsTimeRange("CUSTOM",
                        "2026-07-01 00:00:00", "2026-07-01 00:10:00"),
                "AUTO", "NONE", List.of(), "and"));

        assertThat(response.meta()).containsEntry("granularity", "MINUTE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) response.result().get("rows");
        assertThat(rows).hasSize(10);
    }

    @Test
    void lastTwentyFourHoursUsesRollingTwentyFourHourWindow() {
        when(queryEngine.trend(any(), any(), any(), eq("HOUR")))
                .thenReturn(List.of());

        AnalyticsResponse response = service.trend(new TrendQueryRequest(
                List.of("traffic"), null,
                new AnalyticsTimeRange("LAST_24_HOURS", null, null),
                "HOUR", "NONE", List.of(), "and"));

        ArgumentCaptor<AnalyticsQueryEngine.TimeWindow> window =
                ArgumentCaptor.forClass(AnalyticsQueryEngine.TimeWindow.class);
        verify(queryEngine).trend(any(), any(), window.capture(), eq("HOUR"));
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");
        LocalDateTime start =
                LocalDateTime.parse(window.getValue().startTime(), formatter);
        LocalDateTime end =
                LocalDateTime.parse(window.getValue().endTime(), formatter);
        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofHours(24));
        assertThat(response.meta()).containsEntry("preset", "LAST_24_HOURS");
    }

    @Test
    void aggregateSupportsMultipleMetricsAndUsesResolvedMetaColumns() {
        when(queryEngine.aggregateGroups(any(), isNull())).thenReturn(List.of(
                new LinkedHashMap<>(Map.of(
                        "category", "login",
                        "total", 12L,
                        "average_bytes", 42.5D))));

        AnalyticsResponse response = service.aggregate(new AggregateQueryRequest(
                "traffic",
                List.of(new AggregateQueryRequest.Dimension(
                        "category", "event_code", "事件类型",
                        "FIELD", null, false)),
                List.of(
                        new AnalyticsMetric("total", "COUNT", null, "事件数"),
                        new AnalyticsMetric("average_bytes", "AVG", "bytes", "平均流量")),
                new AnalyticsTimeRange("ALL_TIME", null, null),
                null, List.of(), "and",
                new AggregateQueryRequest.OrderBy("total", "desc"),
                20, "BAR"));

        assertThat(response.meta())
                .containsEntry("query_type", "aggregate")
                .containsEntry("truncated", false);
        assertThat(response.echarts()).containsEntry("chart_type", "bar");
        ArgumentCaptor<AnalyticsQueryEngine.GroupQuery> query =
                ArgumentCaptor.forClass(AnalyticsQueryEngine.GroupQuery.class);
        verify(queryEngine).aggregateGroups(query.capture(), isNull());
        assertThat(query.getValue().dimensions()).singleElement()
                .satisfies(dimension ->
                        assertThat(dimension.column()).isEqualTo("event_code"));
        assertThat(query.getValue().metrics()).extracting(
                        metric -> metric.metric().operation())
                .containsExactly("COUNT", "AVG");
    }

    @Test
    void aggregateBuildsHeatmapAndRejectsExcessiveSeries() {
        List<Map<String, Object>> rows = IntStream.range(0, 21)
                .mapToObj(index -> Map.<String, Object>of(
                        "source", "src",
                        "category", "category-" + index,
                        "total", 1L))
                .toList();
        when(queryEngine.aggregateGroups(any(), isNull())).thenReturn(rows);
        AggregateQueryRequest request = new AggregateQueryRequest(
                "traffic",
                List.of(
                        new AggregateQueryRequest.Dimension(
                                "source", "addr_src", null, "FIELD", null, false),
                        new AggregateQueryRequest.Dimension(
                                "category", "event_code", null, "FIELD", null, false)),
                List.of(new AnalyticsMetric("total", "COUNT", null, "数量")),
                new AnalyticsTimeRange("ALL_TIME", null, null),
                null, List.of(), "and", null, 100, "HEATMAP");

        assertThatThrownBy(() -> service.aggregate(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("图表序列不能超过20");
    }

    @Test
    void percentileHistogramAndScatterKeepControlledContracts() {
        when(queryEngine.aggregate(any(), any(), isNull())).thenReturn(95L);
        AnalyticsResponse summary = service.summary(new SummaryQueryRequest(
                "traffic",
                List.of(new AnalyticsMetric(
                        "p95", "PERCENTILE", "bytes", "P95", 0.95D)),
                new AnalyticsTimeRange("ALL_TIME", null, null),
                null, "NONE", List.of(), "and"));
        assertThat(summary.meta()).containsEntry("truncated", false);
        ArgumentCaptor<AnalyticsQueryEngine.Metric> metric =
                ArgumentCaptor.forClass(AnalyticsQueryEngine.Metric.class);
        verify(queryEngine).aggregate(any(), metric.capture(), isNull());
        assertThat(metric.getValue().percentile()).isEqualTo(0.95D);

        when(queryEngine.histogram(any(), isNull())).thenReturn(
                new AnalyticsQueryEngine.HistogramResult(
                        List.of(Map.of("bucket", 0, "value", 2L),
                                Map.of("bucket", 4, "value", 3L)),
                        BigDecimal.ZERO, BigDecimal.valueOf(100), 5L));
        AnalyticsResponse histogram = service.histogram(new HistogramQueryRequest(
                "traffic", "bytes", 5, null, null,
                new AnalyticsTimeRange("ALL_TIME", null, null),
                null, List.of(), "and"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> histogramRows =
                (List<Map<String, Object>>) histogram.result().get("rows");
        assertThat(histogramRows).hasSize(5);
        assertThat(histogramRows).extracting(row -> row.get("value"))
                .containsExactly(2L, 0L, 0L, 0L, 3L);

        when(queryEngine.scatter(any(), isNull())).thenReturn(
                new AnalyticsQueryEngine.ScatterResult(
                        List.of(Map.of("x", 1, "y", 2)), true));
        AnalyticsResponse scatter = service.scatter(new ScatterQueryRequest(
                "traffic", "bytes", "score", null, null, null,
                null, null, List.of(), "and", null, null, 500));
        assertThat(scatter.meta()).containsEntry("truncated", true);
    }

    @Test
    void newAnalyticsRejectInvalidTypesBoundsAndLogicalIdentifierInjection() {
        assertThatThrownBy(() -> service.histogram(new HistogramQueryRequest(
                "traffic", "addr_src", 20, null, null,
                null, null, List.of(), "and")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("数字类型");
        assertThatThrownBy(() -> service.histogram(new HistogramQueryRequest(
                "traffic", "bytes", 101, null, null,
                null, null, List.of(), "and")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5到100");
        assertThatThrownBy(() -> service.aggregate(new AggregateQueryRequest(
                "traffic",
                List.of(new AggregateQueryRequest.Dimension(
                        "category", "event_code) from system.tables --",
                        null, "FIELD", null, false)),
                List.of(new AnalyticsMetric("total", "COUNT", null, null)),
                null, null, List.of(), "and", null, 10, "BAR")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("字段不存在");
        assertThatThrownBy(() -> service.summary(new SummaryQueryRequest(
                "traffic",
                List.of(new AnalyticsMetric(
                        "invalid", "PERCENTILE", "bytes", null, 1.0D)),
                null, null, "NONE", List.of(), "and")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("大于0且小于1");
    }

    @Test
    void relationsKeepInboundAndOutboundGraphDirections() {
        Map<String, Object> entityBreakdown = new LinkedHashMap<>();
        entityBreakdown.put("entity", "traffic");
        entityBreakdown.put("total", 5L);
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("value", "198.51.100.8");
        peer.put("total", 5L);
        peer.put("inbound", 2L);
        peer.put("outbound", 3L);
        peer.put("entities", List.of(entityBreakdown));
        when(queryEngine.relations(any(), eq("192.0.2.1"), any(), eq(10)))
                .thenReturn(Map.of("peers", List.of(peer), "relation_total", 5L));

        AnalyticsResponse response = service.relations(new RelationQueryRequest(
                "192.0.2.1",
                new AnalyticsTimeRange("CUSTOM",
                        "2026-07-01 00:00:00", "2026-07-02 00:00:00"),
                10, List.of(new RelationQueryRequest.Mapping(
                        "traffic", "addr_src", "addr_dst", null, List.of(), "and"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> option = (Map<String, Object>) response.echarts().get("option");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graphSeries =
                (List<Map<String, Object>>) option.get("series");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links =
                (List<Map<String, Object>>) graphSeries.get(0).get("links");
        assertThat(links).extracting(link -> link.get("direction"))
                .containsExactly("outbound", "inbound");
    }

    @Test
    void relationTimelineUsesGenericCategoryExtractionAndStackedBarOption() {
        when(queryEngine.relationTimeline(any(), eq("192.0.2.1"), any(),
                eq("DAY"), eq(10))).thenReturn(List.of(
                new LinkedHashMap<>(Map.of(
                        "bucket", "2026-07-01",
                        "direction", "outbound",
                        "category", "malware",
                        "value", 4L))));

        AnalyticsResponse response = service.relationTimeline(
                new RelationTimelineQueryRequest(
                        "192.0.2.1",
                        new AnalyticsTimeRange("CUSTOM",
                                "2026-07-01 00:00:00", "2026-07-02 00:00:00"),
                        "DAY", 10,
                        List.of(new RelationTimelineQueryRequest.Mapping(
                                "traffic", "addr_src", "addr_dst", null,
                                "event_code",
                                new RelationTimelineQueryRequest.CategoryExtraction(
                                        "SUBSTRING", 2, 6),
                                List.of(), "and"))));

        assertThat(response.echarts()).containsEntry("chart_type", "bar");
        @SuppressWarnings("unchecked")
        Map<String, Object> option = (Map<String, Object>) response.echarts().get("option");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series =
                (List<Map<String, Object>>) option.get("series");
        assertThat(series).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("stack", "total"));
        ArgumentCaptor<List<AnalyticsQueryEngine.TimelineSource>> sources =
                ArgumentCaptor.forClass(List.class);
        verify(queryEngine).relationTimeline(
                sources.capture(), eq("192.0.2.1"), any(), eq("DAY"), eq(10));
        assertThat(sources.getValue()).singleElement().satisfies(source -> {
            assertThat(source.extractionType()).isEqualTo("SUBSTRING");
            assertThat(source.relation().source().timeColumn())
                    .isEqualTo(MetaDataConstants.INSERT_TIME_COLUMN);
        });
    }

    @Test
    void valueStatisticsUsesOrFieldsAndCountsOneRowOnceInQueryEngine() {
        when(queryEngine.countAnyOf(any(), any(), eq("192.0.2.1"), isNull()))
                .thenReturn(6L);

        AnalyticsResponse response = service.valueStatistics(
                new ValueStatisticsQueryRequest("192.0.2.1",
                        List.of(new ValueStatisticsQueryRequest.Mapping(
                                "traffic", null, List.of("addr_src"),
                                null, List.of(), "or")),
                        new AnalyticsTimeRange("ALL_TIME", null, null)));

        assertThat(response.result()).containsEntry("focus_value", "192.0.2.1")
                .containsEntry("total", 6L);
        verify(queryEngine).countAnyOf(any(), eq(List.of("source_address")),
                eq("192.0.2.1"), isNull());
    }

    @Test
    void rejectsComplexGroupingFields() {
        assertThatThrownBy(() -> service.distribution(new DistributionQueryRequest(
                "traffic", "payload", null, null, null, null,
                List.of(), "and", 10, false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("标量类型");
    }

    @Test
    void rejectsMoreThanFiftyConditionsAcrossMappingsAndInvalidCriteriaLogic() {
        List<RequestCriteriaDto> first = criteria(26);
        List<RequestCriteriaDto> second = criteria(25);
        assertThatThrownBy(() -> service.relations(new RelationQueryRequest(
                "192.0.2.1", null, 10, List.of(
                new RelationQueryRequest.Mapping(
                        "traffic", "addr_src", "addr_dst", null, first, "and"),
                new RelationQueryRequest.Mapping(
                        "traffic", "addr_src", "addr_dst", null, second, "and")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("总数不能超过50");

        assertThatThrownBy(() -> service.distribution(new DistributionQueryRequest(
                "traffic", "addr_src", null, null, null, null,
                List.of(), "xor", 10, false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅支持and或or");
    }

    private List<RequestCriteriaDto> criteria(int size) {
        return IntStream.range(0, size).mapToObj(index -> {
            RequestCriteriaDto criterion = new RequestCriteriaDto();
            criterion.setAttribute("addr_src");
            criterion.setOperator("equal");
            criterion.setValueList(List.of("value-" + index));
            return criterion;
        }).toList();
    }

    private DataAttribute attribute(String name, String column, String type) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("traffic");
        attribute.setName(name);
        attribute.setLabel(name);
        attribute.setColumnName(column);
        attribute.setColumnType(type);
        attribute.setOperators(List.of("equal"));
        return attribute;
    }
}
