package com.coolxer.utils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 识别 Vector 配置格式。
 */
public final class ConfigFormatDetector {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("yaml", "toml", "json");
    private static final Pattern TOML_SECTION = Pattern.compile("^\\[\\[?.+?\\]\\]?\\s*(?:#.*)?$");
    private static final Pattern TOML_ASSIGNMENT = Pattern.compile(
            "^(?:\"(?:[^\"\\\\]|\\\\.)+\"|'[^']+'|[A-Za-z0-9_.-]+(?:\\.[A-Za-z0-9_.-]+)*)\\s*=");
    private static final Pattern YAML_KEY = Pattern.compile(
            "^(?:\"(?:[^\"\\\\]|\\\\.)+\"|'[^']+'|[A-Za-z0-9_.-]+)\\s*:(?:\\s|$)");

    private ConfigFormatDetector() {
    }

    /**
     * 规范化显式格式；空值返回 null，非法格式也返回 null。
     *
     * @param requestedFormat 请求格式
     * @return yaml、toml、json 或 null
     */
    public static String normalizeExplicit(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            return null;
        }
        String normalized = requestedFormat.trim().toLowerCase(Locale.ROOT);
        normalized = "yml".equals(normalized) ? "yaml" : normalized;
        return supported(normalized) ? normalized : null;
    }

    /**
     * 解析任务配置格式。显式格式非法时不猜测；缺省时仅接受可可靠识别的配置。
     *
     * @param requestedFormat 显式格式，可为空
     * @param config 配置正文
     * @return 规范化格式
     */
    public static String resolve(String requestedFormat, String config) {
        if (requestedFormat != null && !requestedFormat.isBlank()) {
            String normalized = normalizeExplicit(requestedFormat);
            if (normalized == null) {
                throw new IllegalArgumentException(
                        "不支持的配置格式：" + requestedFormat + "；仅支持 yaml、toml 或 json");
            }
            return normalized;
        }
        String detected = detect(null, config);
        if (!supported(detected)) {
            throw new IllegalArgumentException(
                    "无法可靠识别配置格式，请显式传入 format=yaml、toml 或 json");
        }
        return detected;
    }

    public static String detect(String requestedFormat, String config) {
        if (requestedFormat != null && !requestedFormat.isBlank()) {
            String normalized = normalizeExplicit(requestedFormat);
            return normalized == null ? "unknown" : normalized;
        }
        String firstLine = firstStructuralLine(config);
        if (firstLine.startsWith("{")) {
            return "json";
        }
        if (TOML_SECTION.matcher(firstLine).matches()) {
            return "toml";
        }
        if (YAML_KEY.matcher(firstLine).find()) {
            return "yaml";
        }
        if (TOML_ASSIGNMENT.matcher(firstLine).find()) {
            return "toml";
        }
        return "unknown";
    }

    public static boolean supported(String format) {
        return SUPPORTED_FORMATS.contains(format);
    }

    private static String firstStructuralLine(String config) {
        if (config == null || config.isBlank()) {
            return "";
        }
        for (String rawLine : config.split("\\R")) {
            String line = rawLine.replaceFirst("^\\uFEFF", "").trim();
            if (line.isEmpty()
                    || line.startsWith("#")
                    || line.startsWith("%YAML")
                    || line.startsWith("---")
                    || line.equals("...")) {
                continue;
            }
            return line;
        }
        return "";
    }
}
