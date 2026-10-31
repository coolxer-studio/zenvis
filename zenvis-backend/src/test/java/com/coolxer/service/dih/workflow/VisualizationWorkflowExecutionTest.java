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
        assertThat(app.supportsPart(part("config"))).isTrue();
        assertThat(app.supportsPart(part("visualization-chart-preview"))).isFalse();
        assertThat(app.supportsPart(part("visualization-dashboard-record"))).isFalse();

        assertThat(dashboard.supportsPart(part("visualization-dashboard-decision")))
                .isTrue();
        assertThat(dashboard.supportsPart(part("visualization-dashboard-record")))
                .isTrue();
        assertThat(dashboard.supportsPart(part("config"))).isTrue();
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
    void appMenuConfirmationRequiresAReadBackVerifiedConfigRecord() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.ARTIFACT_READY);
        ChatMessagePart confirmMenu = decision(
                VisualizationAgentTypes.APP,
                "confirm_menu",
                Map.of("menu", Map.of("request", Map.of("name", "用户事件入口"))));

        assertThat(app.resolveStep(state, confirmMenu, confirmMenu.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);

        ChatMessagePart configRecord = record(
                "visualization-app-record", "config");
        app.updateContext(state, configRecord, configRecord.getMetadata());

        assertThat(app.resolveStep(state, confirmMenu, confirmMenu.getMetadata()))
                .isEqualTo(AgentWorkflowStep.MENU_CONFIRMATION);
    }

    @Test
    void dashboardMenuConfirmationRequiresBothConfigAndDashboardRecords() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.DASHBOARD, AgentWorkflowStep.ARTIFACT_READY);
        ChatMessagePart confirmMenu = decision(
                VisualizationAgentTypes.DASHBOARD,
                "confirm_menu",
                Map.of("menu", Map.of("request", Map.of("name", "用户事件看板入口"))));
        ChatMessagePart configRecord = record(
                "visualization-dashboard-record", "config");

        dashboard.updateContext(state, configRecord, configRecord.getMetadata());
        assertThat(dashboard.resolveStep(state, confirmMenu, confirmMenu.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);

        ChatMessagePart dashboardRecord = record(
                "visualization-dashboard-record", "dashboard");
        dashboard.updateContext(state, dashboardRecord, dashboardRecord.getMetadata());
        assertThat(dashboard.resolveStep(state, confirmMenu, confirmMenu.getMetadata()))
                .isEqualTo(AgentWorkflowStep.MENU_CONFIRMATION);
    }

    @Test
    void rejectingAMenuKeepsTheWorkflowVerifiedInsteadOfCancellingPersistedArtifacts() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.MENU_CONFIRMATION);

        assertThat(app.transition(state, "reject"))
                .isEqualTo(AgentWorkflowStep.VERIFIED);
    }

    @Test
    void revisingAMenuOnlyReopensMenuConfirmation() {
        AgentWorkflowState state = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.MENU_CONFIRMATION);

        assertThat(app.transition(state, "revise"))
                .isEqualTo(AgentWorkflowStep.MENU_CONFIRMATION);
        assertThat(app.continuation(state, "revise").get("request"))
                .asString()
                .contains("只重新生成菜单确认卡")
                .contains("不得重复写入");
    }

    @Test
    void persistencePlanRejectsMenusAndDashboardResourcesForAppWorkflow() {
        AgentWorkflowState appState = state(
                VisualizationAgentTypes.APP, AgentWorkflowStep.ARTIFACT_READY);
        appState.getContext().put("persistenceCandidate",
                Map.of("content", "{\"type\":\"page\"}"));

        ChatMessagePart mixed = decision(
                VisualizationAgentTypes.APP,
                "confirm_plan",
                Map.of(
                        "configType", "user-event-page",
                        "fileName", "index.json",
                        "menu", Map.of("request", Map.of("name", "用户事件入口"))));
        assertThat(app.resolveStep(appState, mixed, mixed.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);

        ChatMessagePart dashboardResource = decision(
                VisualizationAgentTypes.APP,
                "confirm_plan",
                Map.of(
                        "configType", "user-event-page",
                        "fileName", "index.json",
                        "dashboard", Map.of("request", Map.of(
                                "name", "错误看板",
                                "code", "wrong-dashboard"))));
        assertThat(app.resolveStep(appState, dashboardResource,
                dashboardResource.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
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
