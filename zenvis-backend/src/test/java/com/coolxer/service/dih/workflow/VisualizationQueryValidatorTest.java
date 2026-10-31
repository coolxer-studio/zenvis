package com.coolxer.service.dih.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationQueryValidatorTest {

    @Test
    void acceptsTheAllTimeAliasOnlyForToolsThatSupportIt() {
        Map<String, Object> distribution = Map.of(
                "entity", "school_info",
                "dimension", "school_level",
                "time_range", Map.of("preset", "ALL"));

        assertThat(VisualizationQueryValidator.validate(
                "entity_distribution", distribution)).isEmpty();

        Map<String, Object> trend = Map.of(
                "entities", List.of("school_info"),
                "time_range", Map.of("preset", "ALL"));
        assertThat(VisualizationQueryValidator.validate("entity_trend", trend))
                .isEqualTo("时间范围无效：当前接口不支持ALL_TIME");
    }

    @Test
    void rejectsInvalidCustomWindowsAndToolLimits() {
        assertThat(VisualizationQueryValidator.validate("entity_distribution", Map.of(
                "entity", "school_info",
                "dimension", "school_level",
                "time_range", Map.of(
                        "preset", "CUSTOM",
                        "start_time", "2020-01-01 00:00:00",
                        "end_time", "2026-12-31 23:59:59"))))
                .isEqualTo("时间范围无效：时间跨度不能超过3年");

        assertThat(VisualizationQueryValidator.validate("entity_distribution", Map.of(
                "entity", "school_info",
                "dimension", "school_level",
                "limit", 101)))
                .isEqualTo("limit必须为1到100");
    }

    @Test
    void aggregateAllowsAllTimeOnlyWhenThereIsNoTimeDimension() {
        Map<String, Object> categoryOnly = Map.of(
                "entity", "school_info",
                "dimensions", List.of(Map.of("field", "school_level", "kind", "FIELD")),
                "time_range", Map.of("preset", "ALL_TIME"));
        Map<String, Object> withTime = Map.of(
                "entity", "school_info",
                "dimensions", List.of(Map.of("field", "school_create_time", "kind", "TIME")),
                "time_range", Map.of("preset", "ALL_TIME"));

        assertThat(VisualizationQueryValidator.validate(
                "entity_aggregate", categoryOnly)).isEmpty();
        assertThat(VisualizationQueryValidator.validate("entity_aggregate", withTime))
                .isEqualTo("时间范围无效：当前接口不支持ALL_TIME");
    }
}
