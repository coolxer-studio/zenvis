package com.coolxer.plugin.infra.monitor.service;

import com.coolxer.plugin.infra.monitor.model.EndpointStatus;
import com.coolxer.plugin.infra.monitor.model.MetricItem;
import com.coolxer.plugin.infra.monitor.model.MonitorOverview;
import com.coolxer.plugin.infra.monitor.model.MonitorSummary;
import com.coolxer.plugin.infra.monitor.model.PrometheusScrape;
import com.coolxer.plugin.infra.monitor.service.EndpointDefinition.VerificationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InfraMetricsService {

    private static final String STATE_HEALTHY = "healthy";
    private static final String STATE_DEGRADED = "degraded";
    private static final String STATE_DOWN = "down";
    private static final int MAX_ERROR_LENGTH = 320;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final MetricSnapshotStore snapshotStore;
    private final List<EndpointDefinition> endpoints;
    private final Clock clock;
    private final int lookbackMinutes;
    private final long staleMillis;

    @Autowired
    public InfraMetricsService(MetricSnapshotStore snapshotStore) {
        this(snapshotStore,
                Clock.systemUTC(),
                envInt("ZENVIS_INFRA_LOOKBACK_MINUTES", 15, 5, 1440),
                Duration.ofSeconds(envInt("ZENVIS_INFRA_STALE_SECONDS", 120, 30, 3600)).toMillis(),
                defaultEndpoints());
    }

    InfraMetricsService(MetricSnapshotStore snapshotStore, Clock clock, int lookbackMinutes,
                        long staleMillis, List<EndpointDefinition> endpoints) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lookbackMinutes = Math.max(1, lookbackMinutes);
        this.staleMillis = Math.max(1L, staleMillis);
        this.endpoints = List.copyOf(endpoints);
    }

    public MonitorOverview overview() {
        long nowMillis = clock.millis();
        List<MetricSeriesRow> rows;
        try {
            rows = snapshotStore.loadLatestBatch(lookbackMinutes);
        } catch (RuntimeException exception) {
            return unavailableOverview(nowMillis, exception);
        }

        Map<String, List<MetricSeriesRow>> byService = rows.stream()
                .filter(row -> row.service() != null && !row.service().isBlank())
                .collect(Collectors.groupingBy(MetricSeriesRow::service));

        List<EndpointStatus> statuses = endpoints.stream()
                .map(endpoint -> endpointStatus(endpoint,
                        byService.getOrDefault(endpoint.id(), List.of()), nowMillis))
                .toList();

        int healthy = 0;
        int degraded = 0;
        int down = 0;
        int metricCount = 0;
        long ageTotal = 0L;
        int ageSamples = 0;
        for (EndpointStatus status : statuses) {
            switch (status.state()) {
                case STATE_HEALTHY -> healthy++;
                case STATE_DEGRADED -> degraded++;
                default -> down++;
            }
            metricCount += status.metricCount();
            if (status.lastObservedAt() != null) {
                ageTotal += status.dataAgeMs();
                ageSamples++;
            }
        }
        long avgAge = ageSamples == 0 ? 0L : ageTotal / ageSamples;
        MonitorSummary summary = new MonitorSummary(
                statuses.size(), healthy, degraded, down, avgAge, metricCount);
        return new MonitorOverview(formatInstant(nowMillis), summary, statuses);
    }

    private EndpointStatus endpointStatus(EndpointDefinition endpoint, List<MetricSeriesRow> rows,
                                          long nowMillis) {
        if (rows.isEmpty()) {
            return new EndpointStatus(
                    endpoint.id(), endpoint.name(), endpoint.category(), endpoint.mode(),
                    endpoint.description(), endpoint.url(), endpoint.target(), STATE_DOWN,
                    0L, null, endpoint.verificationMetric(), null, 0, List.of(),
                    endpoint.prometheus(), "近 " + lookbackMinutes + " 分钟无入库样本");
        }

        long latestMillis = rows.stream().mapToLong(MetricSeriesRow::observedAtMillis).max().orElse(nowMillis);
        long ageMillis = Math.max(0L, nowMillis - latestMillis);
        String lastObservedAt = formatInstant(latestMillis);
        if (ageMillis > staleMillis) {
            return new EndpointStatus(
                    endpoint.id(), endpoint.name(), endpoint.category(), endpoint.mode(),
                    endpoint.description(), endpointUrl(rows, endpoint), endpoint.target(), STATE_DOWN,
                    ageMillis, lastObservedAt, endpoint.verificationMetric(), null, rows.size(),
                    metricsFor(endpoint.id(), rows), endpoint.prometheus(),
                    "最近入库样本延迟 " + ageMillis / 1000L + " 秒，超过 " + staleMillis / 1000L + " 秒阈值");
        }

        MetricSeriesRow verification = rows.stream()
                .filter(row -> endpoint.verificationMetric().equals(row.metricName()))
                .max(Comparator.comparingLong(MetricSeriesRow::observedAtMillis))
                .orElse(null);
        boolean verified;
        String error = null;
        if (verification == null) {
            verified = false;
            error = "最新批次缺少验证指标 " + endpoint.verificationMetric();
        } else if (endpoint.verificationMode() == VerificationMode.POSITIVE) {
            verified = verification.metricValue() > 0D;
            if (!verified) {
                error = "验证指标 " + endpoint.verificationMetric() + " 当前为 0";
            }
        } else {
            verified = true;
        }

        String state = verified ? STATE_HEALTHY : STATE_DEGRADED;
        return new EndpointStatus(
                endpoint.id(), endpoint.name(), endpoint.category(), endpoint.mode(),
                endpoint.description(), endpointUrl(rows, endpoint), endpoint.target(), state,
                ageMillis, lastObservedAt, endpoint.verificationMetric(),
                verification == null ? null : verification.metricValue(), rows.size(),
                metricsFor(endpoint.id(), rows), endpoint.prometheus(), error);
    }

    private MonitorOverview unavailableOverview(long nowMillis, RuntimeException exception) {
        String reason = limit("读取 ClickHouse 指标失败: " + describeError(exception));
        List<EndpointStatus> statuses = endpoints.stream()
                .map(endpoint -> new EndpointStatus(
                        endpoint.id(), endpoint.name(), endpoint.category(), endpoint.mode(),
                        endpoint.description(), endpoint.url(), endpoint.target(), STATE_DOWN,
                        0L, null, endpoint.verificationMetric(), null, 0, List.of(),
                        endpoint.prometheus(), reason))
                .toList();
        return new MonitorOverview(
                formatInstant(nowMillis),
                new MonitorSummary(statuses.size(), 0, 0, statuses.size(), 0L, 0),
                statuses);
    }

    private List<MetricItem> metricsFor(String id, List<MetricSeriesRow> rows) {
        List<MetricItem> metrics = new ArrayList<>();
        switch (id) {
            case "mysql" -> {
                add(metrics, "uptime_seconds", "运行时长",
                        value(rows, "mysql_global_status_uptime"), "s", "duration");
                add(metrics, "threads_connected", "当前连接",
                        value(rows, "mysql_global_status_threads_connected"), "", "number");
                add(metrics, "queries", "累计查询",
                        value(rows, "mysql_global_status_queries"), "", "number");
                add(metrics, "bytes_received", "累计接收",
                        value(rows, "mysql_global_status_bytes_received"), "B", "bytes");
                add(metrics, "bytes_sent", "累计发送",
                        value(rows, "mysql_global_status_bytes_sent"), "B", "bytes");
            }
            case "redis", "redis-stack" -> {
                add(metrics, "uptime_seconds", "运行时长",
                        value(rows, "redis_uptime_in_seconds"), "s", "duration");
                add(metrics, "connected_clients", "当前连接",
                        value(rows, "redis_connected_clients"), "", "number");
                add(metrics, "memory_used_bytes", "内存占用",
                        value(rows, "redis_memory_used_bytes"), "B", "bytes");
                add(metrics, "commands_processed_total", "累计命令",
                        value(rows, "redis_commands_processed_total"), "", "number");
                add(metrics, "keyspace_hits_total", "命中次数",
                        value(rows, "redis_keyspace_hits_total"), "", "number");
            }
            case "kafka" -> {
                add(metrics, "brokers", "Broker 数量",
                        value(rows, "kafka_brokers"), "", "number");
                long topics = rows.stream()
                        .filter(row -> "kafka_topic_partition_current_offset".equals(row.metricName()))
                        .map(MetricSeriesRow::topic)
                        .filter(topic -> topic != null && !topic.isBlank()
                                && !"__consumer_offsets".equals(topic))
                        .distinct()
                        .count();
                add(metrics, "topics", "业务 Topic", (double) topics, "", "number");
                long groups = rows.stream()
                        .filter(row -> "kafka_consumergroup_lag_sum".equals(row.metricName()))
                        .map(MetricSeriesRow::consumerGroup)
                        .filter(group -> group != null && !group.isBlank())
                        .distinct()
                        .count();
                add(metrics, "consumer_groups", "消费组", (double) groups, "", "number");
                boolean hasLagSum = rows.stream()
                        .anyMatch(row -> "kafka_consumergroup_lag_sum".equals(row.metricName()));
                double lag = hasLagSum
                        ? sum(rows, "kafka_consumergroup_lag_sum")
                        : sum(rows, "kafka_consumergroup_lag");
                add(metrics, "consumer_lag", "消费积压", lag, "条", "number");
            }
            case "clickhouse" -> {
                add(metrics, "queries", "累计查询",
                        value(rows, "ClickHouseProfileEvents_Query"), "", "number");
                add(metrics, "select_queries", "SELECT 查询",
                        value(rows, "ClickHouseProfileEvents_SelectQuery"), "", "number");
                add(metrics, "insert_queries", "INSERT 查询",
                        value(rows, "ClickHouseProfileEvents_InsertQuery"), "", "number");
                add(metrics, "inserted_rows", "写入行数",
                        value(rows, "ClickHouseProfileEvents_InsertedRows"), "", "number");
                add(metrics, "failed_queries", "失败查询",
                        value(rows, "ClickHouseProfileEvents_FailedQuery"), "", "number");
            }
            case "minio" -> {
                add(metrics, "usage_total_bytes", "已用容量",
                        value(rows, "minio_cluster_usage_total_bytes"), "B", "bytes");
                add(metrics, "objects", "对象数量",
                        value(rows, "minio_cluster_usage_object_total"), "", "number");
                add(metrics, "capacity_free_bytes", "可用容量",
                        value(rows, "minio_cluster_capacity_usable_free_bytes"), "B", "bytes");
                add(metrics, "buckets", "Bucket 数量",
                        value(rows, "minio_cluster_bucket_total"), "", "number");
                add(metrics, "nodes_online", "在线节点",
                        value(rows, "minio_cluster_nodes_online_total"), "", "number");
                add(metrics, "drives_offline", "离线磁盘",
                        value(rows, "minio_cluster_drive_offline_total"), "", "number");
            }
            default -> {
            }
        }
        return metrics;
    }

    private Double value(List<MetricSeriesRow> rows, String metricName) {
        return rows.stream()
                .filter(row -> metricName.equals(row.metricName()))
                .max(Comparator.comparingLong(MetricSeriesRow::observedAtMillis))
                .map(MetricSeriesRow::metricValue)
                .orElse(null);
    }

    private double sum(List<MetricSeriesRow> rows, String metricName) {
        return rows.stream()
                .filter(row -> metricName.equals(row.metricName()))
                .mapToDouble(MetricSeriesRow::metricValue)
                .sum();
    }

    private void add(List<MetricItem> metrics, String key, String label, Double value,
                     String unit, String format) {
        if (value != null) {
            metrics.add(new MetricItem(key, label, value, unit, format));
        }
    }

    private String endpointUrl(List<MetricSeriesRow> rows, EndpointDefinition endpoint) {
        return rows.stream()
                .max(Comparator.comparingLong(MetricSeriesRow::observedAtMillis))
                .map(MetricSeriesRow::endpoint)
                .filter(url -> url != null && !url.isBlank())
                .orElse(endpoint.url());
    }

    private static String formatInstant(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(DISPLAY_ZONE)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String describeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private static String limit(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private static List<EndpointDefinition> defaultEndpoints() {
        List<EndpointDefinition> definitions = new ArrayList<>();
        definitions.add(new EndpointDefinition(
                "mysql", "MySQL Exporter", "MySQL", "exporter",
                "mysqld_exporter 采集 mysql-service 的连接、查询与网络流量",
                env("ZENVIS_MYSQL_EXPORTER_URL", "http://zenvis-service-exporters:9104/metrics"),
                "mysql-service:3306", "mysql_up", VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-mysql", "zenvis-service-exporters:9104", "/metrics", null)));

        definitions.add(new EndpointDefinition(
                "redis", "Redis Exporter", "Redis", "exporter",
                "redis_exporter 采集 redis-service 的连接、内存与命令指标",
                env("ZENVIS_REDIS_EXPORTER_URL", "http://zenvis-service-exporters:9121/metrics"),
                "redis-service:6379", "redis_up", VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-redis", "zenvis-service-exporters:9121", "/metrics", null)));

        definitions.add(new EndpointDefinition(
                "redis-stack", "Redis-Stack Exporter", "Redis-Stack", "exporter",
                "通过 redis_exporter 多目标抓取 redis-stack-service",
                env("ZENVIS_REDIS_STACK_EXPORTER_URL",
                        "http://zenvis-service-exporters:9121/scrape?target=redis://redis-stack-service:6379"),
                "redis-stack-service:6379", "redis_up", VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-redis-stack", "zenvis-service-exporters:9121", "/scrape",
                        "redis://redis-stack-service:6379")));

        definitions.add(new EndpointDefinition(
                "kafka", "Kafka Exporter", "Kafka", "exporter",
                "kafka_exporter 采集 kafka-service 的 Broker、Topic 与消费积压",
                env("ZENVIS_KAFKA_EXPORTER_URL", "http://zenvis-service-exporters:9308/metrics"),
                "kafka-service:9092", "kafka_brokers", VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-kafka", "zenvis-service-exporters:9308", "/metrics", null)));

        definitions.add(new EndpointDefinition(
                "clickhouse", "ClickHouse Native", "ClickHouse", "native",
                "ClickHouse 内置 Prometheus 指标端点，指标由推送任务采集入库",
                env("ZENVIS_CLICKHOUSE_METRICS_URL", "http://clickhouse-service:9363/metrics"),
                "clickhouse-service:9363", "ClickHouseProfileEvents_Query", VerificationMode.PRESENT,
                new PrometheusScrape("zenvis-clickhouse", "clickhouse-service:9363", "/metrics", null)));

        definitions.add(new EndpointDefinition(
                "minio", "MinIO Native", "MinIO", "native",
                "MinIO 内置 Prometheus 集群指标端点，指标由推送任务采集入库",
                env("ZENVIS_MINIO_METRICS_URL", "http://minio-service:9000/minio/v2/metrics/cluster"),
                "minio-service:9000", "minio_cluster_health_status", VerificationMode.POSITIVE,
                new PrometheusScrape("zenvis-minio", "minio-service:9000",
                        "/minio/v2/metrics/cluster", null)));
        return definitions;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int envInt(String key, int fallback, int minimum, int maximum) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
