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
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DataPushWorkflowExecutionTest {

    @Test
    void approvedPushCandidateUsesStableConflictCreateAndLogSequence() {
        List<String> calls = new ArrayList<>();
        AtomicInteger listCalls = new AtomicInteger();
        String candidate = "[sources.demo]\ntype = \"demo_logs\"";
        String metaContent = "{\"entity\":[{\"name\":\"user_event\"}]}";
        String mark = "data-push:chat-1:probe";
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"user_event.json\",\"nodes\":[]}]"),
                callback("config_read", calls, ignored -> metaContent),
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> {
                    if (listCalls.incrementAndGet() == 1) {
                        return "[]";
                    }
                    return """
                            [{"id":31,"name":"探针推送","description":"probe",
                              "source":"SYSTEM","mark":"data-push:chat-1:probe",
                              "config":"[sources.demo]\\ntype = \\"demo_logs\\"",
                              "status":"running"}]
                            """;
                }),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> """
                        {"taskId":31,"sourceMark":"data-push:chat-1:probe",
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
                        "metaRef", Map.of(
                                "fileName", "user_event.json",
                                "contentDigest", digest(metaContent)),
                        "request", Map.of(
                                "name", "探针推送",
                                "description", "probe"))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataPushWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台已完成锁定 PushTask 执行与读回")
                .contains("\"taskId\":31")
                .contains(mark);
        assertThat(calls).containsExactly(
                "config_tree",
                "config_read",
                "push_task_detect_format",
                "push_task_list_by_source_mark",
                "push_task_create_and_start",
                "push_task_list_by_source_mark",
                "push_task_get_log");
    }

    @Test
    void missingMetaStopsBeforeAnyWriteTool() {
        List<String> calls = new ArrayList<>();
        McpToolContext tools = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_read", calls,
                        ignored -> "{\"entity\":[]}"),
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> "[]"),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> "{}"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.PUSH_FORMAT_CHECK,
                Map.of(
                        "candidate", Map.of(
                                "content", "[sources.demo]",
                                "digest", "digest-2"),
                        "sourceMark", "data-push:chat-1:missing",
                        "metaRef", Map.of(
                                "fileName", "missing_event.json",
                                "contentDigest", "digest-meta"),
                        "request", Map.of("name", "缺失 Meta 任务"))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataPushWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt)
                .contains("平台 Meta 缺失阻断")
                .contains("kind=missing_meta")
                .contains("agent_metadata_config");
        assertThat(calls).containsExactly("config_tree");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get()
                .extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.BLOCKED);
    }

    @Test
    void changedMetaRequiresNewConfirmationWithoutWrites() {
        List<String> calls = new ArrayList<>();
        String currentMeta = "{\"entity\":[{\"name\":\"user_event\"}],\"changed\":true}";
        McpToolContext tools = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"user_event.json\",\"nodes\":[]}]"),
                callback("config_read", calls, ignored -> currentMeta),
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> "[]"),
                callback("push_task_create_and_start", calls, ignored -> "true"));
        WorkflowStateStore store = new WorkflowStateStore();
        ChatSession session = session();
        store.upsert(session, state(
                AgentWorkflowStep.PUSH_EXECUTING,
                Map.of(
                        "candidate", Map.of(
                                "content", "[sources.demo]",
                                "digest", "digest-2"),
                        "sourceMark", "data-push:chat-1:probe",
                        "metaRef", Map.of(
                                "fileName", "user_event.json",
                                "contentDigest", "stale-digest"),
                        "request", Map.of("name", "探针推送"))));
        DihChatApplicationService service = service(store);

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "bootstrapDataPushWorkflow",
                tools,
                "继续",
                session);

        assertThat(prompt).contains("平台检测到 Meta 已变化");
        assertThat(calls).containsExactly("config_tree", "config_read");
        assertThat(store.loadActive(session, DataPushServiceAgent.AGENT_TYPE))
                .get()
                .extracting(AgentWorkflowState::getStep)
                .isEqualTo(AgentWorkflowStep.PUSH_PLAN_CONFIRMATION);
    }

    private AgentWorkflowState state(
            AgentWorkflowStep step,
            Map<String, Object> context) {
        return new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setWorkflowType("data_push")
                .setAgentType(DataPushServiceAgent.AGENT_TYPE)
                .setObjectType("push_task")
                .setStep(step)
                .setStatus("active")
                .setContext(new LinkedHashMap<>(context));
    }

    private ChatSession session() {
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType(DataPushServiceAgent.AGENT_TYPE);
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
                "data push workflow tools");
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

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
