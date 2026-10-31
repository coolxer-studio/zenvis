package com.coolxer.service.dih.workflow;

import com.coolxer.service.retrieval.AnalyticsTimeRangeRules;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates a visualization query plan before it is presented to the user.
 * The time-range portion delegates to the retrieval rules used by MCP execution.
 */
public final class VisualizationQueryValidator {

    private static final ZoneId VALIDATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String, String> DEFAULT_PRESETS = Map.ofEntries(
            Map.entry("entity_overview", "TODAY"),
            Map.entry("entity_summary", "ALL_TIME"),
            Map.entry("entity_trend", "LAST_7_DAYS"),
            Map.entry("entity_distribution", "ALL_TIME"),
            Map.entry("entity_aggregate", "LAST_30_DAYS"),
            Map.entry("entity_histogram", "ALL_TIME"),
            Map.entry("entity_scatter", "ALL_TIME"),
            Map.entry("entity_value_statistics", "ALL_TIME"),
            Map.entry("entity_relations", "LAST_7_DAYS"),
            Map.entry("entity_relation_timeline", "LAST_7_DAYS"));
    private static final Set<String> NO_ANALYTICS_TIME_RANGE_TOOLS = Set.of(
            "retrieval_search", "entity_list");
    private static final Set<String> ALL_TIME_TOOLS = Set.of(
            "entity_overview", "entity_summary", "entity_distribution",
            "entity_histogram", "entity_scatter", "entity_value_statistics");

    private VisualizationQueryValidator() {
    }

    public static String validate(String tool, Map<String, Object> request) {
        if (!StringUtils.isNotBlank(tool)) {
            return "查询工具不能为空";
        }
        if (request == null || request.isEmpty()) {
            return "查询请求不能为空";
        }
        Map<String, Object> normalized = VisualizationQueryNormalizer.normalizeRequest(request);
        if (NO_ANALYTICS_TIME_RANGE_TOOLS.contains(tool)) {
            return "";
        }
        String defaultPreset = DEFAULT_PRESETS.get(tool);
        if (defaultPreset == null) {
            return "不支持的图表查询工具：" + tool;
        }

        Map<String, Object> timeRange = map(normalized.get("time_range"));
        if (!timeRange.isEmpty()) {
            String timeError = AnalyticsTimeRangeRules.validate(
                    text(timeRange.get("preset")),
                    text(timeRange.get("start_time")),
                    text(timeRange.get("end_time")),
                    defaultPreset,
                    allowsAllTime(tool, normalized),
                    VALIDATION_ZONE,
                    LocalDateTime.now(VALIDATION_ZONE));
            if (StringUtils.isNotBlank(timeError)) {
                return "时间范围无效：" + timeError;
            }
        }
        return validateToolShape(tool, normalized);
    }

    private static String validateToolShape(String tool, Map<String, Object> request) {
        return switch (tool) {
            case "entity_distribution" -> {
                if (!StringUtils.isNotBlank(text(request.get("dimension")))
                        && list(request.get("mappings")).isEmpty()) {
                    yield "分布查询必须提供 dimension 或 mappings";
                }
                yield validateIntegerRange(request, "limit", 1, 100);
            }
            case "entity_aggregate" -> {
                if (list(request.get("dimensions")).size() > 2) {
                    yield "聚合维度不能超过2个";
                }
                yield validateIntegerRange(request, "limit", 1, 100);
            }
            case "entity_histogram" -> validateIntegerRange(request, "bins", 5, 100);
            case "entity_scatter" -> validateIntegerRange(request, "limit", 1, 2000);
            default -> "";
        };
    }

    private static boolean allowsAllTime(String tool, Map<String, Object> request) {
        if (!"entity_aggregate".equals(tool)) {
            return ALL_TIME_TOOLS.contains(tool);
        }
        return list(request.get("dimensions")).stream()
                .map(VisualizationQueryValidator::map)
                .noneMatch(dimension -> "TIME".equals(
                        StringUtils.upperCase(text(dimension.get("kind")), Locale.ROOT)));
    }

    private static String validateIntegerRange(Map<String, Object> request,
                                               String field,
                                               int min,
                                               int max) {
        Object raw = request.get(field);
        if (raw == null) {
            return "";
        }
        if (!(raw instanceof Number number)) {
            return field + "必须为数字";
        }
        int value = number.intValue();
        return value < min || value > max
                ? field + "必须为" + min + "到" + max
                : "";
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) {
                result.put(key.toString(), item);
            }
        });
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
