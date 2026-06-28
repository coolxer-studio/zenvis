package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.ChatMessagePart;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatMessagePartParser {

    private static final Pattern FENCE_PATTERN = Pattern.compile("```([^\\r\\n]*)\\R([\\s\\S]*?)\\R?```");
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>([\\s\\S]*?)</think>", Pattern.CASE_INSENSITIVE);

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
        if (!"zenvis:notice".equals(info) && !"zenvis:confirm".equals(info)) {
            return null;
        }

        try {
            JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(body);
            Map<String, Object> metadata = JacksonConfig.OBJECT_MAPPER.convertValue(
                    node,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            String type = "zenvis:notice".equals(info) ? "notice" : "confirm";
            ChatMessagePart.ChatMessagePartBuilder builder = part(type)
                    .title(textValue(node, "title"))
                    .content(firstTextValue(node, "content", "message", "description"))
                    .level(firstTextValue(node, "level", "type"))
                    .metadata(metadata);
            if ("confirm".equals(type)) {
                builder.status("pending");
            }
            return builder.build();
        } catch (Exception e) {
            return null;
        }
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

    private ChatMessagePart.ChatMessagePartBuilder part(String type) {
        return ChatMessagePart.builder()
                .id(UUID.randomUUID().toString())
                .type(type);
    }
}
