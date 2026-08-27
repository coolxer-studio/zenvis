package com.coolxer.service.dih.workflow;

import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
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
}
