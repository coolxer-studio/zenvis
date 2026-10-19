package com.coolxer.plugin.infra.monitor.model;

public record MonitorSummary(
        int total,
        int healthy,
        int degraded,
        int down,
        long avgDataAgeMs,
        int metricCount
) {
}
