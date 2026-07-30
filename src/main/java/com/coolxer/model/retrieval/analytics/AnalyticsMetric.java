package com.coolxer.model.retrieval.analytics;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyticsMetric(
        String name,
        String operation,
        String field,
        String label,
        Double percentile) {

    public AnalyticsMetric(String name, String operation, String field, String label) {
        this(name, operation, field, label, null);
    }
}
