package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SummaryQueryRequest(
        String entity,
        List<AnalyticsMetric> metrics,
        AnalyticsTimeRange timeRange,
        String timeField,
        String comparison,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic) {
}
