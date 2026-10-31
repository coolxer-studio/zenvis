package com.coolxer.service.retrieval;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Time-range rules shared by the retrieval analytics MCP tools and the
 * visualization workflow plan validator. Keeping both paths on the same rules
 * prevents a plan from being marked valid when the tool would reject it later.
 */
public final class AnalyticsTimeRangeRules {

    public static final Duration MAX_CUSTOM_RANGE = Duration.ofDays(366L * 3);

    private static final DateTimeFormatter BOUNDARY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter SECOND_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SUPPORTED_PRESETS = Set.of(
            "TODAY", "YESTERDAY", "LAST_24_HOURS", "LAST_7_DAYS",
            "LAST_30_DAYS", "THIS_MONTH", "CUSTOM", "ALL_TIME");

    private AnalyticsTimeRangeRules() {
    }

    public static String normalizePreset(String value) {
        String preset = StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        return "ALL".equals(preset) ? "ALL_TIME" : preset;
    }

    /**
     * Resolves a request time range using exactly the same presets, aliases,
     * custom-date formats and maximum span as the MCP execution path.
     */
    public static Resolved resolve(String preset,
                                   String startTime,
                                   String endTime,
                                   String defaultPreset,
                                   boolean allowAllTime,
                                   ZoneId zone,
                                   LocalDateTime now) {
        String normalized = normalizePreset(StringUtils.defaultIfBlank(preset, defaultPreset));
        if (StringUtils.isBlank(normalized)) {
            throw invalidTime("时间范围不能为空");
        }
        if ("ALL_TIME".equals(normalized)) {
            if (!allowAllTime) {
                throw unsupported("当前接口不支持ALL_TIME");
            }
            return new Resolved(normalized, null, null, zone.getId());
        }
        if (!SUPPORTED_PRESETS.contains(normalized)) {
            throw unsupported("不支持的时间范围: " + normalized);
        }

        LocalDateTime start;
        LocalDateTime end;
        switch (normalized) {
            case "TODAY" -> {
                start = LocalDate.now(zone).atStartOfDay();
                end = start.plusDays(1);
            }
            case "YESTERDAY" -> {
                end = LocalDate.now(zone).atStartOfDay();
                start = end.minusDays(1);
            }
            case "LAST_24_HOURS" -> {
                end = now;
                start = now.minusHours(24);
            }
            case "LAST_7_DAYS" -> {
                end = now;
                start = now.minusDays(7);
            }
            case "LAST_30_DAYS" -> {
                end = now;
                start = now.minusDays(30);
            }
            case "THIS_MONTH" -> {
                start = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay();
                end = start.plusMonths(1);
            }
            case "CUSTOM" -> {
                start = parseTime(startTime, "start_time");
                end = parseTime(endTime, "end_time");
            }
            default -> throw unsupported("不支持的时间范围: " + normalized);
        }
        if (!start.isBefore(end)) {
            throw invalidTime("开始时间必须早于结束时间");
        }
        if (Duration.between(start, end).compareTo(MAX_CUSTOM_RANGE) > 0) {
            throw invalidTime("时间跨度不能超过3年");
        }
        return new Resolved(normalized, start, end, zone.getId());
    }

    public static String validate(String preset,
                                  String startTime,
                                  String endTime,
                                  String defaultPreset,
                                  boolean allowAllTime,
                                  ZoneId zone,
                                  LocalDateTime now) {
        try {
            resolve(preset, startTime, endTime, defaultPreset, allowAllTime, zone, now);
            return "";
        } catch (Violation violation) {
            return violation.getMessage();
        }
    }

    private static LocalDateTime parseTime(String value, String label) {
        String normalized = StringUtils.trimToEmpty(value);
        if (!StringUtils.isNotEmpty(normalized)) {
            throw invalidTime(label + "不能为空");
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME, SECOND_FORMAT, BOUNDARY_FORMAT)) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted representation.
            }
        }
        throw invalidTime(label + "格式必须为ISO本地时间或yyyy-MM-dd HH:mm:ss[.SSS]");
    }

    private static Violation unsupported(String message) {
        return new Violation(false, message);
    }

    private static Violation invalidTime(String message) {
        return new Violation(true, message);
    }

    public record Resolved(String preset,
                           LocalDateTime start,
                           LocalDateTime end,
                           String zoneId) {
    }

    public static final class Violation extends IllegalArgumentException {
        private final boolean invalidTime;

        private Violation(boolean invalidTime, String message) {
            super(message);
            this.invalidTime = invalidTime;
        }

        public boolean invalidTime() {
            return invalidTime;
        }
    }
}
