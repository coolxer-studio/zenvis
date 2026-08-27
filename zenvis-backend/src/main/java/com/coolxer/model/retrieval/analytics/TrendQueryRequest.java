package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrendQueryRequest(
        List<String> entities,
        List<SeriesMapping> series,
        AnalyticsTimeRange timeRange,
        String granularity,
        String comparison,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SeriesMapping(
            String entity,
            String label,
            AnalyticsMetric metric,
            String timeField,
            List<RequestCriteriaDto> criteriaList,
            String criteriaLogic) {
    }
}
