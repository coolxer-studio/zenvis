package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DistributionQueryRequest(
        String entity,
        String dimension,
        String label,
        List<Mapping> mappings,
        AnalyticsTimeRange timeRange,
        String timeField,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic,
        Integer limit,
        Boolean includeNull) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mapping(
            String entity,
            String dimension,
            String label,
            String timeField,
            List<RequestCriteriaDto> criteriaList,
            String criteriaLogic) {
    }
}
