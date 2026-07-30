package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.service.retrieval.AnalyticsQueryEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsQueryEngineImplTest {

    private AnalyticsQueryEngineImpl engine;
    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() {
        engine = new AnalyticsQueryEngineImpl();
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        ReflectionTestUtils.setField(engine, "entityManager", entityManager);
        ReflectionTestUtils.setField(engine, "retrievalTimeZone", "Asia/Shanghai");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setHint(anyString(), any())).thenReturn(query);
    }

    @Test
    void countAnyOfUsesParameterizedOrMatchingAndCountsEachRowOnce() {
        when(query.getSingleResult()).thenReturn(6L);
        AnalyticsQueryEngine.QuerySource source = source(List.of(
                new AnalyticsQueryEngine.Criterion(
                        "severity", "String", "equal", List.of("high"))));

        long result = engine.countAnyOf(
                source, List.of("source_address", "destination_address"),
                "192.0.2.1", null);

        assertThat(result).isEqualTo(6L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("toString(source_address) = :focusValue")
                .contains("or toString(destination_address) = :focusValue")
                .contains("severity = :vc0")
                .doesNotContain("192.0.2.1");
        verify(query).setParameter("focusValue", "192.0.2.1");
        verify(query).setParameter("vc0", "high");
        verify(query).setHint("jakarta.persistence.query.timeout", 60_000);
    }

    @Test
    void distributionUsesStableOrderingAndRejectsInjectedIdentifiers() {
        when(query.getResultList()).thenReturn(List.of(
                new Object[]{"192.0.2.1", 9L},
                new Object[]{"198.51.100.8", 9L}));
        AnalyticsQueryEngine.DistributionSource source =
                new AnalyticsQueryEngine.DistributionSource(
                        source(List.of()), "source_address", "String");

        List<Map<String, Object>> rows =
                engine.distribution(source, null, 100, false);

        assertThat(rows).extracting(row -> row.get("bucket"))
                .containsExactly("192.0.2.1", "198.51.100.8");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("order by value desc, bucket asc limit 100");

        AnalyticsQueryEngine.DistributionSource injected =
                new AnalyticsQueryEngine.DistributionSource(
                        source(List.of()), "source_address) from system.tables --", "String");
        assertThatThrownBy(() -> engine.distribution(injected, null, 10, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void dateTimeRangeUsesFractionTolerantClickHouseParser() {
        when(query.getResultList()).thenReturn(List.of());
        AnalyticsQueryEngine.QuerySource source = new AnalyticsQueryEngine.QuerySource(
                "traffic", "网络流量", "zenvis.traffic",
                "event_time", "DateTime", List.of(), "and");

        engine.trend(source, new AnalyticsQueryEngine.Metric("COUNT", null, null),
                new AnalyticsQueryEngine.TimeWindow(
                        "2026-07-21 00:00:00", "2026-07-27 16:45:18"),
                "DAY");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("event_time >= parseDateTimeBestEffort(:tStart, 'Asia/Shanghai')")
                .contains("event_time < parseDateTimeBestEffort(:tEnd, 'Asia/Shanghai')")
                .doesNotContain("event_time >= toDateTime(");
        verify(query).setParameter("tStart", "2026-07-21 00:00:00");
        verify(query).setParameter("tEnd", "2026-07-27 16:45:18");
    }

    @Test
    void dateTime64RangeKeepsMillisecondPrecision() {
        when(query.getResultList()).thenReturn(List.of());

        engine.trend(source(List.of()), new AnalyticsQueryEngine.Metric("COUNT", null, null),
                new AnalyticsQueryEngine.TimeWindow(
                        "2026-07-21 00:00:00", "2026-07-27 16:45:18"),
                "DAY");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("zenvis_insert_time >= toDateTime64(:tStart, 3, 'Asia/Shanghai')")
                .contains("zenvis_insert_time < toDateTime64(:tEnd, 3, 'Asia/Shanghai')");
    }

    @Test
    void aggregateUsesControlledAliasesOrderingAndPercentileExpression() {
        when(query.getResultList()).thenReturn(
                java.util.Collections.singletonList(
                        new Object[]{"login", 12L, 95.5D}));
        AnalyticsQueryEngine.GroupQuery groupQuery = new AnalyticsQueryEngine.GroupQuery(
                source(List.of()),
                List.of(new AnalyticsQueryEngine.GroupDimension(
                        "category", "事件类型", "event_code", "String",
                        "FIELD", null, false)),
                List.of(
                        new AnalyticsQueryEngine.GroupMetric(
                                "total", "数量",
                                new AnalyticsQueryEngine.Metric("COUNT", null, null)),
                        new AnalyticsQueryEngine.GroupMetric(
                                "p95", "P95",
                                new AnalyticsQueryEngine.Metric(
                                        "PERCENTILE", "bytes_total", "UInt64", 0.95D))),
                new AnalyticsQueryEngine.GroupOrder("p95", "desc"),
                21);

        List<Map<String, Object>> rows = engine.aggregateGroups(groupQuery, null);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row).containsEntry("category", "login")
                        .containsEntry("total", 12L)
                        .containsEntry("p95", 95.5D));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("ifNull(toString(event_code), '') as d0")
                .contains("quantileExact(0.95)(bytes_total) as m1")
                .contains("group by d0")
                .contains("order by m1 desc limit 21")
                .doesNotContain("category", "p95");
    }

    @Test
    void histogramKeepsRangeCountsAndScatterUsesStableBoundedSampling() {
        when(query.getSingleResult()).thenReturn(new Object[]{0D, 100D, 5L});
        when(query.getResultList()).thenReturn(List.of(
                new Object[]{0, 2L},
                new Object[]{4, 3L}));
        AnalyticsQueryEngine.HistogramResult histogram = engine.histogram(
                new AnalyticsQueryEngine.HistogramSource(
                        source(List.of()), "bytes", "bytes_total", "UInt64",
                        5, BigDecimal.ZERO, BigDecimal.valueOf(100)),
                null);

        assertThat(histogram.total()).isEqualTo(5L);
        assertThat(histogram.rows()).extracting(row -> row.get("value"))
                .containsExactly(2L, 3L);

        when(query.getResultList()).thenReturn(List.of(
                new Object[]{1D, 2D, null, "a", "first"},
                new Object[]{2D, 3D, null, "b", "second"}));
        AnalyticsQueryEngine.ScatterResult scatter = engine.scatter(
                new AnalyticsQueryEngine.ScatterSource(
                        source(List.of()),
                        "x", "bytes_total",
                        "y", "score_value",
                        null, null,
                        "category", "event_code",
                        "label", "source_address",
                        "zenvis_insert_time", "desc", 1),
                null);

        assertThat(scatter.rows()).hasSize(1);
        assertThat(scatter.hasMore()).isTrue();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeast(3))
                .createNativeQuery(sql.capture());
        assertThat(sql.getAllValues().get(sql.getAllValues().size() - 1))
                .contains("bytes_total is not null")
                .contains("score_value is not null")
                .contains("order by zenvis_insert_time desc, bytes_total asc, score_value asc")
                .contains("limit 2")
                .doesNotContain("rand(");
    }

    private AnalyticsQueryEngine.QuerySource source(
            List<AnalyticsQueryEngine.Criterion> criteria) {
        return new AnalyticsQueryEngine.QuerySource(
                "traffic", "网络流量", "zenvis.traffic",
                "zenvis_insert_time", "DateTime64(3)",
                criteria, "and");
    }
}
