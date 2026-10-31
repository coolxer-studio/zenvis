package com.coolxer.service.dih.workflow;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.config.MetaConfigValidationService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowOrchestratorTest {

    @Test
    void entityOptionsBecomeStrictAndCarryMcpEvidence() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_visualization_chart");
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
                        "action", "visualization_chart.select_entity_from_meta",
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
        assertThat(store.loadActive(session, "agent_visualization_chart"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getMessageId()).isEqualTo("message-1");
                    assertThat(state.getPartId()).isEqualTo("part-1");
                });
    }

    @Test
    void metadataConfigPlanLocksCandidateAndRequiresRealConfigTreeEvidence() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_metadata_config");
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
                .type("metadata-config-decision")
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
        assertThat(store.loadActive(session, "agent_metadata_config"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getWorkflowType())
                            .isEqualTo("metadata_config");
                    assertThat(state.getContext())
                            .containsEntry("fileName", "probe_message.json")
                            .containsKey("candidate");
                });
    }

    @Test
    void metadataConfigPlanWithoutConfigTreeEvidenceIsBlocked() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_metadata_config");
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("config-1")
                .type("config")
                .content("{\"entity\":[],\"attribute\":[],\"operator\":[]}")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta-config")))
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-1")
                .type("metadata-config-decision")
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
                session, "agent_metadata_config").orElseThrow();
        assertThat(new MetadataConfigWorkflowDefinition()
                .transition(blocked, "retry"))
                .isEqualTo(AgentWorkflowStep.META_DISCOVERY);
    }

    @Test
    void firstOverwriteDecisionCardStaysInteractiveWithItsServerOwnedPlanId() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_metadata_config");
        String content = """
                {"entity":[{"name":"car_info"}],"attribute":[],"operator":[]}
                """;
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("config-1")
                .type("config")
                .content(content)
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta-config")))
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-1")
                .type("metadata-config-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "meta",
                        "fileName", "car_info.json",
                        "overwrite", true,
                        "planId", "meta-car_info-overwrite-1",
                        "candidateDigest", MetaConfigValidationService.digest(content))))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(candidate, decision),
                List.of(Map.of(
                        "evidenceId", "config-tree-1",
                        "tool", "config_tree",
                        "status", "succeeded")),
                "message-1");

        assertThat(decision.getStatus()).isEqualTo("pending");
        assertThat(decision.getMetadata())
                .containsEntry("step", "META_OVERWRITE_CONFIRMATION")
                .containsEntry("validationStatus", "success")
                .containsEntry("planId", "meta-car_info-overwrite-1")
                .containsEntry("allowedActions", List.of("approve", "reject", "revise"));
        assertThat(store.loadActive(session, "agent_metadata_config"))
                .get()
                .satisfies(state -> assertThat(state.getContext())
                        .containsEntry("planId", "meta-car_info-overwrite-1"));
    }

    @Test
    void dataPushPlanUsesServerLockedMetaAndValidationReceipt() {
        String content = "[sources.demo]\ntype = \"demo_logs\"";
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_push_service");
        seedDataPushWorkflow(store, session);
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("code-1")
                .type("code")
                .language("toml")
                .content(content)
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-1")
                .type("data-push-service-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_push.confirm_plan",
                        "configKind", "push_task",
                        "sourceMark", "data-push:chat-1:probe",
                        "metaRef", Map.of(
                                "fileName", "user_event.json",
                                "contentDigest", "digest-meta"),
                        "validation", Map.of(
                                "passed", true,
                                "blocked", false,
                                "candidateDigest", digest(content),
                                "metaDigest", "digest-meta",
                                "validationDigest", "validation-1"),
                        "request", Map.of("name", "探针推送"))))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(candidate, decision),
                List.of(
                        Map.of(
                                "evidenceId", "config-tree-1",
                                "tool", "config_tree",
                                "status", "succeeded"),
                        Map.of(
                                "evidenceId", "config-read-1",
                                "tool", "config_read",
                                "status", "succeeded"),
                        Map.of(
                                "evidenceId", "validation-1",
                                "tool", "push_task_validate_config",
                                "status", "succeeded",
                                "validation", Map.of(
                                        "passed", true,
                                        "blocked", false,
                                        "format", "toml",
                                        "candidateDigest", digest(content),
                                        "metaDigest", "digest-meta",
                                        "validationDigest", "validation-1"))),
                "message-1");

        assertThat(decision.getMetadata())
                .containsEntry("source", "workflow")
                .containsEntry("step", "PUSH_PLAN_CONFIRMATION")
                .containsEntry("validationStatus", "success")
                .containsKey("candidateDigest");
        assertThat(store.loadActive(session, "agent_data_push_service"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getWorkflowType()).isEqualTo("data_push");
                    assertThat(state.getContext())
                            .containsEntry("sourceMark", "data-push:workflow-orchestrator")
                            .containsKey("metaRef")
                            .containsKey("candidate");
                });
    }

    @Test
    void dataPushConfirmationDoesNotPersistPlatformGateFailures() {
        String content = "[sources.demo]\ntype = \"demo_logs\"";
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_data_push_service");
        seedDataPushWorkflow(store, session);
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("code-blocked")
                .type("code")
                .language("toml")
                .content(content)
                .build();
        ChatMessagePart decision = ChatMessagePart.builder()
                .id("decision-blocked")
                .type("data-push-service-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "data_push.confirm_plan",
                        "configKind", "push_task",
                        "sourceMark", "data-push:chat-1:probe",
                        "metaRef", Map.of(
                                "fileName", "user_event.json",
                                "contentDigest", "digest-meta"),
                        "validation", Map.of(
                                "passed", true,
                                "blocked", false,
                                "candidateDigest", "stale-candidate",
                                "metaDigest", "digest-meta",
                                "validationDigest", "validation-1"),
                        "request", Map.of("name", "探针推送"))))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(candidate, decision),
                List.of(
                        Map.of(
                                "evidenceId", "config-tree-1",
                                "tool", "config_tree",
                                "status", "succeeded"),
                        Map.of(
                                "evidenceId", "config-read-1",
                                "tool", "config_read",
                                "status", "succeeded"),
                        Map.of(
                                "evidenceId", "validation-1",
                                "tool", "push_task_validate_config",
                                "status", "succeeded",
                                "validation", Map.of(
                                        "passed", true,
                                        "blocked", false,
                                        "format", "toml",
                                        "candidateDigest", "stale-candidate",
                                        "metaDigest", "digest-meta",
                                        "validationDigest", "validation-1"))),
                "message-1");

        assertThat(decision.getStatus()).isEqualTo("pending");
        assertThat(decision.getMetadata())
                .containsEntry("step", "PUSH_PLAN_CONFIRMATION")
                .containsEntry("validationStatus", "success")
                .containsEntry("allowedActions", List.of("approve", "reject", "revise"))
                .doesNotContainKey("blockingReasons");
        assertThat(store.loadActive(session, "agent_data_push_service"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getStep())
                            .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
                    assertThat(state.getContext())
                            .containsEntry("sourceMark", "data-push:workflow-orchestrator")
                            .containsEntry("metaRef", Map.of(
                                    "fileName", "user_event.json",
                                    "contentDigest", "digest-meta"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> lockedCandidate =
                            (Map<String, Object>) state.getContext().get("candidate");
                    assertThat(lockedCandidate).containsEntry("digest", digest(content));
                    assertThat(state.getFailures()).isEmpty();
                });
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void visualizationPersistenceLocksCompleteDashboardRequest() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_visualization_dashboard");
        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("wf-dashboard")
                .setWorkflowType("visualization_dashboard")
                .setAgentType("agent_visualization_dashboard")
                .setStep(AgentWorkflowStep.ARTIFACT_READY)
                .setStatus("active")
                .setStateRevision(1)
                .setContext(new LinkedHashMap<>()));
        Map<String, Object> dashboardRequest = Map.of(
                "name", "探针消息看板",
                "code", "probe-message-dashboard",
                "type", "LOW_CODE_PAGE",
                "configIndex", "probe-message",
                "source", "workflow");
        ChatMessagePart confirmation = ChatMessagePart.builder()
                .id("persist-1")
                .type("visualization-dashboard-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_dashboard.confirm_plan",
                        "configType", "probe-message",
                        "configIndex", "probe-message",
                        "fileName", "index.json",
                        "dashboard", Map.of("request", dashboardRequest))))
                .build();
        ChatMessagePart candidate = ChatMessagePart.builder()
                .id("config-1")
                .type("config")
                .status("completed")
                .content("{\"type\":\"page\",\"body\":[]}")
                .metadata(new LinkedHashMap<>(Map.of(
                        "configKind", "low-code-page",
                        "configType", "probe-message",
                        "fileName", "index.json")))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(candidate, confirmation),
                List.of(),
                "message-1");

        assertThat(confirmation.getMetadata())
                .containsEntry("step", "PERSIST_CONFIRMATION")
                .containsEntry("validationStatus", "success");
        assertThat(store.loadActive(session, "agent_visualization_dashboard"))
                .get()
                .satisfies(state -> assertThat(state.getContext())
                        .containsEntry(
                                "persistencePlan",
                                Map.of(
                                        "configType", "probe-message",
                                        "configIndex", "probe-message",
                                        "fileName", "index.json",
                                        "dashboard", Map.of("request", dashboardRequest))));
    }

    @Test
    void visualizationPersistenceWithoutLockedTargetIsBlocked() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_visualization_dashboard");
        ChatMessagePart confirmation = ChatMessagePart.builder()
                .id("persist-1")
                .type("visualization-dashboard-decision")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_dashboard.confirm_plan")))
                .build();

        orchestrator.prepareParts(
                session,
                List.of(confirmation),
                List.of(),
                "message-1");

        assertThat(confirmation.getMetadata())
                .containsEntry("step", "BLOCKED")
                .containsEntry("validationStatus", "blocked");
    }

    @Test
    void failedVisualizationQueryCardIsBlockedAndRetryable() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_visualization_chart");
        ChatMessagePart failure = ChatMessagePart.builder()
                .id("query-failure")
                .type("visualization-chart-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_chart.execute_approved_query",
                        "validationStatus", "blocked",
                        "query", Map.of(
                                "tool", "entity_aggregate",
                                "request", Map.of("entity", "user_event")),
                        "planId", "plan-1")))
                .build();

        orchestrator.prepareParts(
                session, List.of(failure), List.of(), "message-1");

        assertThat(failure.getStatus()).isEqualTo("blocked");
        assertThat(failure.getMetadata())
                .containsEntry("step", "BLOCKED")
                .containsEntry("validationStatus", "blocked")
                .containsEntry("blockedAction",
                        "visualization_chart.execute_approved_query")
                .containsEntry("allowedActions", List.of("retry", "revise"))
                .doesNotContainKey("action");
    }

    @Test
    void appMenuCardCanResumeOnlyAfterAConfigRecordExistsInTheSession() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession session = new ChatSession()
                .setType("agent_visualization_app")
                .setExtraData("""
                        {"visualizationApp":{"configs":[{"id":"page:index.json"}]}}
                        """);
        ChatMessagePart card = ChatMessagePart.builder()
                .id("menu-1")
                .type("visualization-app-decision")
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", "visualization_app.confirm_menu",
                        "menu", Map.of("request", Map.of("name", "用户事件入口")))))
                .build();

        orchestrator.prepareParts(
                session, List.of(card), List.of(), "message-1");

        assertThat(card.getMetadata())
                .containsEntry("step", "MENU_CONFIRMATION")
                .containsEntry("validationStatus", "success");
        assertThat(store.loadActive(session, "agent_visualization_app"))
                .get()
                .satisfies(state -> assertThat(state.getContext())
                        .containsEntry("verifiedPersistedConfig", true));
    }

    @Test
    void dashboardMenuCardRequiresBothConfigAndDashboardRecords() {
        WorkflowStateStore store = new WorkflowStateStore();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(store);
        ChatSession configOnly = new ChatSession()
                .setType("agent_visualization_dashboard")
                .setExtraData("""
                        {"visualizationDashboard":{"configs":[{"id":"config-1"}]}}
                        """);
        ChatSession complete = new ChatSession()
                .setType("agent_visualization_dashboard")
                .setExtraData("""
                        {"visualizationDashboard":{
                          "configs":[{"id":"config-1"}],
                          "dashboards":[{"id":"dashboard-1"}]
                        }}
                        """);

        ChatMessagePart blocked = menuCard("visualization-dashboard-decision",
                "visualization_dashboard.confirm_menu");
        orchestrator.prepareParts(
                configOnly, List.of(blocked), List.of(), "message-1");
        assertThat(blocked.getMetadata())
                .containsEntry("step", "BLOCKED")
                .containsEntry("validationStatus", "blocked");

        ChatMessagePart allowed = menuCard("visualization-dashboard-decision",
                "visualization_dashboard.confirm_menu");
        orchestrator.prepareParts(
                complete, List.of(allowed), List.of(), "message-2");
        assertThat(allowed.getMetadata())
                .containsEntry("step", "MENU_CONFIRMATION")
                .containsEntry("validationStatus", "success");
    }

    private ChatMessagePart menuCard(String partType, String action) {
        return ChatMessagePart.builder()
                .id("menu-" + action)
                .type(partType)
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "action", action,
                        "menu", Map.of("request", Map.of("name", "用户事件入口")))))
                .build();
    }

    private void seedDataPushWorkflow(WorkflowStateStore store,
                                      ChatSession session) {
        String now = java.time.Instant.now().toString();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sourceMark", "data-push:workflow-orchestrator");
        context.put("metaRef", Map.of(
                "fileName", "user_event.json",
                "contentDigest", "digest-meta"));
        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("workflow-orchestrator")
                .setWorkflowType("data_push")
                .setAgentType("agent_data_push_service")
                .setObjectType("push_task")
                .setStep(AgentWorkflowStep.PUSH_INPUT_COLLECTION)
                .setStatus("active")
                .setContext(context)
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }
}
