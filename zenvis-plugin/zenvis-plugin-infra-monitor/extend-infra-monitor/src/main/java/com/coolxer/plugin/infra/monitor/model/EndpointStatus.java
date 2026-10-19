package com.coolxer.plugin.infra.monitor.model;

import java.util.List;

public record EndpointStatus(
        String id,
        String name,
        String category,
        String mode,
        String description,
        String endpoint,
        String target,
        String state,
        long dataAgeMs,
        String lastObservedAt,
        String verificationMetric,
        Double verificationValue,
        int metricCount,
        List<MetricItem> metrics,
        PrometheusScrape prometheus,
        String error
) {
}
