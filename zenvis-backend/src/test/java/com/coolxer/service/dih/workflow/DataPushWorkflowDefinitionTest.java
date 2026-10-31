package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataPushWorkflowDefinitionTest {

    private static final String CONFIG = "sources: {}\nsinks: {}";
    private static final String CANDIDATE_DIGEST = digest(CONFIG);
    private static final String META_DIGEST = digest("{\"entity\":[]}");
    private static final String SOURCE_MARK = "data-push:chat-1:probe";

    private final DataPushWorkflowDefinition definition =
            new DataPushWorkflowDefinition();

    @Test
    void confirmationRequiresPassedValidationAndAllDigests() {
        Map<String, Object> metadata = confirmMetadata();

        assertThat(definition.resolveStep(readyState(), part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);

        Map<String, Object> missingValidationDigest = confirmMetadata();
        validation(missingValidationDigest).remove("validationDigest");
        assertThat(definition.resolveStep(
                readyState(), part("confirm", missingValidationDigest), missingValidationDigest))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(missingValidationDigest))
                .contains("PUSH_VALIDATION_DIGEST_MISSING");

        Map<String, Object> missingCandidateDigest = confirmMetadata();
        validation(missingCandidateDigest).remove("candidateDigest");
        assertThat(definition.resolveStep(
                readyState(),
                part("confirm", missingCandidateDigest), missingCandidateDigest))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(missingCandidateDigest))
                .contains("PUSH_VALIDATION_CANDIDATE_DIGEST_MISSING");

        Map<String, Object> failed = confirmMetadata();
        validation(failed).put("passed", false);
        assertThat(definition.resolveStep(readyState(), part("confirm", failed), failed))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(failed)).contains("PUSH_VALIDATION_FAILED");

        Map<String, Object> mismatchedCandidate = confirmMetadata();
        validation(mismatchedCandidate).put("candidateDigest", "stale-candidate");
        assertThat(definition.resolveStep(
                readyState(), part("confirm", mismatchedCandidate), mismatchedCandidate))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(mismatchedCandidate))
                .contains("PUSH_CANDIDATE_DIGEST_MISMATCH");

        Map<String, Object> mismatchedMeta = confirmMetadata();
        validation(mismatchedMeta).put("metaDigest", "stale-meta");
        assertThat(definition.resolveStep(
                readyState(), part("confirm", mismatchedMeta), mismatchedMeta))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(mismatchedMeta))
                .contains("PUSH_META_DIGEST_MISMATCH");
    }

    @Test
    void blockingReasonsCoverContentEvidenceAndValidationAvailability() {
        Map<String, Object> candidateBodyMismatch = confirmMetadata();
        AgentWorkflowState wrongCandidateDigest = readyState();
        wrongCandidateDigest.getContext().put(
                "candidate", Map.of("content", CONFIG, "digest", "stale-candidate"));
        assertThat(definition.resolveStep(
                wrongCandidateDigest,
                part("confirm", candidateBodyMismatch), candidateBodyMismatch))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(candidateBodyMismatch))
                .contains("PUSH_CANDIDATE_CONTENT_DIGEST_MISMATCH");

        Map<String, Object> unavailable = confirmMetadata();
        validation(unavailable).put("blocked", true);
        assertThat(definition.resolveStep(readyState(), part("confirm", unavailable), unavailable))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(unavailable)).contains("PUSH_VALIDATION_UNAVAILABLE");

        Map<String, Object> missingMetaRef = confirmMetadata();
        missingMetaRef.remove("metaRef");
        AgentWorkflowState stateWithoutMetaRef = readyState();
        stateWithoutMetaRef.getContext().remove("metaRef");
        assertThat(definition.resolveStep(
                stateWithoutMetaRef, part("confirm", missingMetaRef), missingMetaRef))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(missingMetaRef)).contains("PUSH_META_REFERENCE_MISSING");

        Map<String, Object> missingEvidenceMetadata = confirmMetadata();
        AgentWorkflowState stateWithoutEvidence = readyState();
        stateWithoutEvidence.setEvidenceRefs(List.of());
        assertThat(definition.resolveStep(
                stateWithoutEvidence,
                part("confirm", missingEvidenceMetadata), missingEvidenceMetadata))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(missingEvidenceMetadata))
                .contains("PUSH_EVIDENCE_MISSING");
    }

    @Test
    void validationEvidenceMustCarrySnakeCaseDigestsAndMatchCard() {
        Map<String, Object> missingEvidenceDigests = confirmMetadata();
        AgentWorkflowState noDigestState = readyState();
        validationEvidence(noDigestState).put("resultSummary",
                "{\"passed\":true,\"format\":\"toml\"}");
        assertThat(definition.resolveStep(
                noDigestState, part("confirm", missingEvidenceDigests), missingEvidenceDigests))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(missingEvidenceDigests))
                .contains("PUSH_VALIDATION_EVIDENCE_DIGEST_MISSING");

        Map<String, Object> evidenceMismatch = confirmMetadata();
        AgentWorkflowState staleEvidenceState = readyState();
        validationEvidence(staleEvidenceState).put("resultSummary",
                "{\"candidate_digest\":\"stale-candidate\","
                        + "\"meta_digest\":\"stale-meta\","
                        + "\"validation_digest\":\"stale-validation\"}");
        assertThat(definition.resolveStep(
                staleEvidenceState, part("confirm", evidenceMismatch), evidenceMismatch))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(reasonCodes(evidenceMismatch)).contains(
                "PUSH_VALIDATION_EVIDENCE_CANDIDATE_MISMATCH",
                "PUSH_VALIDATION_EVIDENCE_META_MISMATCH",
                "PUSH_VALIDATION_EVIDENCE_DIGEST_MISMATCH");
    }

    @Test
    void blockedCardOnlyAllowsRetryAndRetryRevalidatesTheLockedPlan() {
        AgentWorkflowState blocked = readyState()
                .setStep(AgentWorkflowStep.BLOCKED)
                .setStatus("blocked");

        assertThat(definition.allowedActions(AgentWorkflowStep.BLOCKED, Map.of()))
                .containsExactly("retry", "revise");
        assertThat(definition.isAllowedForState(AgentWorkflowStep.BLOCKED, "approve"))
                .isFalse();

        AgentWorkflowStep retried = definition.transition(blocked, "retry");
        assertThat(retried).isEqualTo(AgentWorkflowStep.PUSH_CONFIG_VALIDATION);
        blocked.setStep(retried);
        assertThat(blocked.getContext()).containsEntry("revalidateOnly", true);
        assertThat(definition.continuation(blocked, "retry").get("request").toString())
                .contains("重新校验", "不得创建或启动任务")
                .doesNotContain("重新收集");
    }

    @Test
    void successRecordRequiresRealTaskRunAndValidationIdentity() {
        AgentWorkflowState state = verifiedState();

        assertThat(definition.resolveStep(
                state, part("data-push-service-record", recordMetadata()), recordMetadata()))
                .isEqualTo(AgentWorkflowStep.PUSH_VERIFIED);

        Map<String, Object> missingRun = recordMetadata();
        missingRun.remove("runId");
        assertThat(definition.resolveStep(
                state, part("data-push-service-record", missingRun), missingRun))
                .isEqualTo(AgentWorkflowStep.BLOCKED);

        Map<String, Object> staleValidation = recordMetadata();
        staleValidation.put("validationDigest", "stale-validation");
        assertThat(definition.resolveStep(
                state, part("data-push-service-record", staleValidation), staleValidation))
                .isEqualTo(AgentWorkflowStep.BLOCKED);

        Map<String, Object> wrongTask = recordMetadata();
        wrongTask.put("taskId", "32");
        assertThat(definition.resolveStep(
                state, part("data-push-service-record", wrongTask), wrongTask))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void remembersExplicitFormatFromCodeFence() {
        AgentWorkflowState state = readyState();
        ChatMessagePart part = ChatMessagePart.builder()
                .type("code")
                .language("yml")
                .content(CONFIG)
                .build();

        definition.updateContext(state, part, Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> candidate =
                (Map<String, Object>) state.getContext().get("candidate");
        assertThat(candidate).containsEntry("format", "yaml");
    }

    private AgentWorkflowState readyState() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("candidate", Map.of("content", CONFIG, "digest", CANDIDATE_DIGEST));
        context.put("sourceMark", SOURCE_MARK);
        context.put("metaRef", Map.of(
                "fileName", "user_event.json", "contentDigest", META_DIGEST));
        context.put("request", Map.of("name", "探针推送"));
        return state(context, evidence(
                "config_tree", "config_read", "push_task_validate_config"));
    }

    private AgentWorkflowState verifiedState() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("candidate", Map.of("content", CONFIG, "digest", CANDIDATE_DIGEST));
        context.put("sourceMark", SOURCE_MARK);
        context.put("taskId", 31);
        context.put("runId", "run-1");
        context.put("validationDigest", "validation-1");
        context.put("metaRef", Map.of(
                "fileName", "user_event.json", "contentDigest", META_DIGEST));
        return state(context, evidence(
                "push_task_validate_config",
                "push_task_list",
                "push_task_diagnose"));
    }

    private AgentWorkflowState state(
            Map<String, Object> context,
            List<Map<String, Object>> evidence) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("data_push")
                .setAgentType("data-push-service-agent")
                .setObjectType("push_task")
                .setStep(AgentWorkflowStep.PUSH_FORMAT_CHECK)
                .setStatus("active")
                .setContext(context)
                .setEvidenceRefs(evidence);
    }

    private List<Map<String, Object>> evidence(String... tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String tool : tools) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tool", tool);
            item.put("status", "succeeded");
            if ("push_task_validate_config".equals(tool)) {
                item.put("resultSummary", validationEvidenceSummary(
                        CANDIDATE_DIGEST, META_DIGEST, "validation-1"));
            }
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> validationEvidence(AgentWorkflowState state) {
        return state.getEvidenceRefs().stream()
                .filter(item -> "push_task_validate_config".equals(item.get("tool")))
                .findFirst()
                .orElseThrow();
    }

    private String validationEvidenceSummary(
            String candidateDigest,
            String metaDigest,
            String validationDigest) {
        return "{\"candidate_digest\":\"" + candidateDigest + "\","
                + "\"meta_digest\":\"" + metaDigest + "\","
                + "\"validation_digest\":\"" + validationDigest + "\"}";
    }

    private List<String> reasonCodes(Map<String, Object> metadata) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reasons =
                (List<Map<String, Object>>) metadata.get("blockingReasons");
        assertThat(reasons).isNotNull();
        return reasons.stream().map(reason -> String.valueOf(reason.get("code"))).toList();
    }

    private ChatMessagePart part(String type, Map<String, Object> metadata) {
        return ChatMessagePart.builder()
                .type(type)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> confirmMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("action", "data_push.confirm_plan");
        metadata.put("configKind", "push_task");
        metadata.put("sourceMark", SOURCE_MARK);
        metadata.put("metaRef", Map.of(
                "fileName", "user_event.json", "contentDigest", META_DIGEST));
        metadata.put("validation", new LinkedHashMap<>(Map.of(
                "passed", true,
                "blocked", false,
                "candidateDigest", CANDIDATE_DIGEST,
                "metaDigest", META_DIGEST,
                "validationDigest", "validation-1")));
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validation(Map<String, Object> metadata) {
        return (Map<String, Object>) metadata.get("validation");
    }

    private Map<String, Object> recordMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "running");
        metadata.put("taskId", "31");
        metadata.put("runId", "run-1");
        metadata.put("sourceMark", SOURCE_MARK);
        metadata.put("validationDigest", "validation-1");
        return metadata;
    }

    private static String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
