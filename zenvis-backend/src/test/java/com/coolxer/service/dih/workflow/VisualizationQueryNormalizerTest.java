package com.coolxer.service.dih.workflow;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationQueryNormalizerTest {

    @Test
    void normalizesDimensionKindsWithoutMutatingTheOriginalQuery() {
        Map<String, Object> originalDimension = new LinkedHashMap<>(Map.of(
                "field", "event_type",
                "kind", "CATEGORY"));
        Map<String, Object> request = new LinkedHashMap<>(Map.of(
                "entity", "user_event",
                "dimensions", List.of(originalDimension)));
        Map<String, Object> query = new LinkedHashMap<>(Map.of(
                "tool", "entity_aggregate",
                "request", request));

        Map<String, Object> normalized = VisualizationQueryNormalizer.normalizeQuery(query);

        assertThat(dimensions(normalized)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "FIELD"));
        assertThat(originalDimension).containsEntry("kind", "CATEGORY");
    }

    @Test
    void mapsTimeAliasesToTheSupportedKind() {
        Map<String, Object> query = Map.of(
                "tool", "entity_trend",
                "request", Map.of(
                        "entity", "user_event",
                        "dimensions", List.of(Map.of(
                                "field", "zenvis_insert_time",
                                "kind", "DATETIME"))));

        Map<String, Object> normalized = VisualizationQueryNormalizer.normalizeQuery(query);

        assertThat(dimensions(normalized)).singleElement()
                .satisfies(dimension -> assertThat(dimension).containsEntry("kind", "TIME"));
    }

    private static List<Map<String, Object>> dimensions(Map<String, Object> query) {
        Map<String, Object> request = (Map<String, Object>) query.get("request");
        return (List<Map<String, Object>>) request.get("dimensions");
    }
}
