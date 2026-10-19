package com.coolxer.plugin.infra.monitor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
public class ClickHouseMetricSnapshotStore implements MetricSnapshotStore {

    private static final String DATA_SOURCE_BEAN = "clickHouseDataSource";
    private static final String LATEST_BATCH_QUERY = """
            SELECT s.service,
                   s.service_name,
                   s.metric_name,
                   s.metric_value,
                   s.endpoint,
                   s.instance,
                   s.metric_kind,
                   s.source_type,
                   JSONExtractString(toString(s.labels), 'tags', 'topic') AS topic,
                   JSONExtractString(toString(s.labels), 'tags', 'consumergroup') AS consumer_group,
                   toUnixTimestamp64Milli(s.observed_at) AS observed_at_ms
            FROM infra_metric_sample AS s
            WHERE s.observed_at >= now64(3) - toIntervalMinute(:lookback_minutes)
              AND (s.service, s.observed_at) IN (
                  SELECT service, max(observed_at)
                  FROM infra_metric_sample
                  WHERE observed_at >= now64(3) - toIntervalMinute(:lookback_minutes)
                  GROUP BY service
              )
            ORDER BY s.service, s.metric_name, topic, consumer_group
            """;

    @Autowired(required = false)
    private ApplicationContext applicationContext;
    private volatile NamedParameterJdbcTemplate jdbcTemplate;

    public ClickHouseMetricSnapshotStore() {
    }

    public ClickHouseMetricSnapshotStore(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<MetricSeriesRow> loadLatestBatch(int lookbackMinutes) {
        return template().query(
                LATEST_BATCH_QUERY,
                new MapSqlParameterSource("lookback_minutes", lookbackMinutes),
                this::mapRow
        );
    }

    private MetricSeriesRow mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new MetricSeriesRow(
                resultSet.getString("service"),
                resultSet.getString("service_name"),
                resultSet.getString("metric_name"),
                resultSet.getDouble("metric_value"),
                resultSet.getString("endpoint"),
                resultSet.getString("instance"),
                resultSet.getString("metric_kind"),
                resultSet.getString("source_type"),
                resultSet.getString("topic"),
                resultSet.getString("consumer_group"),
                resultSet.getLong("observed_at_ms")
        );
    }

    private NamedParameterJdbcTemplate template() {
        NamedParameterJdbcTemplate current = jdbcTemplate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (jdbcTemplate == null) {
                if (applicationContext == null || !applicationContext.containsBean(DATA_SOURCE_BEAN)) {
                    throw new IllegalStateException("平台未提供 " + DATA_SOURCE_BEAN);
                }
                DataSource dataSource = applicationContext.getBean(DATA_SOURCE_BEAN, DataSource.class);
                JdbcTemplate base = new JdbcTemplate(dataSource);
                base.setQueryTimeout(envInt("ZENVIS_INFRA_QUERY_TIMEOUT_SECONDS", 10, 1, 60));
                jdbcTemplate = new NamedParameterJdbcTemplate(base);
            }
            return jdbcTemplate;
        }
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
