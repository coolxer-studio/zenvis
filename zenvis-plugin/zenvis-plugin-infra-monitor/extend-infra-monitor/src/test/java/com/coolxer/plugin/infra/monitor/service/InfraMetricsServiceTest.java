package com.coolxer.plugin.infra.monitor.service;

import com.coolxer.plugin.infra.monitor.model.EndpointStatus;
import com.coolxer.plugin.infra.monitor.model.MetricItem;
import com.coolxer.plugin.infra.monitor.model.MonitorOverview;
import com.coolxer.plugin.infra.monitor.model.PrometheusScrape;
import com.coolxer.plugin.infra.monitor.service.EndpointDefinition.VerificationMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfraMetricsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:30:00Z"), ZoneOffset.UTC);
    private static final long NOW = CLOCK.millis();
    private static final long LATEST = NOW - 5_000L;
    private static final long STALE_MILLIS = 120_000L;

    @Test
    void reportsHealthyFromLatestClickHouseBatch() {
        InfraMetricsService service = service(List.of(mysqlEndpoint()), List.of(
                row("mysql", "mysql_up", 1D),
                row("mysql", "mysql_global_status_uptime", 120D),
                row("mysql", "mysql_global_status_threads_connected", 4D),
                row("mysql", "mysql_global_status_queries", 1000D),
                row("mysql", "mysql_global_status_bytes_received", 2048D),
                row("mysql", "mysql_global_status_bytes_sent", 4096D)));

        EndpointStatus status = service.overview().endpoints().get(0);

        assertEquals("healthy", status.state());
        assertEquals(5_000L, status.dataAgeMs());
        assertEquals("2026-10-19T18:29:55+08:00", status.lastObservedAt());
        assertEquals(1D, status.verificationValue());
        assertEquals(6, status.metricCount());
        assertEquals(5, status.metrics().size());
        assertNull(status.error());
    }

    @Test
    void reportsDegradedWhenVerificationMetricIsZero() {
        InfraMetricsService service = service(List.of(mysqlEndpoint()),
                List.of(row("mysql", "mysql_up", 0D)));

        EndpointStatus status = service.overview().endpoints().get(0);

        assertEquals("degraded", status.state());
        assertEquals(0D, status.verificationValue());
        assertTrue(status.error().contains("mysql_up"));
    }

    @Test
    void reportsDownWhenLatestSamplesExceedFreshnessThreshold() {
        InfraMetricsService service = service(List.of(mysqlEndpoint()),
                List.of(row("mysql", "mysql_up", 1D, NOW - 180_000L)));

        EndpointStatus status = service.overview().endpoints().get(0);

        assertEquals("down", status.state());
        assertEquals(180_000L, status.dataAgeMs());
        assertTrue(status.error().contains("超过 120 秒阈值"));
    }

    @Test
    void reportsDownWhenServiceHasNoIngestedSamples() {
        InfraMetricsService service = service(List.of(mysqlEndpoint()), List.of());

        EndpointStatus status = service.overview().endpoints().get(0);

        assertEquals("down", status.state());
        assertEquals(0, status.metricCount());
        assertTrue(status.error().contains("无入库样本"));
    }

    @Test
    void aggregatesKafkaTopicsConsumerGroupsAndLag() {
        InfraMetricsService service = service(List.of(kafkaEndpoint()), List.of(
                row("kafka", "kafka_brokers", 1D),
                row("kafka", "kafka_topic_partition_current_offset", 0D, "orders", null),
                row("kafka", "kafka_topic_partition_current_offset", 0D, "payments", null),
                row("kafka", "kafka_topic_partition_current_offset", 0D, "__consumer_offsets", null),
                row("kafka", "kafka_consumergroup_lag_sum", 4D, "orders", "worker"),
                row("kafka", "kafka_consumergroup_lag_sum", 6D, "orders", "billing")));

        EndpointStatus status = service.overview().endpoints().get(0);
        Map<String, MetricItem> metrics = status.metrics().stream()
                .collect(Collectors.toMap(MetricItem::key, Function.identity()));

        assertEquals("healthy", status.state());
        assertEquals(1D, metrics.get("brokers").value());
        assertEquals(2D, metrics.get("topics").value());
        assertEquals(2D, metrics.get("consumer_groups").value());
        assertEquals(10D, metrics.get("consumer_lag").value());
    }

    @Test
    void buildsSummaryAcrossHealthyDegradedAndMissingServices() {
        InfraMetricsService service = service(
                List.of(mysqlEndpoint(), redisEndpoint(), kafkaEndpoint()),
                List.of(
                        row("mysql", "mysql_up", 1D),
                        row("redis", "redis_up", 0D)));

        MonitorOverview overview = service.overview();

        assertEquals(3, overview.summary().total());
        assertEquals(1, overview.summary().healthy());
        assertEquals(1, overview.summary().degraded());
        assertEquals(1, overview.summary().down());
        assertEquals(5_000L, overview.summary().avgDataAgeMs());
        assertEquals(2, overview.summary().metricCount());
    }

    @Test
    void reportsClickHouseFailureAsDownWithoutFailingTheApi() {
        InfraMetricsService service = new InfraMetricsService(
                lookbackMinutes -> {
                    throw new IllegalStateException("clickhouse unavailable");
                },
                CLOCK, 15, STALE_MILLIS, List.of(mysqlEndpoint(), redisEndpoint()));

        MonitorOverview overview = service.overview();

        assertEquals(2, overview.endpoints().size());
        assertEquals(2, overview.summary().down());
        assertTrue(overview.endpoints().stream()
                .allMatch(status -> status.error().contains("读取 ClickHouse 指标失败")));
    }

    private InfraMetricsService service(List<EndpointDefinition> endpoints, List<MetricSeriesRow> rows) {
        return new InfraMetricsService(ignored -> rows, CLOCK, 15, STALE_MILLIS, endpoints);
    }

    private static EndpointDefinition mysqlEndpoint() {
        return endpoint("mysql", "MySQL Exporter", "MySQL", "mysql_up");
    }

    private static EndpointDefinition redisEndpoint() {
        return endpoint("redis", "Redis Exporter", "Redis", "redis_up");
    }

    private static EndpointDefinition kafkaEndpoint() {
        return endpoint("kafka", "Kafka Exporter", "Kafka", "kafka_brokers");
    }

    private static EndpointDefinition endpoint(String id, String name, String category,
                                               String verificationMetric) {
        return new EndpointDefinition(
                id, name, category, "exporter", "test",
                "http://zenvis-service-exporters:9104/metrics", "mysql-service:3306",
                verificationMetric, VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-" + id, "zenvis-service-exporters:9104", "/metrics", null));
    }

    private static MetricSeriesRow row(String service, String metricName, double value) {
        return row(service, metricName, value, LATEST);
    }

    private static MetricSeriesRow row(String service, String metricName, double value, long observedAtMillis) {
        return row(service, metricName, value, null, null, observedAtMillis);
    }

    private static MetricSeriesRow row(String service, String metricName, double value,
                                       String topic, String consumerGroup) {
        return row(service, metricName, value, topic, consumerGroup, LATEST);
    }

    private static MetricSeriesRow row(String service, String metricName, double value,
                                       String topic, String consumerGroup, long observedAtMillis) {
        return new MetricSeriesRow(
                service, service, metricName, value,
                "http://zenvis-service-exporters:9104/metrics", "test:9104",
                "gauge", "exporter", topic, consumerGroup, observedAtMillis);
    }
}
