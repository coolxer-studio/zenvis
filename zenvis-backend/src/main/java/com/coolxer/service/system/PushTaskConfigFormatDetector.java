package com.coolxer.service.system;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 识别 Vector 配置格式。
 * <p>格式判断只看首个有语义的配置行，注释和 VRL 中的赋值不能影响格式识别。</p>
 */
public final class PushTaskConfigFormatDetector {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("yaml", "toml", "json");
    private static final Pattern TOML_SECTION = Pattern.compile("^\\[\\[?.+?\\]\\]?\\s*(?:#.*)?$");
    private static final Pattern TOML_ASSIGNMENT = Pattern.compile(
            "^(?:\"(?:[^\"\\\\]|\\\\.)+\"|'[^']+'|[A-Za-z0-9_.-]+(?:\\.[A-Za-z0-9_.-]+)*)\\s*=");
    private static final Pattern YAML_KEY = Pattern.compile(
            "^(?:\"(?:[^\"\\\\]|\\\\.)+\"|'[^']+'|[A-Za-z0-9_.-]+)\\s*:(?:\\s|$)");

    private PushTaskConfigFormatDetector() {
    }

    /**
     * 检测配置格式。
     *
     * @param requestedFormat 显式格式，可为空
     * @param config 配置正文
     * @return 检测结果
     */
    public static Detection detect(String requestedFormat, String config) {
        if (requestedFormat != null && !requestedFormat.isBlank()) {
            String normalized = requestedFormat.trim().toLowerCase(Locale.ROOT);
            normalized = "yml".equals(normalized) ? "yaml" : normalized;
            return SUPPORTED_FORMATS.contains(normalized)
                    ? new Detection(normalized, "")
                    : new Detection("", "UNSUPPORTED");
        }

        String firstLine = firstStructuralLine(config);
        if (firstLine.isEmpty()) {
            return new Detection("", "AMBIGUOUS");
        }
        if (firstLine.startsWith("{")) {
            return new Detection("json", "");
        }
        if (TOML_SECTION.matcher(firstLine).matches()) {
            return new Detection("toml", "");
        }
        if (YAML_KEY.matcher(firstLine).find()) {
            return new Detection("yaml", "");
        }
        if (TOML_ASSIGNMENT.matcher(firstLine).find()) {
            return new Detection("toml", "");
        }
        return new Detection("", "AMBIGUOUS");
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

    /**
     * 格式检测结果。
     *
     * @param format 规范化格式，无法识别时为空
     * @param problem UNSUPPORTED 或 AMBIGUOUS，正常时为空
     */
    public record Detection(String format, String problem) {
        public boolean ambiguous() {
            return "AMBIGUOUS".equals(problem);
        }

        public boolean unsupported() {
            return "UNSUPPORTED".equals(problem);
        }
    }
}
