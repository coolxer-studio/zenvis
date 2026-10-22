package com.coolxer.service.dih.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes model-generated visualization query dimensions to the vocabulary
 * accepted by the retrieval MCP analytics endpoints.
 */
public final class VisualizationQueryNormalizer {

    private static final Set<String> TIME_KINDS = Set.of(
            "TIME", "DATE", "DATETIME", "DATE_TIME", "TIMESTAMP", "TIMESTAMP_TZ");

    private VisualizationQueryNormalizer() {
    }

    public static Map<String, Object> normalizeQuery(Object value) {
        if (!(value instanceof Map<?, ?> query)) {
            return Map.of();
        }
        return map(query);
    }

    public static Map<String, Object> normalizeRequest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return request == null ? Map.of() : new LinkedHashMap<>();
        }
        return map(request);
    }

    private static Map<String, Object> map(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String name = key.toString();
            normalized.put(name, "dimensions".equals(name) && value instanceof List<?> dimensions
                    ? dimensions(dimensions) : nested(value));
        });
        return normalized;
    }

    private static List<Object> dimensions(List<?> dimensions) {
        List<Object> normalized = new ArrayList<>(dimensions.size());
        for (Object value : dimensions) {
            if (!(value instanceof Map<?, ?> dimension)) {
                normalized.add(nested(value));
                continue;
            }
            Map<String, Object> item = map(dimension);
            String kind = text(item.get("kind")).toUpperCase(Locale.ROOT);
            item.put("kind", TIME_KINDS.contains(kind) ? "TIME" : "FIELD");
            normalized.add(item);
        }
        return normalized;
    }

    private static Object nested(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map(map);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(item -> normalized.add(nested(item)));
            return normalized;
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
