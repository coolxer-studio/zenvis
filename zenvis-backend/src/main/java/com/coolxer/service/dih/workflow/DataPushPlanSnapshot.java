package com.coolxer.service.dih.workflow;

import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-owned data-push plan snapshot. Card metadata can display these values but
 * must not replace them once a value has been locked by the platform.
 */
public record DataPushPlanSnapshot(
        String planId,
        String sourceMark,
        Map<String, Object> candidate,
        Map<String, Object> metaRef,
        Map<String, Object> validation,
        Map<String, Object> request) {

    public static final String CONTEXT_KEY = "pushPlanSnapshot";

    private static final Set<String> PLAN_TOOLS = Set.of(
            "push_task_create_and_start",
            "push_task_update_and_restart");

    public DataPushPlanSnapshot {
        candidate = copy(candidate);
        metaRef = copy(metaRef);
        validation = copy(validation);
        request = copy(request);
    }

    public static DataPushPlanSnapshot load(Map<String, Object> context) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        Map<String, Object> stored = map(safeContext.get(CONTEXT_KEY));
        if (!stored.isEmpty()) {
            return new DataPushPlanSnapshot(
                    text(stored.get("planId")),
                    text(stored.get("sourceMark")),
                    map(stored.get("candidate")),
                    map(stored.get("metaRef")),
                    map(stored.get("validation")),
                    map(stored.get("request")));
        }
        return new DataPushPlanSnapshot(
                firstText(text(safeContext.get("planId")), "plan:" + UUID.randomUUID()),
                text(safeContext.get("sourceMark")),
                map(safeContext.get("candidate")),
                map(safeContext.get("metaRef")),
                map(safeContext.get("validation")),
                map(safeContext.get("request")));
    }

    public DataPushPlanSnapshot withCandidate(String content,
                                              String digest,
                                              String format,
                                              String artifactId) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(digest)) {
            return this;
        }
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("content", content);
        next.put("digest", digest);
        String normalizedFormat = normalizeFormat(format);
        if (StringUtils.hasText(normalizedFormat)) {
            next.put("format", normalizedFormat);
        }
        if (StringUtils.hasText(artifactId)) {
            next.put("artifactId", artifactId);
        }
        return new DataPushPlanSnapshot(planId, sourceMark, next, metaRef,
                Map.of(), request);
    }

    public DataPushPlanSnapshot withMetaRef(Map<String, Object> nextMetaRef,
                                            String nextSourceMark) {
        Map<String, Object> safeMetaRef = copy(nextMetaRef);
        boolean metaChanged = !Objects.equals(metaRef, safeMetaRef);
        boolean sourceChanged = StringUtils.hasText(nextSourceMark)
                && !nextSourceMark.equals(sourceMark);
        return new DataPushPlanSnapshot(
                planId,
                StringUtils.hasText(nextSourceMark) ? nextSourceMark : sourceMark,
                candidate,
                safeMetaRef,
                metaChanged || sourceChanged ? Map.of() : validation,
                request);
    }

    public DataPushPlanSnapshot withValidation(Map<String, Object> nextValidation) {
        return new DataPushPlanSnapshot(planId, sourceMark, candidate, metaRef,
                nextValidation, request);
    }

    public DataPushPlanSnapshot withRequest(Map<String, Object> nextRequest) {
        return new DataPushPlanSnapshot(planId, sourceMark, candidate, metaRef,
                validation, nextRequest);
    }

    public DataPushPlanSnapshot withoutValidation() {
        return withValidation(Map.of());
    }

    public DataPushPlanSnapshot withoutRequest() {
        return withRequest(Map.of());
    }

    public boolean hasCandidate() {
        return StringUtils.hasText(text(candidate.get("content")))
                && StringUtils.hasText(text(candidate.get("digest")))
                && StringUtils.hasText(text(candidate.get("format")));
    }

    public boolean hasMetaRef() {
        return StringUtils.hasText(text(metaRef.get("fileName")))
                && StringUtils.hasText(text(metaRef.get("contentDigest")));
    }

    public boolean hasRequest() {
        return !request.isEmpty() && StringUtils.hasText(text(request.get("name")));
    }

    public boolean complete() {
        return hasCandidate()
                && hasMetaRef()
                && StringUtils.hasText(sourceMark)
                && hasRequest()
                && Objects.equals(
                text(candidate.get("digest")), digest(text(candidate.get("content"))));
    }

    public boolean validationPassed() {
        return !validation.isEmpty()
                && Boolean.TRUE.equals(validation.get("passed"))
                && !Boolean.TRUE.equals(validation.get("blocked"));
    }

    public boolean receiptMatchesCurrent() {
        if (validation.isEmpty()) {
            return false;
        }
        String candidateDigest = text(candidate.get("digest"));
        String metaDigest = text(metaRef.get("contentDigest"));
        String validationDigest = text(validation.get("validationDigest"));
        String receiptCandidate = text(validation.get("candidateDigest"));
        String receiptMeta = text(validation.get("metaDigest"));
        String receiptFormat = normalizeFormat(text(validation.get("format")));
        String candidateFormat = normalizeFormat(text(candidate.get("format")));
        return StringUtils.hasText(candidateDigest)
                && candidateDigest.equals(receiptCandidate)
                && StringUtils.hasText(metaDigest)
                && metaDigest.equals(receiptMeta)
                && StringUtils.hasText(validationDigest)
                && (!StringUtils.hasText(receiptFormat)
                || !StringUtils.hasText(candidateFormat)
                || receiptFormat.equals(candidateFormat));
    }

    public boolean sealed() {
        return hasCandidate() && validationPassed() && receiptMatchesCurrent();
    }

    public boolean matchesPushArguments(String toolName, String arguments) {
        if (!PLAN_TOOLS.contains(toolName)) {
            return true;
        }
        if (!complete() || !sealed()) {
            return false;
        }
        Map<String, Object> root;
        try {
            root = JacksonUtil.toMap(arguments,
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (RuntimeException ignored) {
            return false;
        }
        if (root == null) {
            return false;
        }
        Map<String, Object> toolRequest = map(root.get("request"));
        String config = text(toolRequest.get("config"));
        String requestCandidateDigest = text(toolRequest.get("candidateDigest"));
        String requestMetaDigest = text(toolRequest.get("metaDigest"));
        String requestValidationDigest = text(toolRequest.get("validationDigest"));
        String requestMark = text(toolRequest.get("mark"));
        String requestMetaFileName = text(toolRequest.get("metaFileName"));
        String requestMetaContent = text(toolRequest.get("metaContent"));
        String candidateDigest = text(candidate.get("digest"));
        String metaDigest = text(metaRef.get("contentDigest"));
        String validationDigest = text(validation.get("validationDigest"));
        return StringUtils.hasText(config)
                && candidateDigest.equals(digest(config))
                && candidateDigest.equals(requestCandidateDigest)
                && metaDigest.equals(requestMetaDigest)
                && validationDigest.equals(requestValidationDigest)
                && sourceMark.equals(requestMark)
                && text(metaRef.get("fileName")).equals(requestMetaFileName)
                && metaDigest.equals(digest(requestMetaContent))
                && "SYSTEM".equalsIgnoreCase(text(toolRequest.get("source")))
                && (!hasRequest()
                || text(request.get("name")).equals(text(toolRequest.get("name"))));
    }

    public void persist(Map<String, Object> context) {
        if (context == null) {
            return;
        }
        context.put(CONTEXT_KEY, toMap());
        putOrRemove(context, "planId", planId);
        putOrRemove(context, "sourceMark", sourceMark);
        putOrRemove(context, "candidate", candidate);
        putOrRemove(context, "metaRef", metaRef);
        putOrRemove(context, "validation", validation);
        putOrRemove(context, "request", request);
        if (validation.isEmpty()) {
            context.remove("validationStatus");
            context.remove("validationDigest");
        } else {
            context.put("validationDigest", text(validation.get("validationDigest")));
            context.put("validationStatus", validationPassed() ? "success"
                    : Boolean.TRUE.equals(validation.get("blocked")) ? "blocked" : "failed");
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("sourceMark", sourceMark);
        result.put("candidate", candidate);
        result.put("metaRef", metaRef);
        result.put("validation", validation);
        result.put("request", request);
        return result;
    }

    public static String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.hasText(content)
                            ? content.getBytes(StandardCharsets.UTF_8)
                            : new byte[0]));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private static void putOrRemove(Map<String, Object> target,
                                    String key,
                                    Object value) {
        if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
            target.remove(key);
        } else if (value instanceof String textValue && !StringUtils.hasText(textValue)) {
            target.remove(key);
        } else if (value == null) {
            target.remove(key);
        } else {
            target.put(key, value);
        }
    }

    private static String normalizeFormat(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        return "yml".equals(normalized) ? "yaml" : normalized;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of() : new LinkedHashMap<>(value);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
