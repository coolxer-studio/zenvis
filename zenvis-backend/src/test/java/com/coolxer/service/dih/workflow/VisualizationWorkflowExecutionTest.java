package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationWorkflowExecutionTest {

    private final VisualizationChartWorkflowDefinition chart =
            new VisualizationChartWorkflowDefinition();
    private final VisualizationAppWorkflowDefinition app =
            new VisualizationAppWorkflowDefinition();
    private final VisualizationDashboardWorkflowDefinition dashboard =
            new VisualizationDashboardWorkflowDefinition();

    @Test
    void eachVisualizationWorkflowAcceptsOnlyItsOwnDecisionAndRecordProtocols() {
        assertThat(chart.supportsPart(part("visualization-chart-decision")))
                .isTrue();
        assertThat(chart.supportsPart(part("visualization-chart-record")))
                .isTrue();
        assertThat(chart.supportsPart(part("visualization-chart-preview")))
                .isTrue();
        assertThat(chart.supportsPart(part("visualization-app-record")))
                .isFalse();
        assertThat(chart.supportsPart(part("visualization-dashboard-record")))
                .isFalse();
        assertThat(chart.supportsPart(part("config"))).isFalse();

        assertThat(app.supportsPart(part("visualization-app-decision"))).isTrue();
        assertThat(app.supportsPart(part("visualization-app-record"))).isTrue();
        assertThat(app.supportsPart(part("visualization-app-preview"))).isTrue();
        assertThat(app.supportsPart(part("config"))).isFalse();
        assertThat(app.supportsPart(part("visualization-chart-preview"))).isFalse();
        assertThat(app.supportsPart(part("visualization-dashboard-record"))).isFalse();

        assertThat(dashboard.supportsPart(part("visualization-dashboard-decision")))
                .isTrue();
        assertThat(dashboard.supportsPart(part("visualization-dashboard-record")))
                .isTrue();
        assertThat(dashboard.supportsPart(part("visualization-dashboard-preview")))
                .isTrue();
        assertThat(dashboard.supportsPart(part("config"))).isFalse();
        assertThat(dashboard.supportsPart(part("visualization-app-record"))).isFalse();
        assertThat(dashboard.supportsPart(part("visualization-chart-record"))).isFalse();
    }

    @Test
    void legacyVisualizationProtocolsAreNotAccepted() {
        for (String legacy : new String[]{
                "visualization-config-record",
                "dashboard-config-record",
                "menu-config-record",
                "data-visualization-decision"}) {
            assertThat(chart.supportsPart(part(legacy))).isFalse();
            assertThat(app.supportsPart(part(legacy))).isFalse();
            assertThat(dashboard.supportsPart(part(legacy))).isFalse();
        }
    }

    @Test
    void chartRemainsReadOnlyAndOnlyItCanAddToTheChartLibrary() {
        AgentWorkflowState chartState = state(
                VisualizationAgentTypes.CHART, AgentWorkflowStep.ARTIFACT_READY);

        assertThat(chart.isAllowedForState(
                AgentWorkflowStep.ARTIFACT_READY, "add_to_library")).isTrue();
        assertThat(app.isAllowedForState(
                AgentWorkflowStep.ARTIFACT_READY, "add_to_library")).isFalse();
        assertThat(dashboard.isAllowedForState(
                AgentWorkflowStep.ARTIFACT_READY, "add_to_library")).isFalse();
        assertThat(chart.isAllowedForState(
                AgentWorkflowStep.PERSIST_CONFIRMATION, "approve")).isFalse();
        ChatMessagePart confirmPlan = part("visualization-chart-decision", Map.of(
                "action", "visualization_chart.confirm_plan"));
        assertThat(chart.resolveStep(chartState, confirmPlan, confirmPlan.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void entityInTitleDoesNotOverrideTheQueryPlanConfirmationAction() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.CHART, AgentWorkflowStep.ATTRIBUTE_META);
        ChatMessagePart confirmPlan = decision(
                VisualizationAgentTypes.CHART,
                "confirm_query_plan",
                Map.of(
                        "title", "确认实体与字段查询方案",
                        "planId", "plan-entity-title",
                        "query", Map.of(
                                "tool", "entity_distribution",
                                "request", Map.of(
                                        "entity", "user_event",
                                        "dimension", "event_type"))));

        assertThat(chart.resolveStep(state, confirmPlan, confirmPlan.getMetadata()))
                .isEqualTo(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION);
    }

    @Test
    void appLegacyPlanOverwriteAndMenuActionsAreBlocked() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.APP_DISCOVERY);
        for (String legacy : new String[]{"confirm_plan", "confirm_overwrite", "confirm_menu"}) {
            ChatMessagePart card = decision(
                    VisualizationAgentTypes.APP,
                    legacy,
                    Map.of("menu", Map.of("request", Map.of("name", "用户事件入口"))));
            assertThat(app.resolveStep(state, card, card.getMetadata()))
                    .isEqualTo(AgentWorkflowStep.BLOCKED);
        }
    }

    @Test
    void dashboardPreviewRequiresLockedDigestsAndPublishConfirmation() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.DASHBOARD, AgentWorkflowStep.DASHBOARD_VALIDATING);
        state.getContext().put("candidateDigest", "candidate-1");
        state.getContext().put("validationDigest", "validation-1");
        ChatMessagePart preview = part("visualization-dashboard-preview", Map.of(
                "validationStatus", "validated",
                "candidateDigest", "candidate-1",
                "validationDigest", "validation-1"));
        ChatMessagePart confirm = decision(
                VisualizationAgentTypes.DASHBOARD, "confirm_publish", Map.of(
                        "candidateDigest", "candidate-1",
                        "validationDigest", "validation-1"));

        assertThat(dashboard.resolveStep(state, preview, preview.getMetadata()))
                .isEqualTo(AgentWorkflowStep.DASHBOARD_VALIDATING);
        assertThat(dashboard.resolveStep(state, confirm, confirm.getMetadata()))
                .isEqualTo(AgentWorkflowStep.DASHBOARD_VALIDATING);
        assertThat(dashboard.validateAction(state, confirm, confirm.getMetadata(), "approve"))
                .isBlank();
        assertThat(dashboard.transition(state, "approve"))
                .isEqualTo(AgentWorkflowStep.DASHBOARD_APPLYING);

        Map<String, Object> stale = new LinkedHashMap<>(preview.getMetadata());
        stale.put("validationDigest", "validation-old");
        ChatMessagePart stalePreview = part("visualization-dashboard-preview", stale);
        assertThat(dashboard.resolveStep(state, stalePreview, stalePreview.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void dashboardPlanAndPublishCanOnlyUseTheNewLifecycleActions() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.DASHBOARD, AgentWorkflowStep.DASHBOARD_DISCOVERY);
        ChatMessagePart plan = decision(
                VisualizationAgentTypes.DASHBOARD,
                "confirm_dashboard_plan",
                Map.of("dashboardId", "user-event-dashboard", "specDigest", "spec-1"));
        assertThat(dashboard.resolveStep(state, plan, plan.getMetadata()))
                .isEqualTo(AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION);

        state.setStep(AgentWorkflowStep.DASHBOARD_DISCOVERY);
        for (String legacy : new String[]{"confirm_plan", "confirm_overwrite", "confirm_menu"}) {
            ChatMessagePart old = decision(
                    VisualizationAgentTypes.DASHBOARD,
                    legacy,
                    Map.of("menu", Map.of("request", Map.of("name", "历史入口"))));
            assertThat(dashboard.resolveStep(state, old, old.getMetadata()))
                    .isEqualTo(AgentWorkflowStep.BLOCKED);
        }
    }

    @Test
    void appPreviewRequiresTheLockedDigestsAndOpensPublishConfirmation() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.APP_VALIDATING);
        state.getContext().put("candidateDigest", "candidate-1");
        state.getContext().put("validationDigest", "validation-1");

        ChatMessagePart preview = part("visualization-app-preview", Map.of(
                "validationStatus", "validated",
                "candidateDigest", "candidate-1",
                "validationDigest", "validation-1"));

        assertThat(app.resolveStep(state, preview, preview.getMetadata()))
                .isEqualTo(AgentWorkflowStep.PERSIST_CONFIRMATION);

        Map<String, Object> stale = new LinkedHashMap<>(preview.getMetadata());
        stale.put("validationDigest", "validation-old");
        ChatMessagePart stalePreview = part("visualization-app-preview", stale);
        assertThat(app.resolveStep(state, stalePreview, stalePreview.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void partialMenuRecordKeepsRetryStateInsteadOfClaimingVerified() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.PARTIAL_MENU);
        ChatMessagePart record = part("visualization-app-record", Map.of(
                "recordKind", "application",
                "publishStatus", "partial_menu",
                "status", "partial_menu"));

        assertThat(app.resolveStep(state, record, record.getMetadata()))
                .isEqualTo(AgentWorkflowStep.PARTIAL_MENU);
        assertThat(app.allowedActions(AgentWorkflowStep.PARTIAL_MENU, record.getMetadata()))
                .contains("retry", "revise", "reject");
    }

    @Test
    void menuRetryMustUseTheWorkflowLockedDigests() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.PARTIAL_MENU);
        state.getContext().put("candidateDigest", "candidate-1");
        state.getContext().put("validationDigest", "validation-1");
        ChatMessagePart retry = decision(VisualizationAgentTypes.APP, "retry_menu", Map.of(
                "candidateDigest", "candidate-1",
                "validationDigest", "validation-1"));
        ChatMessagePart stale = decision(VisualizationAgentTypes.APP, "retry_menu", Map.of(
                "candidateDigest", "candidate-1",
                "validationDigest", "validation-old"));

        assertThat(app.resolveStep(state, retry, retry.getMetadata()))
                .isEqualTo(AgentWorkflowStep.PARTIAL_MENU);
        assertThat(app.validateAction(state, retry, retry.getMetadata(), "retry"))
                .isBlank();
        assertThat(app.resolveStep(state, stale, stale.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(app.validateAction(state, stale, stale.getMetadata(), "retry"))
                .contains("锁定");
    }

    @Test
    void rejectingPartialMenuKeepsPublishedConfigurationAndRetryState() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.PARTIAL_MENU);

        assertThat(app.transition(state, "reject"))
                .isEqualTo(AgentWorkflowStep.PARTIAL_MENU);
    }

    @Test
    void appWorkflowRejectsLegacyConfigResources() {
        AgentWorkflowState appState = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.ARTIFACT_READY);
        appState.getContext().put("persistenceCandidate",
                Map.of("content", "{\"type\":\"page\"}"));

        ChatMessagePart mixed = decision(VisualizationAgentTypes.APP, "confirm_plan", Map.of(
                "configType", "user-event-page",
                "fileName", "index.json",
                "menu", Map.of("request", Map.of("name", "用户事件入口"))));
        assertThat(app.resolveStep(appState, mixed, mixed.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(app.supportsPart(part("config"))).isFalse();
    }

    @Test
    void missingMetaAlwaysBlocksWithoutOfferingDomainActions() {
        ChatMessagePart missing = ChatMessagePart.builder()
                .id("missing-1")
                .type(VisualizationAgentTypes.decisionPartType(
                        VisualizationAgentTypes.CHART))
                .status("pending")
                .metadata(new LinkedHashMap<>(Map.of(
                        "kind", "missing_meta",
                        "handoff", Map.of(
                                "targetAgentType", "agent_metadata_config",
                                "prompt", "请补齐 user_event Meta"))))
                .build();

        AgentWorkflowState state = state(
                VisualizationAgentTypes.CHART, AgentWorkflowStep.ENTITY_META);
        assertThat(chart.resolveStep(state, missing, missing.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(chart.allowedActions(AgentWorkflowStep.BLOCKED, missing.getMetadata()))
                .isEmpty();
    }

    private AgentWorkflowState state(String agentType, AgentWorkflowStep step) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-" + agentType)
                .setAgentType(agentType)
                .setWorkflowType(visualizationType(agentType))
                .setStep(step)
                .setStatus("active")
                .setContext(new LinkedHashMap<>());
    }

    private String visualizationType(String agentType) {
        if (VisualizationAgentTypes.isChart(agentType)) {
            return "visualization_chart";
        }
        if (VisualizationAgentTypes.isApp(agentType)) {
            return "visualization_app";
        }
        return "visualization_dashboard";
    }

    private ChatMessagePart part(String type) {
        return ChatMessagePart.builder()
                .id(type)
                .type(type)
                .status("pending")
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private ChatMessagePart part(String type, Map<String, Object> metadata) {
        return ChatMessagePart.builder()
                .id(type)
                .type(type)
                .status("pending")
                .metadata(new LinkedHashMap<>(metadata))
                .build();
    }

    private ChatMessagePart decision(String agentType,
                                     String action,
                                     Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        metadata.put("action", VisualizationAgentTypes.actionPrefix(agentType)
                + "." + action);
        return part(VisualizationAgentTypes.decisionPartType(agentType), metadata);
    }

    private ChatMessagePart record(String type, String recordKind) {
        return part(type, Map.of("recordKind", recordKind));
    }
}
