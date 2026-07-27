package com.coolxer.model.retrieval.analytics;

import java.util.Map;

public record AnalyticsResponse(
        Map<String, Object> meta,
        Map<String, Object> result,
        Map<String, Object> echarts) {
}
