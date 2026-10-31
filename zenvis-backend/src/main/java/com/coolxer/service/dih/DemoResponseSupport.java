package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.dih.Message;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskSummaryVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

final class DemoResponseSupport {

    private static final String VECTUM_RUN_LOG_MARKER_PREFIX = "=== Vectum run ";
    private static final int DEMO_STREAM_CHUNK_SIZE = 18;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(55);

    private DemoResponseSupport() {
    }

    static String loadResource(String resourceRoot, String fileName) {
        String path = resourceRoot + fileName;
        try (InputStream stream = DemoResponseSupport.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少智能体演示资源: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取智能体演示资源失败: " + path, e);
        }
    }

    static boolean sessionContains(ChatSession chatSession, String... markers) {
        if (chatSession == null || !StringUtils.hasText(chatSession.getMessages())) {
            return false;
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    chatSession.getMessages(),
                    new TypeReference<List<Message>>() {
                    });
            return messages.stream()
                    .map(Message::getContent)
                    .filter(StringUtils::hasText)
                    .anyMatch(content -> Arrays.stream(markers)
                            .filter(StringUtils::hasText)
                            .anyMatch(content::contains));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Flux<String> streamAction(Callable<String> action) {
        return Mono.fromCallable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(DemoResponseSupport::streamResponse);
    }

    static Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int nextLineBreak = response.indexOf('\n', index);
            int limit = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            int end = nextLineBreak >= index && nextLineBreak < limit
                    ? nextLineBreak + 1 : limit;
            chunks.add(response.substring(index, end));
            index = end;
        }
        return Flux.fromIterable(chunks).delayElements(DEMO_STREAM_DELAY);
    }

    static String callTool(McpToolContext mcpToolContext,
                           String toolName,
                           Map<String, Object> arguments) {
        if (mcpToolContext == null
                || mcpToolContext.toolCallbackProvider() == null
                || mcpToolContext.toolCallbackProvider().getToolCallbacks() == null) {
            throw new IllegalStateException("演示所需 MCP 工具上下文不可用");
        }
        ToolCallback callback = Arrays.stream(
                        mcpToolContext.toolCallbackProvider().getToolCallbacks())
                .filter(tool -> tool != null
                        && tool.getToolDefinition() != null
                        && toolName.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "演示所需 MCP 工具不可用：" + toolName));

        Map<String, Object> context = new LinkedHashMap<>();
        McpInvocationContext invocationContext = mcpToolContext.invocationContext();
        if (invocationContext != null) {
            context.put(McpInvocationContext.TOOL_CONTEXT_KEY, invocationContext);
        }
        ToolRuntimeContext runtimeContext = mcpToolContext.toolRuntimeContext();
        if (runtimeContext != null) {
            context.put(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext);
        }
        return callback.call(JacksonUtil.toJson(arguments), new ToolContext(context));
    }

    static void requireTrueResult(String toolName, String result) throws Exception {
        JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
        if (node != null && (node.isBoolean() && node.booleanValue()
                || node.isTextual() && Boolean.parseBoolean(node.textValue()))) {
            return;
        }
        throw new IllegalStateException(
                toolName + " 未成功：" + describeToolResult(result));
    }

    static String decodeStringResult(String result) throws Exception {
        JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
        return node != null && node.isTextual() ? node.textValue() : result;
    }

    static List<ConfigVo> readConfigTree(String result) throws Exception {
        return JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                new TypeReference<List<ConfigVo>>() {
                });
    }

    static boolean containsConfigFile(String treeResult, String fileName) throws Exception {
        JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(treeResult);
        return containsConfigFile(root, fileName);
    }

    private static boolean containsConfigFile(JsonNode node, String fileName) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsConfigFile(child, fileName)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        String candidate = firstNonBlank(
                node.path("fileName").asText(""),
                node.path("file_name").asText(""),
                node.path("name").asText(""));
        if (fileName.equals(candidate)) {
            return true;
        }
        return containsConfigFile(node.get("nodes"), fileName)
                || containsConfigFile(node.get("children"), fileName);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    static List<PushTaskSummaryVo> listPushTasks(McpToolContext context,
                                                 String sourceMark) throws Exception {
        String result = callTool(
                context,
                "push_task_list",
                Map.of(
                        "sourceMark", sourceMark,
                        "source", "SYSTEM",
                        "offset", 0,
                        "limit", 100));
        PushTaskPageVo page = JacksonConfig.MCP_TOOL_RESULT_MAPPER.readValue(
                result, PushTaskPageVo.class);
        if (page.isHasMore()) {
            throw new IllegalStateException(
                    "sourceMark=" + sourceMark + " 的任务超过单页上限，拒绝自动选择");
        }
        return page.getItems() == null ? List.of() : page.getItems();
    }

    static PushTaskVo getPushTask(McpToolContext context, Integer taskId) throws Exception {
        String result = callTool(context, "push_task_get", Map.of("taskId", taskId));
        return JacksonConfig.MCP_TOOL_RESULT_MAPPER.readValue(result, PushTaskVo.class);
    }

    static Map<String, Object> validatePushConfig(McpToolContext context,
                                                  String config,
                                                  String format,
                                                  String metaFileName) throws Exception {
        String result = callTool(
                context,
                "push_task_validate_config",
                Map.of(
                        "config", config,
                        "format", format,
                        "metaFileName", firstNonBlank(metaFileName)));
        return JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                new TypeReference<Map<String, Object>>() {
                });
    }

    static Map<String, Object> readSystemLog(McpToolContext context,
                                             PushTaskVo task) throws Exception {
        String result = callTool(
                context,
                "push_task_get_log",
                Map.of(
                        "taskId", task.getId(),
                        "logType", "system",
                        "tailLines", 100));
        return JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                new TypeReference<Map<String, Object>>() {
                });
    }

    static String describeToolResult(String result) {
        if (!StringUtils.hasText(result)) {
            return "工具未返回结果";
        }
        try {
            JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
            if (node != null && node.isObject()) {
                String status = node.path("status").asText("");
                String message = node.path("message").asText("");
                if (StringUtils.hasText(status) || StringUtils.hasText(message)) {
                    return "status=" + status
                            + (StringUtils.hasText(message) ? "，" + message : "");
                }
            }
        } catch (Exception ignored) {
            // Use the bounded plain text below.
        }
        return result.length() <= 500 ? result : result.substring(0, 500) + "...";
    }

    static boolean containsRuntimeError(String logContent) {
        if (!StringUtils.hasText(logContent)) {
            return false;
        }
        int markerIndex = logContent.lastIndexOf(VECTUM_RUN_LOG_MARKER_PREFIX);
        String currentRunLog = markerIndex >= 0
                ? logContent.substring(markerIndex + VECTUM_RUN_LOG_MARKER_PREFIX.length())
                : logContent;
        String normalized = currentRunLog.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("error")
                || normalized.contains("failed")
                || normalized.contains("fatal")
                || normalized.contains("panic");
    }

    static String sanitizeSystemLog(String logContent) {
        String sanitized = StringUtils.trimWhitespace(
                StringUtils.hasText(logContent) ? logContent : "空日志");
        sanitized = sanitized.replaceAll("(?i)\\bBearer\\s+[^\\s,;]+", "Bearer ***");
        sanitized = sanitized.replaceAll(
                "(?i)([\"']?(?:password|passwd|token|secret|api[_-]?key|access[_-]?key|authorization)"
                        + "[\"']?\\s*[:=]\\s*[\"']?)([^\\s,;\"']+)",
                "$1***");
        return sanitized.length() > 2_000
                ? sanitized.substring(sanitized.length() - 2_000) : sanitized;
    }

    static String safeError(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return StringUtils.hasText(message) ? sanitizeSystemLog(message) : "未知错误";
    }

    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    static String digest(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    static Optional<ConfigVo> findConfigFile(List<ConfigVo> nodes, String fileName) {
        if (nodes == null) {
            return Optional.empty();
        }
        for (ConfigVo node : nodes) {
            if (node == null) {
                continue;
            }
            if (fileName.equals(node.getFileName())) {
                return Optional.of(node);
            }
            Optional<ConfigVo> nested = findConfigFile(node.getNodes(), fileName);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }
}
