package com.coolxer.service.dih.workflow;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowOrchestratorTest {

    @Test
    void entityOptionsBecomeStrictAndCarryMcpEvidence() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_visualization");
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "analysis_entity");
        step.put("title", "选择实体");
        step.put("suggestions", List.of(Map.of(
                "label", "探针消息（probe_message）",
                "value", "probe_message")));
        ChatMessagePart part = ChatMessagePart.builder()
                .id("part-1")
                .type("info-steps")
                .title("请选择实体")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_visualization.select_entity_from_meta",
                        "steps", List.of(step))))
                .build();
        List<Map<String, Object>> evidence = List.of(Map.of(
                "evidenceId", "call-1",
                "tool", "retrieval_list_display_entity",
                "status", "succeeded"));

        orchestrator.prepareVisualizationParts(
                session, List.of(part), evidence, "message-1");

        assertThat(part.getMetadata())
                .containsEntry("workflowVersion", "1")
                .containsEntry("step", "ENTITY_SELECTION")
                .containsEntry("allowedActions", List.of("submit"));
        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedStep =
                ((List<Map<String, Object>>) part.getMetadata().get("steps")).get(0);
        assertThat(normalizedStep)
                .containsEntry("strictOptions", true)
                .containsEntry("sourceEvidenceId", "call-1");
        assertThat(store.loadActive(session, "agent_data_visualization"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getMessageId()).isEqualTo("message-1");
                    assertThat(state.getPartId()).isEqualTo("part-1");
                });
    }

    @Test
    void dataAccessPlanLocksCandidateAndRequiresRealConfigTreeEvidence() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_access");
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("config-1")
                .type("config")
                .status("completed")
                .content("""
                        {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                        """)
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta-config")))
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-1")
                .type("data-access-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta",
                        "fileName", "probe_message.json",
                        "actions", List.of(
                                "apply_config", "abandon", "revise"))))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(candidate, decision),
                List.of(Map.of(
                        "evidenceId", "config-tree-1",
                        "tool", "config_tree",
                        "status", "succeeded")),
                "message-1");

        assertThat(decision.getMetadata())
                .containsEntry("source", "workflow")
                .containsEntry("step", "META_PLAN_CONFIRMATION")
                .containsEntry("validationStatus", "success");
        assertThat(decision.getMetadata().get("candidateDigest"))
                .isNotNull();
        assertThat(store.loadActive(session, "agent_data_access"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getWorkflowType())
                            .isEqualTo("data_access");
                    assertThat(state.getContext())
                            .containsEntry("fileName", "probe_message.json")
                            .containsKey("candidate");
                });
    }

    @Test
    void dataAccessPlanWithoutConfigTreeEvidenceIsBlocked() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_access");
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("config-1")
                .type("config")
                .content("{\"entity\":[],\"attribute\":[],\"operator\":[]}")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta-config")))
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-1")
                .type("data-access-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta",
                        "fileName", "probe_message.json")))
                .build();

        orchestrator.prepareParts(
                session, List.of(candidate, decision), List.of(), "message-1");

        assertThat(decision.getMetadata())
                .containsEntry("step", "BLOCKED")
                .containsEntry("validationStatus", "blocked");
        AgentWorkflowState blocked = store.loadActive(
                session, "agent_data_access").orElseThrow();
        assertThat(new DataAccessWorkflowDefinition()
                .transition(blocked, "retry"))
                .isEqualTo(AgentWorkflowStep.META_DISCOVERY);
    }

    @Test
    void visualizationPersistenceLocksCompleteDashboardRequest() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_visualization");
        ChatMessagePart preview = ChatMessagePart.builder()
                .id("artifact-1")
                .type("visualization-chart-preview")
                .status("completed")
                .metadata(new LinkedHashMap<>(Map.of(
                        "artifactId", "artifact-1")))
                .build();
        Map<String, Object> dashboardRequest = Map.of(
                "name", "探针消息看板",
                "code", "probe-message-dashboard",
                "type", "LOW_CODE_PAGE",
                "configIndex", "probe-message",
                "source", "workflow");
        ChatMessagePart confirmation = ChatMessagePart.builder()
                .id("persist-1")
                .type("confirm")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_visualization.apply_config",
                        "dashboard", Map.of("request", dashboardRequest))))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(preview, confirmation),
                List.of(),
                "message-1");

        assertThat(confirmation.getMetadata())
                .containsEntry("step", "PERSIST_CONFIRMATION")
                .containsEntry("validationStatus", "success");
        assertThat(store.loadActive(session, "agent_data_visualization"))
                .get()
                .satisfies(state -> assertThat(state.getContext())
                        .containsEntry(
                                "persistencePlan",
                                Map.of("dashboard",
                                        Map.of("request", dashboardRequest))));
    }

    @Test
    void visualizationPersistenceWithoutLockedTargetIsBlocked() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_visualization");
        ChatMessagePart preview = ChatMessagePart.builder()
                .id("artifact-1")
                .type("visualization-chart-preview")
                .metadata(new LinkedHashMap<>(Map.of(
                        "artifactId", "artifact-1")))
                .build();
        ChatMessagePart confirmation = ChatMessagePart.builder()
                .id("persist-1")
                .type("confirm")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_visualization.apply_config")))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(preview, confirmation),
                List.of(),
                "message-1");

        assertThat(confirmation.getMetadata())
                .containsEntry("step", "BLOCKED")
                .containsEntry("validationStatus", "blocked");
    }
}
