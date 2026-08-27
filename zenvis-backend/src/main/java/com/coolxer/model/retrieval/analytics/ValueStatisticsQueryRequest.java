package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ValueStatisticsQueryRequest(
        String focusValue,
        List<Mapping> mappings,
        AnalyticsTimeRange timeRange) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mapping(
            String entity,
            String label,
            List<String> matchFields,
            String timeField,
            List<RequestCriteriaDto> criteriaList,
            String criteriaLogic) {
    }
}
