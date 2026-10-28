package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.VisualizationAppAgent;
import com.coolxer.service.dih.agent.VisualizationChartAgent;
import com.coolxer.service.dih.agent.VisualizationDashboardAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
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
import com.coolxer.utils.JacksonUtil;
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

class MetadataConfigWorkflowExecutionTest {

    @Test
    void approvedMetaCandidateUsesFixedWriteAndReadBackSequence() {
        List<String> calls = new ArrayList<>();
        String candidate = """
                {"entity":[{"name":"probe_message"}],"attribute":[],"operator":[]}
                """;
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "true"),
                callback("config_read", calls,
                        ignored -> JacksonUtil.toJson(candidate)));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PREWRITE_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "meta",
                                "content", candidate,
                                "digest", "digest-1"),
                        "fileName", "probe_message.json")));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成锁定 Meta 配置写入与读回")
                .contains("probe_message.json");
        assertThat(calls).containsExactly(
                "config_tree", "config_add", "config_apply", "config_read");
    }

    @Test
    void firstOrdinaryMetaTurnQueriesConfigTreeBeforeGeneratingDecision() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"existing.json\"}]"));
        DihChatApplicationService service = service(new WorkflowStateStore());

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapMetadataConfigWorkflow",
                tools,
                "请根据以下字段生成并添加元数据配置：event_id、event_type、server_time",
                session());

        assertThat(prompt)
                .contains("平台已执行 Meta 配置树 MCP")
                .contains("config_tree")
                .contains("existing.json")
                .doesNotContain("push_task_");
        assertThat(calls).containsExactly("config_tree");
    }

    @Test
    void metadataWorkflowUsesOnlyMetadataSteps() {
        List<String> calls = new ArrayList<>();
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.META_PLAN_CONFIRMATION,
                Map.of(
                        "candidate", Map.of("content", "{}", "digest", "digest-1"),
                        "fileName", "probe_message.json")));

        String prompt = ReflectionTestUtils.invokeMethod(
                service(store),
                "bootstrapMetadataConfigWorkflow",
                toolContext(callback("config_tree", calls, ignored -> "[]")),
                "继续",
                session);

        assertThat(prompt).contains("不得调用任何 push_task_* 工具");
        assertThat(calls).isEmpty();
    }

    private AgentWorkflowState state(
            AgentWorkflowStep step,
            Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("metadata_config")
                .setAgentType(MetadataConfigAgent.AGENT_TYPE)
                .setObjectType("meta_config")
                .setStep(step)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType(MetadataConfigAgent.AGENT_TYPE);
        session.setId(1);
        return session;
    }

    private DihChatApplicationService service(WorkflowStateStore store) {
        DihChatApplicationService service = new DihChatApplicationService(
                null, null, null, null, null, null, null, null,
                null,
                (ReportAgent) null,
                (MetadataConfigAgent) null,
                (DataPushServiceAgent) null,
                (VisualizationChartAgent) null,
                (VisualizationAppAgent) null,
                (VisualizationDashboardAgent) null,
                null, null, null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null);
        ReflectionTestUtils.setField(service, "workflowStateStore", store);
        return service;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "metadata config workflow tools");
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
