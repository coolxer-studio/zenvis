package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataVisualizationWorkflowExecutionTest {

    @Test
    void approvedDashboardRequestIsCreatedAndReadBackWithoutModelParameters() {
        List<String> calls = new ArrayList<>();
        Map<String, Object> request = Map.of(
                "name", "探针消息看板",
                "code", "probe-message-dashboard",
                "type", "LOW_CODE_PAGE",
                "configIndex", "probe-message",
                "source", "workflow");
        McpToolContext tools = toolContext(
                callback("dashboard_create", calls, ignored -> """
                        {"id":21,"name":"探针消息看板",
                         "code":"probe-message-dashboard","type":"LOW_CODE_PAGE",
                         "config_index":"probe-message","source":"workflow"}
                        """),
                callback("dashboard_view", calls, ignored -> """
                        {"id":21,"name":"探针消息看板",
                         "code":"probe-message-dashboard","type":"LOW_CODE_PAGE",
                         "config_index":"probe-message","source":"workflow"}
                        """));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = new ChatSession()
                .setSessionId("chat-visualization-1")
                .setType(DataVisualizationAgent.AGENT_TYPE);
        session.setId(1);
        AgentWorkflowState workflow = new AgentWorkflowState()
                .setWorkflowId("workflow-visualization-1")
                .setWorkflowType("data_visualization")
                .setAgentType(DataVisualizationAgent.AGENT_TYPE)
                .setObjectType("dashboard")
                .setStep(AgentWorkflowStep.PERSISTING)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(Map.of(
                        "persistencePlan",
                        Map.of("dashboard", Map.of("request", request)))));
        store.upsert(session, workflow);
        DashboardService dashboardService = mock(DashboardService.class);
        when(dashboardService.findAll()).thenReturn(List.of());
        DihChatApplicationService service =
                service(store, dashboardService);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "executeApprovedVisualizationPersistence",
                tools,
                "继续",
                session,
                workflow);

        assertThat(prompt)
                .contains("平台已完成锁定可视化资源写入与读回")
                .contains("\"dashboardId\":21")
                .contains("probe-message-dashboard");
        assertThat(calls).containsExactly(
                "dashboard_create", "dashboard_view");
        assertThat(workflow.getContext())
                .containsEntry("dashboardReadBack", Map.of(
                        "dashboardId", 21,
                        "name", "探针消息看板",
                        "code", "probe-message-dashboard",
                        "dashboardType", "LOW_CODE_PAGE",
                        "url", "",
                        "configIndex", "probe-message",
                        "htmlPath", ""));
    }

    private DihChatApplicationService service(
            WorkflowStateStore store,
            DashboardService dashboardService) {
        DihChatApplicationService service = new DihChatApplicationService(
                null, null, null, null, null, null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                null, null, null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                dashboardService,
                (MenuService) null);
        ReflectionTestUtils.setField(
                service, "workflowStateStore", store);
        return service;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "ordinary data visualization workflow tools");
    }

    private ToolCallback callback(
            String name,
            List<String> calls,
            Function<String, String> result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                calls.add(name);
                return result.apply(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}
