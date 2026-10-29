package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.config.MetaConfigValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataConfigWorkflowDefinitionTest {

    @Test
    void failedCandidateOnlyAllowsForceRejectReviseAndRejectsApprove() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "fileName", "probe_event.json",
                        "validationStatus", "failed",
                        "forceAllowed", true,
                        "candidate", Map.of("content", "{}", "digest", "digest-1")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "candidateDigest", "digest-1",
                "validationStatus", "failed"));

        assertThat(definition.allowedActions(state.getStep(), part.getMetadata()))
                .containsExactly("force_approve", "reject", "revise");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "approve"))
                .contains("未通过平台校验");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .isEmpty();

        definition.decorate(state, part, part.getMetadata());
        assertThat(part.getMetadata())
                .containsEntry("validationStatus", "failed")
                .containsEntry("forceAllowed", true)
                .containsEntry("allowedActions", List.of(
                        "force_approve", "reject", "revise"));

        assertThat(definition.transition(state, "force_approve"))
                .isEqualTo(AgentWorkflowStep.META_PREWRITE_CHECK);
        assertThat(state.getContext()).containsEntry("forced", true);
    }

    @Test
    void passedCandidateDoesNotExposeForceApproval() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "fileName", "probe_event.json",
                        "validationStatus", "success",
                        "forceAllowed", false,
                        "candidate", Map.of("content", "{}", "digest", "digest-1")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "candidateDigest", "digest-1",
                "validationStatus", "success"));

        assertThat(definition.allowedActions(state.getStep(), part.getMetadata()))
                .containsExactly("approve", "reject", "revise");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "approve"))
                .isEmpty();
        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .contains("没有可绕过的校验错误");
    }

    @Test
    void forceApprovalFailsWhenCandidateDigestChanged() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "fileName", "probe_event.json",
                        "validationStatus", "failed",
                        "forceAllowed", true,
                        "candidate", Map.of("content", "{}", "digest", "locked-digest")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "candidateDigest", "stale-digest"));

        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .contains("候选配置已变化");
    }

    @Test
    void approvalWithoutCardDigestIsRejected() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "fileName", "probe_event.json",
                        "validationStatus", "failed",
                        "forceAllowed", true,
                        "candidate", Map.of("content", "{}", "digest", "locked-digest")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json"));

        assertThat(definition.validateAction(state, part, part.getMetadata(), "approve"))
                .contains("缺少候选摘要");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .contains("缺少候选摘要");
    }

    @Test
    void overwriteDecisionAdoptsItsPlanIdBeforeTheWorkflowContextIsHydrated() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY,
                Map.of(
                        "fileName", "probe_event.json",
                        "candidate", Map.of("content", "{}", "digest", "digest-1")));
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "overwrite", true,
                "planId", "meta-overwrite-1",
                "candidateDigest", "digest-1"));

        assertThat(definition.resolveStep(state, part, part.getMetadata()))
                .isEqualTo(AgentWorkflowStep.META_OVERWRITE_CONFIRMATION);
        assertThat(state.getContext()).containsEntry("planId", "meta-overwrite-1");
        assertThat(part.getMetadata()).containsEntry("planId", "meta-overwrite-1");
    }

    @Test
    void overwriteDecisionStillBlocksWhenTheLockedDigestChanged() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY,
                Map.of(
                        "fileName", "probe_event.json",
                        "candidate", Map.of("content", "{}", "digest", "locked-digest")));
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "overwrite", true,
                "planId", "meta-overwrite-1",
                "candidateDigest", "stale-digest"));

        assertThat(definition.resolveStep(state, part, part.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void forceApprovalFailsWhenTargetFileChangedAfterConfirmation() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.fileExistsInConfigPath("meta", "probe_event.json"))
                .thenReturn(true);
        when(configService.readFile("meta", "probe_event.json"))
                .thenReturn("after");
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "configService", configService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "fileName", "probe_event.json",
                        "validationStatus", "failed",
                        "forceAllowed", true,
                        "targetFileDigest", MetaConfigValidationService.digest("before"),
                        "candidate", Map.of("content", "{}", "digest", "digest-1")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json",
                "candidateDigest", "digest-1"));

        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .contains("目标 Meta 文件在确认后已变化");
    }

    @Test
    @SuppressWarnings("unchecked")
    void decisionValidationLocksPlatformNormalizedCandidate() {
        MetaConfigValidationService validationService =
                mock(MetaConfigValidationService.class);
        MetaConfigValidationService.ValidationResult validation =
                new MetaConfigValidationService.ValidationResult(
                        false,
                        "{\"normalized\":true}",
                        "normalized-digest",
                        List.of(new MetaConfigValidationService.Diagnostic(
                                "META_COLUMN_TYPE_INVALID", "ERROR",
                                "$.attribute[0].column_type", "非法类型", "修正类型")),
                        List.of(),
                        List.of("非法类型"),
                        List.of());
        when(validationService.validate("probe_event.json", "raw-candidate"))
                .thenReturn(validation);
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "metaConfigValidationService", validationService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY,
                Map.of("candidate", Map.of(
                        "content", "raw-candidate",
                        "digest", "raw-digest")));
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json"));

        AgentWorkflowStep step = definition.resolveStep(state, part, part.getMetadata());

        assertThat(step).isEqualTo(AgentWorkflowStep.META_PLAN_CONFIRMATION);
        assertThat(state.getContext())
                .containsEntry("validationStatus", "failed")
                .containsEntry("forceAllowed", true)
                .containsEntry("validationDigest", "normalized-digest");
        assertThat((List<Map<String, Object>>) state.getContext().get("validationErrors"))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("code", "META_COLUMN_TYPE_INVALID")
                        .containsEntry("path", "$.attribute[0].column_type")
                        .containsEntry("repairHint", "修正类型"));
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        assertThat(candidate)
                .containsEntry("content", "{\"normalized\":true}")
                .containsEntry("digest", "normalized-digest")
                .containsEntry("artifactId", "meta-config:normalized-digest");
        assertThat(part.getMetadata())
                .containsEntry("candidateDigest", "normalized-digest")
                .containsEntry("validationStatus", "failed")
                .containsEntry("forceAllowed", true)
                .containsEntry("validationMessage",
                        "$.attribute[0].column_type：非法类型");
    }

    @Test
    void validationServiceFailureBlocksCandidateWithoutForceApproval() {
        MetaConfigValidationService validationService =
                mock(MetaConfigValidationService.class);
        when(validationService.validate("probe_event.json", "raw-candidate"))
                .thenThrow(new IllegalStateException("validator unavailable"));
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "metaConfigValidationService", validationService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY,
                Map.of("candidate", Map.of(
                        "content", "raw-candidate",
                        "digest", "raw-digest")));
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "configKind", "meta",
                "fileName", "probe_event.json"));

        AgentWorkflowStep step = definition.resolveStep(state, part, part.getMetadata());

        assertThat(step).isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(state.getContext())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("forceAllowed", false);
        assertThat(part.getMetadata())
                .containsEntry("validationStatus", "blocked")
                .containsEntry("forceAllowed", false);
        assertThat(part.getMetadata().get("validationMessage").toString())
                .contains("validator unavailable");
    }

    @Test
    void writtenRecordDoesNotPromoteWorkflowToVerified() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.BLOCKED,
                Map.of("candidate", Map.of("content", "{}", "digest", "digest-1")));
        state.setEvidenceRefs(List.of(
                Map.of("tool", "config_apply", "status", "succeeded"),
                Map.of("tool", "config_read", "status", "succeeded")));
        ChatMessagePart part = ChatMessagePart.builder()
                .type("metadata-config-record")
                .build();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "written");
        metadata.put("validationStatus", "failed");

        assertThat(definition.resolveStep(state, part, metadata))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        definition.updateContext(state, part, metadata);
        assertThat(metadata)
                .containsEntry("status", "written")
                .containsEntry("validationStatus", "failed");
    }

    @Test
    void forcedRecordKeepsAuditFieldsAndNeverClaimsValidationSuccess() {
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        AgentWorkflowState state = state(AgentWorkflowStep.META_VERIFIED,
                Map.of(
                        "forced", true,
                        "validationDigest", "validation-digest",
                        "validationErrors", List.of("非法类型"),
                        "candidate", Map.of("content", "{}", "digest", "candidate-digest")));
        ChatMessagePart record = ChatMessagePart.builder()
                .id("record-1")
                .type("metadata-config-record")
                .build();
        Map<String, Object> metadata = new LinkedHashMap<>();
        record.setMetadata(metadata);

        definition.updateContext(state, record, metadata);

        assertThat(metadata)
                .containsEntry("forced", true)
                .containsEntry("validationStatus", "forced")
                .containsEntry("candidateDigest", "candidate-digest")
                .containsEntry("validationDigest", "validation-digest")
                .containsEntry("validationErrors", List.of("非法类型"));
    }

    @Test
    void operationDecisionLocksPathsAndDigestWithoutForceApproval() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfigFileTree("meta"))
                .thenReturn(List.of(fileTree("old.json", "old-content")));
        when(configService.fileExistsInConfigPath("meta", "new.json"))
                .thenReturn(false);
        when(configService.readFile("meta", "old.json")).thenReturn("old-content");
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "configService", configService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY, Map.of());
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "operation", "rename",
                "sourcePath", "old.json",
                "targetPath", "new.json"));

        AgentWorkflowStep step = definition.resolveStep(state, part, part.getMetadata());

        assertThat(step).isEqualTo(AgentWorkflowStep.META_OPERATION_CONFIRMATION);
        assertThat(state.getContext())
                .containsEntry("operationDigest", part.getMetadata().get("operationDigest"));
        assertThat(part.getMetadata())
                .containsEntry("validationStatus", "success")
                .containsEntry("forceAllowed", false)
                .containsEntry("recursive", false)
                .containsEntry("allowedActions", List.of("approve", "reject", "revise"));
        assertThat(definition.allowedActions(step, part.getMetadata()))
                .containsExactly("approve", "reject", "revise");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "force_approve"))
                .contains("不支持强制写入");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "approve"))
                .isEmpty();
    }

    @Test
    void operationApprovalFailsWhenSourceContentOrTargetOccupancyChanges() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfigFileTree("meta"))
                .thenReturn(List.of(fileTree("old.json", "old-content")));
        when(configService.fileExistsInConfigPath("meta", "new.json"))
                .thenReturn(false);
        when(configService.readFile("meta", "old.json")).thenReturn("old-content");
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "configService", configService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY, Map.of());
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart part = decisionPart(Map.of(
                "operation", "rename",
                "sourcePath", "old.json",
                "targetPath", "new.json"));
        definition.resolveStep(state, part, part.getMetadata());

        when(configService.readFile("meta", "old.json")).thenReturn("changed-content");
        assertThat(definition.validateAction(state, part, part.getMetadata(), "approve"))
                .contains("已变化");
    }

    @Test
    void operationDecisionRejectsRootAndUnconfirmedRecursiveDelete() {
        ConfigService configService = mock(ConfigService.class);
        ConfigVo archive = new ConfigVo()
                .setFileName("archive")
                .setRelativePath("archive")
                .setIsDir(true)
                .setNodes(List.of(new ConfigVo()
                        .setFileName("a.json")
                        .setRelativePath("archive/a.json")
                        .setIsDir(false)));
        when(configService.getConfigFileTree("meta"))
                .thenReturn(List.of(new ConfigVo()
                        .setFileName("root")
                        .setRelativePath("")
                        .setIsDir(true)
                        .setNodes(List.of(archive))));
        MetadataConfigWorkflowDefinition definition = new MetadataConfigWorkflowDefinition();
        ReflectionTestUtils.setField(definition, "configService", configService);
        AgentWorkflowState state = state(AgentWorkflowStep.META_DISCOVERY, Map.of());
        state.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));

        ChatMessagePart root = decisionPart(Map.of(
                "operation", "delete",
                "sourcePath", ".", "recursive", true));
        assertThat(definition.resolveStep(state, root, root.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(state.getContext()).containsEntry("validationStatus", "blocked");

        AgentWorkflowState recursiveState = state(AgentWorkflowStep.META_DISCOVERY, Map.of());
        recursiveState.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart unconfirmed = decisionPart(Map.of(
                "operation", "delete",
                "sourcePath", "archive", "recursive", false));
        assertThat(definition.resolveStep(recursiveState, unconfirmed, unconfirmed.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(unconfirmed.getMetadata().get("validationMessage").toString())
                .contains("recursive=true");

        AgentWorkflowState confirmedState = state(AgentWorkflowStep.META_DISCOVERY, Map.of());
        confirmedState.setEvidenceRefs(List.of(Map.of(
                "tool", "config_tree",
                "status", "succeeded")));
        ChatMessagePart recursive = decisionPart(Map.of(
                "operation", "delete",
                "sourcePath", "archive", "recursive", true));
        assertThat(definition.resolveStep(confirmedState, recursive, recursive.getMetadata()))
                .isEqualTo(AgentWorkflowStep.META_OPERATION_CONFIRMATION);
        assertThat(recursive.getMetadata().get("affectedPaths"))
                .isEqualTo(List.of("archive", "archive/a.json"));
    }

    private ConfigVo fileTree(String relativePath, String content) {
        return new ConfigVo()
                .setFileName(relativePath)
                .setRelativePath(relativePath)
                .setIsDir(false)
                .setSize((long) content.length())
                .setNodes(List.of());
    }

    private AgentWorkflowState state(AgentWorkflowStep step,
                                     Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-meta")
                .setWorkflowType("metadata_config")
                .setAgentType("agent_metadata_config")
                .setStep(step)
                .setStatus("active")
                .setStateRevision(2)
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatMessagePart decisionPart(Map<String, Object> metadata) {
        return ChatMessagePart.builder()
                .id("decision-1")
                .type("metadata-config-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(metadata))
                .build();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
