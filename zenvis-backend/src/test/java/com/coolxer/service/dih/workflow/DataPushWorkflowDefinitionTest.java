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
    private static final String SOURCE_MARK = "data-push:workflow-1";

    private final DataPushWorkflowDefinition definition =
            new DataPushWorkflowDefinition();

    @Test
    void confirmationRequiresCompleteSnapshotAndPassedMatchingReceipt() {
        Map<String, Object> metadata = confirmMetadata();
        AgentWorkflowState state = readyState();

        assertThat(definition.resolveStep(state, part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(metadata).containsEntry("candidateDigest", CANDIDATE_DIGEST)
                .containsEntry("sourceMark", SOURCE_MARK);
    }

    @Test
    void validationReceiptIsSelectedForTheLockedCandidateInsteadOfTheLatestToolCall() {
        AgentWorkflowState state = readyState();
        List<Map<String, Object>> evidenceRefs =
                new ArrayList<>(state.getEvidenceRefs());
        evidenceRefs.addAll(evidence(validation(
                "newer-unrelated-candidate", META_DIGEST, true, false, "validation-2")));
        state.setEvidenceRefs(evidenceRefs);
        Map<String, Object> metadata = confirmMetadata();

        assertThat(definition.resolveStep(state, part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(metadata).doesNotContainKey("blockingReasons");
        @SuppressWarnings("unchecked")
        Map<String, Object> lockedValidation =
                (Map<String, Object>) metadata.get("validation");
        assertThat(lockedValidation).containsEntry("candidateDigest", CANDIDATE_DIGEST)
                .containsEntry("validationDigest", "validation-1");
    }

    @Test
    void incompleteSnapshotStillReachesConfirmationWithoutPlatformGate() {
        AgentWorkflowState state = readyState();
        @SuppressWarnings("unchecked")
        Map<String, Object> candidate =
                (Map<String, Object>) state.getContext().get("candidate");
        candidate.remove("format");
        Map<String, Object> metadata = confirmMetadata();

        assertThat(definition.resolveStep(state, part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(metadata).doesNotContainKey("blockingReasons");
    }

    @Test
    void validationFailuresDoNotBlockTheConfirmationCard() {
        Map<String, Object> missingMetadata = confirmMetadata();
        AgentWorkflowState missing = readyState();
        missing.setEvidenceRefs(List.of());
        assertThat(definition.resolveStep(missing, part("confirm", missingMetadata), missingMetadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(missingMetadata).doesNotContainKey("blockingReasons");

        Map<String, Object> unavailableMetadata = confirmMetadata();
        AgentWorkflowState unavailable = readyState(validation(
                CANDIDATE_DIGEST, META_DIGEST, false, true, "validation-1"));
        assertThat(definition.resolveStep(
                unavailable, part("confirm", unavailableMetadata), unavailableMetadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(unavailableMetadata).doesNotContainKey("blockingReasons");

        Map<String, Object> staleMetadata = confirmMetadata();
        AgentWorkflowState stale = readyState(validation(
                "stale-candidate", META_DIGEST, true, false, "validation-1"));
        assertThat(definition.resolveStep(stale, part("confirm", staleMetadata), staleMetadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(staleMetadata).doesNotContainKey("blockingReasons");

        Map<String, Object> failedMetadata = confirmMetadata();
        AgentWorkflowState failed = readyState(validation(
                CANDIDATE_DIGEST, META_DIGEST, false, false, "validation-1"));
        assertThat(definition.resolveStep(failed, part("confirm", failedMetadata), failedMetadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(failedMetadata).doesNotContainKey("blockingReasons");
    }

    @Test
    void cardMetaRefBackfillsAMissingSnapshotMetaReference() {
        AgentWorkflowState state = readyState();
        @SuppressWarnings("unchecked")
        Map<String, Object> context = state.getContext();
        context.remove("metaRef");
        Map<String, Object> metadata = confirmMetadata();

        assertThat(definition.resolveStep(state, part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(context).containsEntry("metaRef", Map.of(
                "fileName", "old.json", "contentDigest", "old-meta"));
        assertThat(metadata).containsEntry("metaRef", Map.of(
                "fileName", "old.json", "contentDigest", "old-meta"));
    }

    @Test
    void cardCannotOverrideLockedMetaSourceOrReceipt() {
        AgentWorkflowState state = readyState();
        Map<String, Object> metadata = confirmMetadata();
        metadata.put("sourceMark", "data-push:model-old");
        metadata.put("metaRef", Map.of(
                "fileName", "old.json", "contentDigest", "old-meta"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cardValidation =
                (Map<String, Object>) metadata.get("validation");
        cardValidation.put("candidateDigest", "old-candidate");
        cardValidation.put("metaDigest", "old-meta");

        assertThat(definition.resolveStep(state, part("confirm", metadata), metadata))
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        assertThat(metadata).containsEntry("sourceMark", SOURCE_MARK)
                .containsEntry("metaRef", Map.of(
                        "fileName", "user_event.json", "contentDigest", META_DIGEST));
        @SuppressWarnings("unchecked")
        Map<String, Object> lockedValidation =
                (Map<String, Object>) metadata.get("validation");
        assertThat(lockedValidation).containsEntry("candidateDigest", CANDIDATE_DIGEST);
        assertThat(metadata).doesNotContainKey("blockingReasons");
    }

    @Test
    void validatedCandidateIsSealedUntilRevision() {
        AgentWorkflowState state = readyState();
        Map<String, Object> context = state.getContext();
        DataPushPlanSnapshot.load(context)
                .withRequest(Map.of("name", "探针推送"))
                .withValidation(validation(
                        CANDIDATE_DIGEST, META_DIGEST, true, false, "validation-1"))
                .persist(context);

        ChatMessagePart replacement = ChatMessagePart.builder()
                .type("code")
                .language("yaml")
                .content("sources: {}\nsinks: {changed: true}")
                .build();
        definition.updateContext(state, replacement, new LinkedHashMap<>());
        assertThat(context).containsEntry("candidate", Map.of(
                "content", CONFIG,
                "digest", CANDIDATE_DIGEST,
                "format", "yaml"));

        state.setStep(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
        definition.transition(state, "revise");
        definition.updateContext(state, replacement, new LinkedHashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> candidate =
                (Map<String, Object>) context.get("candidate");
        assertThat(candidate).containsEntry("content", replacement.getContent());
        assertThat(context).doesNotContainKey("validation");
    }

    @Test
    void blockedCardOnlyAllowsRetryAndRevise() {
        AgentWorkflowState state = readyState();
        assertThat(definition.allowedActions(AgentWorkflowStep.BLOCKED, Map.of()))
                .containsExactly("retry", "revise");
        assertThat(definition.isAllowedForState(AgentWorkflowStep.BLOCKED, "approve"))
                .isFalse();
        assertThat(definition.isAllowedForState(AgentWorkflowStep.BLOCKED, "retry"))
                .isTrue();

        state.setStep(AgentWorkflowStep.BLOCKED);
        assertThat(definition.transition(state, "retry"))
                .isEqualTo(AgentWorkflowStep.PUSH_CONFIG_VALIDATION);
        assertThat(state.getContext()).containsEntry("revalidateOnly", true);
    }

    private AgentWorkflowState readyState() {
        return readyState(validation(
                CANDIDATE_DIGEST, META_DIGEST, true, false, "validation-1"));
    }

    private AgentWorkflowState readyState(Map<String, Object> validation) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("candidate", new LinkedHashMap<>(Map.of(
                "content", CONFIG,
                "digest", CANDIDATE_DIGEST,
                "format", "yaml")));
        context.put("sourceMark", SOURCE_MARK);
        context.put("metaRef", Map.of(
                "fileName", "user_event.json", "contentDigest", META_DIGEST));
        context.put("request", Map.of("name", "探针推送"));
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("data_push")
                .setAgentType("agent_data_push_service")
                .setObjectType("push_task")
                .setStep(AgentWorkflowStep.PUSH_FORMAT_CHECK)
                .setStatus("active")
                .setContext(context)
                .setEvidenceRefs(evidence(validation));
    }

    private List<Map<String, Object>> evidence(Map<String, Object> validation) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.of("tool", "config_tree", "status", "succeeded"));
        result.add(Map.of("tool", "config_read", "status", "succeeded"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tool", "push_task_validate_config");
        item.put("status", "succeeded");
        item.put("validation", validation);
        result.add(item);
        return result;
    }

    private Map<String, Object> validation(String candidateDigest,
                                           String metaDigest,
                                           boolean passed,
                                           boolean blocked,
                                           String validationDigest) {
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("passed", passed);
        validation.put("blocked", blocked);
        validation.put("format", "yaml");
        validation.put("candidateDigest", candidateDigest);
        validation.put("metaDigest", metaDigest);
        validation.put("validationDigest", validationDigest);
        return validation;
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
        metadata.put("sourceMark", "data-push:model-old");
        metadata.put("metaRef", Map.of(
                "fileName", "old.json", "contentDigest", "old-meta"));
        metadata.put("request", Map.of("name", "探针推送"));
        metadata.put("validation", new LinkedHashMap<>(Map.of(
                "passed", true,
                "blocked", false,
                "format", "yaml",
                "candidateDigest", "card-old-candidate",
                "metaDigest", "old-meta",
                "validationDigest", "card-old-validation")));
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
