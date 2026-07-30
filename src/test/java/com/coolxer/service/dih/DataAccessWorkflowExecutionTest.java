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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DataAccessWorkflowExecutionTest {

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
                "bootstrapDataAccessWorkflow",
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
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataAccessWorkflow",
                tools,
                "请根据以下字段生成并添加元数据配置：event_id、event_type、server_time",
                session);

        assertThat(prompt)
                .contains("平台已执行 Meta 配置树 MCP")
                .contains("config_tree")
                .contains("existing.json");
        assertThat(calls).containsExactly("config_tree");
    }

    @Test
    void explicitDirectPushTurnDoesNotQueryMetaTree() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataAccessWorkflow",
                tools,
                "请直接创建并启动 Vectum 数据推送服务，不需要元数据",
                session);

        assertThat(prompt)
                .contains("明确指向数据推送服务")
                .contains("允许跳过 Meta 配置发现");
        assertThat(calls).isEmpty();
    }

    @Test
    void approvedPushCandidateUsesStableConflictCreateAndLogSequence() {
        List<String> calls = new ArrayList<>();
        AtomicInteger listCalls = new AtomicInteger();
        String candidate = "[sources.demo]\ntype = \"demo_logs\"";
        String mark = "data-access:chat-1:probe";
        McpToolContext tools = toolContext(
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> {
                    if (listCalls.incrementAndGet() == 1) {
                        return "[]";
                    }
                    return """
                            [{"id":31,"name":"探针推送","description":"probe",
                              "source":"SYSTEM","mark":"data-access:chat-1:probe",
                              "config":"[sources.demo]\\ntype = \\"demo_logs\\"",
                              "status":"running"}]
                            """;
                }),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> """
                        {"taskId":31,"sourceMark":"data-access:chat-1:probe",
                         "taskStatus":"running","content":"=== Vectum run current started ===\\nstarted"}
                        """));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.PUSH_FORMAT_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "kind", "push_task",
                                "content", candidate,
                                "digest", "digest-2"),
                        "sourceMark", mark,
                        "request", Map.of(
                                "name", "探针推送",
                                "description", "probe"))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataAccessWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成锁定 PushTask 执行与读回")
                .contains("\"taskId\":31")
                .contains(mark);
        assertThat(calls).containsExactly(
                "push_task_detect_format",
                "push_task_list_by_source_mark",
                "push_task_create_and_start",
                "push_task_list_by_source_mark",
                "push_task_get_log");
    }

    private AgentWorkflowState state(
            AgentWorkflowStep step,
            Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("data_access")
                .setAgentType(DataAccessAgent.AGENT_TYPE)
                .setObjectType("data_access")
                .setStep(step)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType(DataAccessAgent.AGENT_TYPE);
        session.setId(1);
        return session;
    }

    private DihChatApplicationService service(WorkflowStateStore store) {
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
                (DashboardService) null,
                (MenuService) null);
        ReflectionTestUtils.setField(
                service, "workflowStateStore", store);
        return service;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "ordinary data access workflow tools");
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
