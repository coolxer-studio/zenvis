package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.ChatMessagePart;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatMessagePartParser {

    private static final Pattern FENCE_PATTERN = Pattern.compile("```([^\\r\\n]*)\\R([\\s\\S]*?)\\R?```");
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>([\\s\\S]*?)</think>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern HTML_HEADING_PATTERN = Pattern.compile("<h([1-6])[^>]*>([\\s\\S]*?)</h\\1>", Pattern.CASE_INSENSITIVE);

    public List<ChatMessagePart> parse(String content, MessageType messageType) {
        if (messageType == MessageType.CHART) {
            return List.of(part("chart")
                    .content(content)
                    .build());
        }

        List<ChatMessagePart> parts = parseThinkingAndContent(content == null ? "" : content);
        if (parts.isEmpty()) {
            parts.add(part("markdown").content(content == null ? "" : content).build());
        }
        return parts;
    }

    private List<ChatMessagePart> parseThinkingAndContent(String content) {
        List<ChatMessagePart> parts = new ArrayList<>();
        Matcher matcher = THINK_PATTERN.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            parts.addAll(parseMarkdownAndFences(content.substring(lastEnd, matcher.start())));
            String thinkingContent = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (StringUtils.hasText(thinkingContent)) {
                parts.add(part("thinking")
                        .title("思考过程")
                        .content(thinkingContent)
                        .status("completed")
                        .build());
            }
            lastEnd = matcher.end();
        }

        parts.addAll(parseMarkdownAndFences(content.substring(lastEnd)));
        return parts;
    }

    private List<ChatMessagePart> parseMarkdownAndFences(String content) {
        List<ChatMessagePart> parts = new ArrayList<>();
        Matcher matcher = FENCE_PATTERN.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            addMarkdownPart(parts, content.substring(lastEnd, matcher.start()));

            String info = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String body = matcher.group(2) == null ? "" : matcher.group(2);
            ChatMessagePart specialPart = parseSpecialFence(info, body);
            if (specialPart != null) {
                parts.add(specialPart);
            } else if (info.startsWith("zenvis:")) {
                parts.add(part("markdown").content(matcher.group(0)).build());
            } else {
                parts.add(part("code")
                        .language(StringUtils.hasText(info) ? info : "plaintext")
                        .content(body)
                        .build());
            }

            lastEnd = matcher.end();
        }

        addMarkdownPart(parts, content.substring(lastEnd));
        return parts;
    }

    private ChatMessagePart parseSpecialFence(String info, String body) {
        ChatMessagePart configPart = parseConfigFence(info, body);
        if (configPart != null) {
            return configPart;
        }

        if (!"zenvis:notice".equals(info)
                && !"zenvis:confirm".equals(info)
                && !"zenvis:info-steps".equals(info)
                && !"zenvis:analysis-decision".equals(info)
                && !"zenvis:analysis-record".equals(info)
                && !"zenvis:data-access-decision".equals(info)
                && !"zenvis:meta-config-record".equals(info)
                && !"zenvis:vectum-task-record".equals(info)
                && !"zenvis:visualization-chart-preview".equals(info)
                && !"zenvis:visualization-chart-record".equals(info)
                && !"zenvis:visualization-config-record".equals(info)
                && !"zenvis:dashboard-config-record".equals(info)
                && !"zenvis:menu-config-record".equals(info)
                && !"zenvis:policy-record".equals(info)) {
            return null;
        }

        try {
            JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(body);
            Map<String, Object> metadata = JacksonConfig.OBJECT_MAPPER.convertValue(
                    node,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            String type = switch (info) {
                case "zenvis:notice" -> "notice";
                case "zenvis:info-steps" -> "info-steps";
                case "zenvis:analysis-decision" -> "analysis-decision";
                case "zenvis:analysis-record" -> "analysis-record";
                case "zenvis:data-access-decision" -> "data-access-decision";
                case "zenvis:meta-config-record" -> "metadata-config-record";
                case "zenvis:vectum-task-record" -> "data-push-service-record";
                case "zenvis:visualization-chart-preview" -> "visualization-chart-preview";
                case "zenvis:visualization-chart-record" -> "visualization-chart-record";
                case "zenvis:visualization-config-record" -> "visualization-config-record";
                case "zenvis:dashboard-config-record" -> "dashboard-config-record";
                case "zenvis:menu-config-record" -> "menu-config-record";
                case "zenvis:policy-record" -> "policy-record";
                default -> "confirm";
            };
            ChatMessagePart.ChatMessagePartBuilder builder = part(type)
                    .title(textValue(node, "title"))
                    .content(firstTextValue(node, "content", "message", "description", "changeDescription", "change_description",
                            "name", "entityLabel", "fileName", "taskId",
                            "configIndex", "dashboardId", "dashboardCode", "menuId"))
                    .level(firstTextValue(node, "level", "type"))
                    .metadata(metadata);
            if ("confirm".equals(type)
                    || "info-steps".equals(type)
                    || "analysis-decision".equals(type)
                    || "data-access-decision".equals(type)) {
                builder.status("pending");
            }
            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    private ChatMessagePart parseConfigFence(String info, String body) {
        return switch (info) {
            case "zenvis:low-code-page-config" -> configPart(
                    body,
                    "低代码页面配置",
                    "json",
                    "low-code-page",
                    "<configIndex>_config/index.json",
                    info
            );
            case "zenvis:low-code-app-config" -> configPart(
                    body,
                    "低代码应用配置",
                    "json",
                    "low-code-app",
                    "<configIndex>_config/site.json",
                    info
            );
            case "zenvis:html-page-config" -> configPart(
                    body,
                    "静态 HTML 页面配置",
                    "html",
                    "html-page",
                    "html-page_config/<slug>.html",
                    info
            );
            case "zenvis:continuous-analysis-task-config" -> configPart(
                    body,
                    "持续分析任务配置",
                    "json",
                    "continuous-analysis-task",
                    "continuous-analysis-task.json",
                    info
            );
            case "zenvis:meta-config" -> configPart(
                    body,
                    "元数据配置",
                    "json",
                    "meta-config",
                    "meta_config/<entity>.json",
                    info
            );
            case "zenvis:disposal-strategy-config" -> configPart(
                    body,
                    "处置策略配置",
                    "json",
                    "disposal-strategy",
                    "analysis-disposal-strategy.json",
                    info
            );
            case "zenvis:collection-policy-config" -> configPart(
                    body,
                    "采集策略配置",
                    "json",
                    "collection-policy",
                    "checker_config/{host|android|ios|h5|wechat}.json",
                    info
            );
            case "zenvis:tagging-policy-config" -> configPart(
                    body,
                    "标记评分策略配置",
                    "json",
                    "tagging-policy",
                    "rating_config/rating_rule.json",
                    info
            );
            case "zenvis:disposal-policy-config" -> configPart(
                    body,
                    "处置策略配置",
                    "json",
                    "disposal-policy",
                    "punish_config/<stable-name>.json",
                    info
            );
            case "zenvis:report-document-config" -> reportDocumentPart(body, info);
            default -> null;
        };
    }

    private ChatMessagePart reportDocumentPart(String body, String fence) {
        String format = detectReportLanguage(body);
        String title = firstNonBlank(extractReportTitle(body, format), "报表文档");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("configKind", "report-document");
        metadata.put("defaultFileName", "html".equals(format) ? "report.html" : "report.md");
        metadata.put("fence", fence);
        metadata.put("title", title);
        metadata.put("format", format);
        metadata.put("version", "v1.0.0");
        metadata.put("updatedAt", java.time.OffsetDateTime.now().toString());
        metadata.put("outline", extractReportOutline(body, format));
        return part("report-document")
                .title(title)
                .language(format)
                .content(body)
                .metadata(metadata)
                .build();
    }

    private String detectReportLanguage(String body) {
        String trimmed = body == null ? "" : body.stripLeading().toLowerCase();
        if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")) {
            return "html";
        }
        return "markdown";
    }

    private String extractReportTitle(String body, String format) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        Matcher matcher = "html".equals(format)
                ? HTML_HEADING_PATTERN.matcher(body)
                : MARKDOWN_HEADING_PATTERN.matcher(body);
        if (matcher.find()) {
            String title = "html".equals(format) ? stripHtml(matcher.group(2)) : matcher.group(2);
            return StringUtils.hasText(title) ? title.trim() : null;
        }
        return null;
    }

    private List<Map<String, Object>> extractReportOutline(String body, String format) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        List<Map<String, Object>> outline = new ArrayList<>();
        Matcher matcher = "html".equals(format)
                ? HTML_HEADING_PATTERN.matcher(body)
                : MARKDOWN_HEADING_PATTERN.matcher(body);
        while (matcher.find()) {
            int level;
            String text;
            if ("html".equals(format)) {
                level = Integer.parseInt(matcher.group(1));
                text = stripHtml(matcher.group(2));
            } else {
                level = matcher.group(1).length();
                text = matcher.group(2);
            }
            if (StringUtils.hasText(text)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "heading-" + (outline.size() + 1));
                item.put("level", level);
                item.put("text", text.trim());
                outline.add(item);
            }
        }
        return outline;
    }

    private String stripHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    private ChatMessagePart configPart(String body,
                                       String title,
                                       String language,
                                       String configKind,
                                       String defaultFileName,
                                       String fence) {
        return part("config")
                .title(title)
                .language(language)
                .content(body)
                .metadata(Map.of(
                        "configKind", configKind,
                        "defaultFileName", defaultFileName,
                        "fence", fence
                ))
                .build();
    }

    private void addMarkdownPart(List<ChatMessagePart> parts, String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return;
        }
        parts.add(part("markdown").content(markdown).build());
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstTextValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = textValue(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private ChatMessagePart.ChatMessagePartBuilder part(String type) {
        return ChatMessagePart.builder()
                .id(UUID.randomUUID().toString())
                .type(type);
    }
}
