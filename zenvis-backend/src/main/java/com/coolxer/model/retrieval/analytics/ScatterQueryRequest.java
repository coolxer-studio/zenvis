package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 单实体散点图/气泡图请求。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ScatterQueryRequest(
        String entity,
        String xField,
        String yField,
        String sizeField,
        String categoryField,
        String labelField,
        AnalyticsTimeRange timeRange,
        String timeField,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic,
        String sortBy,
        String order,
        Integer limit) {
}
