package com.coolxer.plugin.infra.monitor;

import com.coolxer.plugin.infra.monitor.model.ApiResponse;
import com.coolxer.plugin.infra.monitor.model.EndpointStatus;
import com.coolxer.plugin.infra.monitor.model.MetricItem;
import com.coolxer.plugin.infra.monitor.model.MonitorOverview;
import com.coolxer.plugin.infra.monitor.model.MonitorSummary;
import com.coolxer.plugin.infra.monitor.model.PrometheusScrape;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfraMonitorContractTest {

    @Test
    void serializesCanonicalSnakeCaseFields() throws Exception {
        EndpointStatus endpoint = new EndpointStatus(
                "mysql", "MySQL Exporter", "MySQL", "exporter", "test",
                "http://zenvis-service-exporters:9104/metrics", "mysql-service:3306",
                "healthy", 12_000L, "2026-10-19T14:00:00+08:00", "mysql_up", 1D, 1,
                List.of(new MetricItem("uptime_seconds", "运行时长", 120D, "s", "duration")),
                new PrometheusScrape("zenvis-mysql", "zenvis-service-exporters:9104", "/metrics", null),
                null);
        MonitorOverview overview = new MonitorOverview(
                "2026-10-19T14:00:00+08:00",
                new MonitorSummary(1, 1, 0, 0, 12L, 1),
                List.of(endpoint));

        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE);
        String json = mapper.writeValueAsString(ApiResponse.success(overview));

        assertTrue(json.contains("\"generated_at\""));
        assertTrue(json.contains("\"avg_data_age_ms\""));
        assertTrue(json.contains("\"data_age_ms\""));
        assertTrue(json.contains("\"last_observed_at\""));
        assertTrue(json.contains("\"verification_metric\""));
        assertTrue(json.contains("\"metrics_path\""));
        assertTrue(json.contains("\"target_parameter\""));
        assertFalse(json.contains("\"http_status\""));
        assertFalse(json.contains("\"dataAgeMs\""));
        assertFalse(json.contains("\"avgDataAgeMs\""));
    }
}
