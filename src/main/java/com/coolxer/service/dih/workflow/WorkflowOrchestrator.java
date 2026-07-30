package com.coolxer.service.dih.workflow;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.ChatMessagePart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkflowOrchestrator {

    private final WorkflowStateStore stateStore;

    @Autowired(required = false)
    private List<WorkflowDefinition> workflowDefinitions;

    @Autowired(required = false)
    private WorkflowMetrics workflowMetrics;

    public WorkflowOrchestrator(WorkflowStateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * Backwards source-compatible entry used by existing visualization callers.
     */
    public void prepareVisualizationParts(
            ChatSession chatSession,
            List<ChatMessagePart> parts,
            List<Map<String, Object>> evidenceRefs) {
        prepareVisualizationParts(chatSession, parts, evidenceRefs, null);
    }

    public void prepareVisualizationParts(
            ChatSession chatSession,
            List<ChatMessagePart> parts,
            List<Map<String, Object>> evidenceRefs,
            String messageId) {
        prepareParts(chatSession, parts, evidenceRefs, messageId);
    }

    public void prepareParts(
            ChatSession chatSession,
            List<ChatMessagePart> parts,
            List<Map<String, Object>> evidenceRefs,
            String messageId) {
        if (chatSession == null || parts == null || parts.isEmpty()) {
            return;
        }
        WorkflowDefinition definition = definition(chatSession.getType());
        if (definition == null) {
            return;
        }
        AgentWorkflowState state = stateStore
                .loadActive(chatSession, definition.agentType())
                .orElseGet(() -> newState(definition));
        mergeEvidence(state, evidenceRefs);

        boolean changed = false;
        for (ChatMessagePart part : parts) {
            if (!definition.supportsPart(part)) {
                continue;
            }
            Map<String, Object> metadata = part.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(part.getMetadata());
            part.setMetadata(metadata);

            // Candidate config/code parts are captured but are not interactive states.
            if ("config".equals(part.getType()) || "code".equals(part.getType())) {
                definition.updateContext(state, part, metadata);
                changed = true;
                continue;
            }

            AgentWorkflowStep previousStep = state.getStep();
            String previousUpdatedAt = state.getUpdatedAt();
            AgentWorkflowStep step = definition.resolveStep(state, part, metadata);
            if (step == null) {
                continue;
            }
            changed = true;
            state.setStep(step);
            state.setStatus(terminalStatus(step));
            state.setObjectType(definition.resolveObjectType(state, part, metadata));
            state.setMessageId(StringUtils.hasText(messageId)
                    ? messageId : state.getMessageId());
            state.setPartId(part.getId());
            state.setStateRevision(state.getStateRevision() + 1);
            state.setUpdatedAt(Instant.now().toString());
            if (workflowMetrics != null && previousStep != step) {
                workflowMetrics.transition(
                        state.getAgentType(), previousStep, step, previousUpdatedAt);
            }

            if (step == AgentWorkflowStep.ENTITY_SELECTION) {
                attachEntitySelectionEvidence(metadata, state.getEvidenceRefs());
            }
            definition.updateContext(state, part, metadata);
            captureStrictOptions(state, metadata);

            metadata.put("workflowId", state.getWorkflowId());
            metadata.put("workflowVersion", state.getWorkflowVersion());
            metadata.put("stateRevision", state.getStateRevision());
            metadata.put("step", step.name());
            metadata.put("objectType", state.getObjectType());
            metadata.put("allowedActions",
                    definition.allowedActions(step, metadata));
            metadata.put("evidenceRefs", state.getEvidenceRefs());
            String existingSource = text(metadata.get("source"));
            if (StringUtils.hasText(existingSource)
                    && !"workflow".equals(existingSource)
                    && !"demo".equals(existingSource)) {
                metadata.putIfAbsent("businessSource", existingSource);
            }
            metadata.put("source", "workflow");
            metadata.put("validationStatus",
                    step == AgentWorkflowStep.BLOCKED ? "blocked" : "success");
            definition.decorate(state, part, metadata);
            if (step == AgentWorkflowStep.BLOCKED) {
                appendFailure(state, metadata, previousStep);
                if (workflowMetrics != null) {
                    workflowMetrics.blocked(previousStep);
                }
            }
        }
        if (changed) {
            stateStore.upsert(chatSession, state);
        }
    }

    public WorkflowDefinition definition(String agentType) {
        return definitions().stream()
                .filter(item -> item.agentType().equals(agentType))
                .findFirst()
                .orElse(null);
    }

    private List<WorkflowDefinition> definitions() {
        if (workflowDefinitions != null && !workflowDefinitions.isEmpty()) {
            return workflowDefinitions;
        }
        return List.of(
                new DataVisualizationWorkflowDefinition(),
                new DataAccessWorkflowDefinition());
    }

    private AgentWorkflowState newState(WorkflowDefinition definition) {
        String now = Instant.now().toString();
        return new AgentWorkflowState()
                .setWorkflowId(UUID.randomUUID().toString())
                .setWorkflowType(definition.workflowType())
                .setAgentType(definition.agentType())
                .setObjectType(definition.defaultObjectType())
                .setStep(AgentWorkflowStep.INTENT_CONFIRMATION)
                .setStatus("active")
                .setStateRevision(0)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private void mergeEvidence(
            AgentWorkflowState state,
            List<Map<String, Object>> newEvidence) {
        if (newEvidence == null || newEvidence.isEmpty()) {
            return;
        }
        List<Map<String, Object>> merged = state.getEvidenceRefs() == null
                ? new ArrayList<>()
                : new ArrayList<>(state.getEvidenceRefs());
        Set<String> keys = new LinkedHashSet<>();
        merged.forEach(item -> keys.add(evidenceKey(item)));
        for (Map<String, Object> evidence : newEvidence) {
            if (evidence != null && keys.add(evidenceKey(evidence))) {
                merged.add(new LinkedHashMap<>(evidence));
            }
        }
        state.setEvidenceRefs(merged);
    }

    private String evidenceKey(Map<String, Object> evidence) {
        return firstText(
                text(evidence.get("evidenceId")),
                text(evidence.get("tool")) + ":"
                        + text(evidence.get("argumentsDigest")));
    }

    private void attachEntitySelectionEvidence(
            Map<String, Object> metadata,
            List<Map<String, Object>> evidence) {
        String sourceEvidenceId = evidence == null ? "" : evidence.stream()
                .filter(item -> Set.of(
                                "retrieval_list_display_entity",
                                "retrieval_list_entity")
                        .contains(text(item.get("tool"))))
                .map(item -> text(item.get("evidenceId")))
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second)
                .orElse("");
        Object rawSteps = metadata.get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object value : steps) {
            Map<String, Object> step = map(value);
            if (step.isEmpty()) {
                continue;
            }
            if (text(step.get("id")).toLowerCase().contains("entity")
                    || text(step.get("title")).contains("实体")) {
                step.put("strictOptions", true);
                step.put("sourceEvidenceId", sourceEvidenceId);
            }
            normalized.add(step);
        }
        metadata.put("steps", normalized);
    }

    private void captureStrictOptions(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        Object rawSteps = metadata.get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return;
        }
        Map<String, Object> strictOptions = new LinkedHashMap<>();
        for (Object rawStep : steps) {
            Map<String, Object> step = map(rawStep);
            if (!Boolean.TRUE.equals(step.get("strictOptions"))) {
                continue;
            }
            List<String> values = new ArrayList<>();
            Object rawSuggestions = step.get("suggestions");
            if (rawSuggestions instanceof List<?> suggestions) {
                for (Object rawSuggestion : suggestions) {
                    Map<String, Object> suggestion = map(rawSuggestion);
                    String value = suggestion.isEmpty()
                            ? text(rawSuggestion) : text(suggestion.get("value"));
                    if (StringUtils.hasText(value)) {
                        values.add(value);
                    }
                }
            }
            strictOptions.put(text(step.get("id")), values);
        }
        if (!strictOptions.isEmpty()) {
            state.getContext().put("strictOptions", strictOptions);
        }
    }

    private void appendFailure(
            AgentWorkflowState state,
            Map<String, Object> metadata,
            AgentWorkflowStep previousStep) {
        List<Map<String, Object>> failures = state.getFailures() == null
                ? new ArrayList<>() : new ArrayList<>(state.getFailures());
        Map<String, Object> failure = new LinkedHashMap<>();
        AgentWorkflowStep failedStep = previousStep == null
                ? state.getStep() : previousStep;
        failure.put("stage", failedStep.name());
        failure.put("retryStep", failedStep.name());
        Map<String, Object> query = map(metadata.get("query"));
        failure.put("tool", firstText(
                text(query.get("tool")),
                text(metadata.get("metaTool"))));
        failure.put("requestSummary", abbreviate(
                query.get("request") == null ? ""
                        : query.get("request").toString(), 2000));
        failure.put("error", firstText(
                text(metadata.get("validationMessage")),
                "工作流校验失败"));
        failure.put("retryable", true);
        if (StringUtils.hasText(state.getArtifactId())) {
            failure.put("preservedArtifactId", state.getArtifactId());
        }
        failure.put("occurredAt", Instant.now().toString());
        if (failures.size() >= 50) {
            failures.remove(0);
        }
        failures.add(failure);
        state.setFailures(failures);
        metadata.put("failure", failure);
    }

    private String terminalStatus(AgentWorkflowStep step) {
        return switch (step) {
            case VERIFIED, META_VERIFIED, PUSH_VERIFIED, COMPLETED ->
                    "completed";
            case CANCELLED -> "cancelled";
            case BLOCKED -> "blocked";
            default -> "active";
        };
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String abbreviate(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value : value.substring(0, maxLength) + "…";
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        raw.forEach((key, item) -> mapped.put(String.valueOf(key), item));
        return mapped;
    }
}
