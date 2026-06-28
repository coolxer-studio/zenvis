package com.coolxer.service.dih.logging;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大模型请求/响应日志工具。
 */
public final class LlmLogHelper {

    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    private LlmLogHelper() {
    }

    public static String newRequestId() {
        return "llm-" + REQUEST_SEQUENCE.incrementAndGet();
    }

    public static void logRequest(Logger logger, String requestId, String scene, Object request) {
        logger.info("[LLM][{}][{}] request={}", requestId, scene, toLogText(request));
    }

    public static void logResponse(Logger logger, String requestId, String scene, Object response, long startedAtNanos) {
        logger.info("[LLM][{}][{}] response elapsedMs={} result={}",
                requestId, scene, elapsedMs(startedAtNanos), toLogText(response));
    }

    public static void logError(
            Logger logger,
            String requestId,
            String scene,
            Object partialResponse,
            long startedAtNanos,
            Throwable throwable
    ) {
        logger.error("[LLM][{}][{}] error elapsedMs={} partialResponse={} message={}",
                requestId,
                scene,
                elapsedMs(startedAtNanos),
                toLogText(partialResponse),
                throwable.getMessage(),
                throwable);
    }

    public static Flux<String> logStringStream(
            Logger logger,
            String requestId,
            String scene,
            Flux<String> source,
            long startedAtNanos
    ) {
        StringBuilder responseBuffer = new StringBuilder();
        return source
                .doOnNext(chunk -> append(responseBuffer, chunk))
                .doOnError(error -> logError(logger, requestId, scene, responseBuffer.toString(), startedAtNanos, error))
                .doOnComplete(() -> logResponse(logger, requestId, scene, responseBuffer.toString(), startedAtNanos));
    }

    public static Flux<ChatResponse> logChatResponseStream(
            Logger logger,
            String requestId,
            String scene,
            Flux<ChatResponse> source,
            long startedAtNanos
    ) {
        StringBuilder responseBuffer = new StringBuilder();
        return source
                .doOnNext(response -> append(responseBuffer, extractContent(response)))
                .doOnError(error -> logError(logger, requestId, scene, responseBuffer.toString(), startedAtNanos, error))
                .doOnComplete(() -> logResponse(logger, requestId, scene, responseBuffer.toString(), startedAtNanos));
    }

    public static String extractContent(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "";
        }
        return response.getResults().stream()
                .filter(generation -> generation.getOutput() != null && generation.getOutput().getText() != null)
                .map(generation -> generation.getOutput().getText())
                .reduce("", String::concat);
    }

    private static void append(StringBuilder buffer, String chunk) {
        if (chunk != null && !chunk.isEmpty()) {
            buffer.append(chunk);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static String toLogText(Object value) {
        Object sanitized = sanitize(value, "");
        if (sanitized == null) {
            return "null";
        }
        if (sanitized instanceof String stringValue) {
            return stringValue;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            return String.valueOf(sanitized);
        }
    }

    private static Object sanitize(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return "******";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> {
                String stringKey = String.valueOf(entryKey);
                sanitized.put(stringKey, sanitize(entryValue, stringKey));
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(item -> sanitized.add(sanitize(item, key)));
            return sanitized;
        }
        if (value instanceof String stringValue) {
            return sanitizeString(stringValue, key);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase();
        return normalizedKey.contains("authorization")
                || normalizedKey.contains("api_key")
                || normalizedKey.contains("apikey")
                || normalizedKey.contains("api-key")
                || normalizedKey.contains("token")
                || normalizedKey.contains("secret")
                || normalizedKey.contains("password");
    }

    private static String sanitizeString(String value, String key) {
        if ("url".equalsIgnoreCase(key) && value.startsWith("data:image/")) {
            return "<image-data-uri length=" + value.length() + ">";
        }
        return value;
    }
}
