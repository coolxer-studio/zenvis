package com.coolxer.model.retrieval.analytics;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单实体数值字段直方图请求。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HistogramQueryRequest(
        String entity,
        String field,
        Integer bins,
        BigDecimal min,
        BigDecimal max,
        AnalyticsTimeRange timeRange,
        String timeField,
        List<RequestCriteriaDto> criteriaList,
        String criteriaLogic) {
}
