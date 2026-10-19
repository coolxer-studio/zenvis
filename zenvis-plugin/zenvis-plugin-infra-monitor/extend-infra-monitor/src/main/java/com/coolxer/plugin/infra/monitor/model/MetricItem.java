package com.coolxer.plugin.infra.monitor.model;

public record MetricItem(
        String key,
        String label,
        Double value,
        String unit,
        String format
) {
}
