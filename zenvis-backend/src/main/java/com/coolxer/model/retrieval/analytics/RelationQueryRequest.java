package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RelationQueryRequest(
        String focusValue,
        AnalyticsTimeRange timeRange,
        Integer limit,
        List<Mapping> mappings) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mapping(
            String entity,
            String sourceField,
            String targetField,
            String timeField,
            List<RequestCriteriaDto> criteriaList,
            String criteriaLogic) {
    }
}
