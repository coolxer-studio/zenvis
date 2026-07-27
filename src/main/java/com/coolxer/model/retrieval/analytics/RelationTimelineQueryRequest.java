package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RelationTimelineQueryRequest(
        String focusValue,
        AnalyticsTimeRange timeRange,
        String granularity,
        Integer categoryLimit,
        List<Mapping> mappings) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Mapping(
            String entity,
            String sourceField,
            String targetField,
            String timeField,
            String categoryField,
            CategoryExtraction categoryExtraction,
            List<RequestCriteriaDto> criteriaList,
            String criteriaLogic) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CategoryExtraction(
            String type,
            Integer start,
            Integer length) {
    }
}
