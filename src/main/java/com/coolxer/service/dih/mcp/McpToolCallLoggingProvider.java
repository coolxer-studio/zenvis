package com.coolxer.service.dih.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Wraps MCP tool callbacks so every actual tool invocation can be traced in chat and server logs.
 */
@Slf4j
public class McpToolCallLoggingProvider implements ToolCallbackProvider {

    private static final int MAX_ARGUMENT_CHARS = 500;
    private static final int MAX_RESULT_CHARS = 500;

    private final ToolCallbackProvider delegate;
    private final Consumer<McpToolCallLog> logConsumer;

    public McpToolCallLoggingProvider(ToolCallbackProvider delegate, Consumer<McpToolCallLog> logConsumer) {
        this.delegate = delegate;
        this.logConsumer = logConsumer;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (delegate == null || delegate.getToolCallbacks() == null) {
            return new ToolCallback[0];
        }
        return Arrays.stream(delegate.getToolCallbacks())
                .map(this::wrap)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback wrap(ToolCallback callback) {
        return new LoggingToolCallback(callback, logConsumer);
    }

    private static class LoggingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final Consumer<McpToolCallLog> logConsumer;

        private LoggingToolCallback(ToolCallback delegate, Consumer<McpToolCallLog> logConsumer) {
            this.delegate = delegate;
            this.logConsumer = logConsumer;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return callInternal(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return callInternal(toolInput, toolContext);
        }

        private String callInternal(String toolInput, ToolContext toolContext) {
            String toolName = toolName();
            ToolRuntimeContext runtimeContext = resolveRuntimeContext(toolContext);
            if (runtimeContext != null && runtimeContext.stopRequested()) {
                throw new IllegalStateException(
                        "工具执行已停止：" + runtimeContext.stopReason());
            }
            long startedAt = System.nanoTime();
            emit(McpToolCallLog.started(toolName, summarize(toolInput, MAX_ARGUMENT_CHARS)));
            log.info("MCP工具调用开始: tool={}, arguments={}", toolName, summarize(toolInput, MAX_ARGUMENT_CHARS));
            try {
                String result = toolContext == null
                        ? delegate.call(toolInput)
                        : delegate.call(toolInput, toolContext);
                long durationMillis = elapsedMillis(startedAt);
                emit(McpToolCallLog.succeeded(toolName, durationMillis, summarize(result, MAX_RESULT_CHARS)));
                log.info("MCP工具调用成功: tool={}, durationMs={}, result={}",
                        toolName, durationMillis, summarize(result, MAX_RESULT_CHARS));
                return result;
            } catch (RuntimeException e) {
                long durationMillis = elapsedMillis(startedAt);
                if (runtimeContext != null) {
                    runtimeContext.registerFailure(
                            executionFailureSignature(toolName, toolInput, e));
                }
                emit(McpToolCallLog.failed(toolName, durationMillis, summarize(e.getMessage(), MAX_RESULT_CHARS)));
                log.warn("MCP工具调用失败: tool={}, durationMs={}, error={}",
                        toolName, durationMillis, e.getMessage(), e);
                throw e;
            }
        }

        private ToolRuntimeContext resolveRuntimeContext(ToolContext toolContext) {
            if (toolContext == null || toolContext.getContext() == null) {
                return null;
            }
            Object configured = toolContext.getContext().get(
                    ToolRuntimeContext.TOOL_CONTEXT_KEY);
            return configured instanceof ToolRuntimeContext runtimeContext
                    ? runtimeContext
                    : null;
        }

        private String executionFailureSignature(
                String toolName,
                String toolInput,
                RuntimeException exception) {
            Throwable root = exception;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String canonical = toolName
                    + "\n" + normalizeSignatureText(toolInput, false)
                    + "\n" + root.getClass().getName()
                    + "\n" + normalizeSignatureText(root.getMessage(), true);
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8));
                return "tool_execution:" + HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
            }
        }

        private String normalizeSignatureText(String value, boolean replaceNumbers) {
            String normalized = StringUtils.hasText(value)
                    ? value.replaceAll("\\s+", "")
                    : "";
            return replaceNumbers
                    ? normalized.replaceAll("\\d+", "#")
                    : normalized;
        }

        private void emit(McpToolCallLog event) {
            if (logConsumer != null) {
                logConsumer.accept(event);
            }
        }

        private String toolName() {
            ToolDefinition definition = delegate.getToolDefinition();
            if (definition == null || !StringUtils.hasText(definition.name())) {
                return "unknown";
            }
            return definition.name();
        }

        private static long elapsedMillis(long startedAt) {
            return (System.nanoTime() - startedAt) / 1_000_000;
        }

        private static String summarize(String value, int maxChars) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.length() <= maxChars) {
                return normalized;
            }
            return normalized.substring(0, maxChars) + "...";
        }
    }

    public record McpToolCallLog(String status,
                                 String toolName,
                                 String arguments,
                                 String result,
                                 String error,
                                 Long durationMillis,
                                 Instant time) {

        public static McpToolCallLog started(String toolName, String arguments) {
            return new McpToolCallLog("started", toolName, arguments, null, null, null, Instant.now());
        }

        public static McpToolCallLog succeeded(String toolName, long durationMillis, String result) {
            return new McpToolCallLog("succeeded", toolName, null, result, null, durationMillis, Instant.now());
        }

        public static McpToolCallLog failed(String toolName, long durationMillis, String error) {
            return new McpToolCallLog("failed", toolName, null, null, error, durationMillis, Instant.now());
        }
    }
}
