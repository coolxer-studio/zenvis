package com.coolxer.service.dih.workflow;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkflowEvidenceService {

    private final McpToolInvocationRepository invocationRepository;

    @Autowired(required = false)
    private WorkflowMetrics workflowMetrics;

    public WorkflowEvidenceService(McpToolInvocationRepository invocationRepository) {
        this.invocationRepository = invocationRepository;
    }

    public List<Map<String, Object>> succeededForTurn(
            String turnId,
            String chatId,
            Integer userId) {
        if (!StringUtils.hasText(turnId)) {
            return List.of();
        }
        List<McpToolInvocation> invocations = invocationRepository.findByTurnIdAndStatusIn(
                turnId, List.of(McpInvocationStatus.SUCCEEDED));
        if (invocations == null || invocations.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> evidence = new ArrayList<>();
        invocations.stream()
                .filter(invocation -> !StringUtils.hasText(chatId)
                        || Objects.equals(chatId, invocation.getChatId()))
                .filter(invocation -> userId == null
                        || Objects.equals(userId, invocation.getRequesterUserId()))
                .forEach(invocation -> evidence.add(toEvidence(invocation)));
        return evidence;
    }

    public Map<String, Object> toEvidence(McpToolInvocation invocation) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceId", invocation.getRequestId());
        evidence.put("tool", invocation.getToolName());
        evidence.put("status", invocation.getStatus().name().toLowerCase());
        evidence.put("argumentsDigest", firstText(
                invocation.getArgumentsDigest(), digest(invocation.getArguments())));
        evidence.put("argumentsSummary", summarize(invocation.getArguments()));
        evidence.put("resultDigest", digest(invocation.getResult()));
        evidence.put("resultSummary", summarize(invocation.getResult()));
        evidence.put("resultLength", invocation.getResultLength());
        evidence.put("durationMillis", invocation.getDurationMillis());
        evidence.put("turnId", invocation.getTurnId());
        evidence.put("recordedAt", invocation.getFinishTime() == null
                ? Instant.now().toString()
                : invocation.getFinishTime().toInstant().toString());
        if ("push_task_validate_config".equals(invocation.getToolName())) {
            Map<String, Object> validation = validationPayload(invocation.getResult());
            if (!validation.isEmpty()) {
                evidence.put("validation", validation);
            }
        }
        if (workflowMetrics != null) {
            workflowMetrics.mcpEvidence(
                    invocation.getToolName(), invocation.getStatus().name().toLowerCase());
        }
        return evidence;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String digest(String value) {
        if (value == null) {
            return "";
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value
                .replaceAll(
                        "(?i)(\"?(?:password|passwd|token|secret|authorization|api[_-]?key)\"?\\s*[:=]\\s*)\"[^\"]*\"",
                        "$1\"***\"")
                .replaceAll(
                        "(?i)(\\b(?:password|passwd|token|secret|authorization|api[_-]?key)\\b\\s*[:=]\\s*)[^,}\\s]+",
                        "$1***")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 1000
                ? normalized : normalized.substring(0, 1000) + "…";
    }

    private Map<String, Object> validationPayload(String result) {
        if (!StringUtils.hasText(result)) {
            return Map.of();
        }
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(result);
            if (root == null || !root.isObject()) {
                return Map.of();
            }
            JsonNode data = root.get("data");
            JsonNode payload = data != null && data.isObject() ? data : root;
            Map<String, Object> validation = JacksonConfig.OBJECT_MAPPER.convertValue(
                    payload, new TypeReference<Map<String, Object>>() {
                    });
            normalizeValidationKeys(validation);
            if (validation == null || validation.isEmpty()
                    || !StringUtils.hasText(Objects.toString(
                    validation.get("candidateDigest"), ""))
                    || !StringUtils.hasText(Objects.toString(
                    validation.get("metaDigest"), ""))
                    || !StringUtils.hasText(Objects.toString(
                    validation.get("validationDigest"), ""))) {
                return Map.of();
            }
            validation.remove("normalizedConfig");
            return validation;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void normalizeValidationKeys(Map<String, Object> validation) {
        if (validation == null || validation.isEmpty()) {
            return;
        }
        copyIfAbsent(validation, "format", "format");
        copyIfAbsent(validation, "candidateDigest", "candidate_digest");
        copyIfAbsent(validation, "metaDigest", "meta_digest");
        copyIfAbsent(validation, "validationDigest", "validation_digest");
        copyIfAbsent(validation, "vectorVersion", "vector_version");
        copyIfAbsent(validation, "normalizedConfig", "normalized_config");
    }

    private void copyIfAbsent(Map<String, Object> target,
                              String targetKey,
                              String sourceKey) {
        if (!target.containsKey(targetKey) && target.containsKey(sourceKey)) {
            target.put(targetKey, target.get(sourceKey));
        }
    }
}
