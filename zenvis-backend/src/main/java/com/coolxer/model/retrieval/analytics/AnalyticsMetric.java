package com.coolxer.model.retrieval.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyticsMetric(
        @JsonProperty(required = false)
        @JsonPropertyDescription("指标输出名称；省略时按聚合类型和字段生成")
        String name,
        @JsonProperty(required = true)
        @JsonPropertyDescription("聚合类型，如 COUNT、SUM、AVG、MIN、MAX、PERCENTILE")
        String operation,
        @JsonProperty(required = false)
        @JsonPropertyDescription("指标字段；COUNT 必须省略，其他聚合类型必填")
        String field,
        @JsonProperty(required = false)
        @JsonPropertyDescription("指标展示名称；省略时使用字段标签")
        String label,
        @JsonProperty(required = false)
        @JsonPropertyDescription("百分位，仅在 operation=PERCENTILE 时填写，取值大于 0 且小于 1")
        Double percentile) {

    public AnalyticsMetric(String name, String operation, String field, String label) {
        this(name, operation, field, label, null);
    }
}
