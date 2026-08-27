package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 单实体受控多维聚合请求。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AggregateQueryRequest(
        String entity,
        List<Dimension> dimensions,
        List<AnalyticsMetric> metrics,
        AnalyticsTimeRange timeRange,
        String timeField,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic,
        OrderBy orderBy,
        Integer limit,
        String chartHint) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Dimension(
            String name,
            String field,
            String label,
            String kind,
            String granularity,
            Boolean includeNull) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OrderBy(
            String field,
            String direction) {
    }
}
