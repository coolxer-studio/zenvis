package com.coolxer.plugin.infra.monitor.service;

public record MetricSeriesRow(
        String service,
        String serviceName,
        String metricName,
        double metricValue,
        String endpoint,
        String instance,
        String metricKind,
        String sourceType,
        String topic,
        String consumerGroup,
        long observedAtMillis
) {
}
