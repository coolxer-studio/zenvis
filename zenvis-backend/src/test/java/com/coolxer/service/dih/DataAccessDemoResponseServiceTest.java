package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DataAccessDemoResponseServiceTest {

    private final DataAccessDemoResponseService service = new DataAccessDemoResponseService();

    @Test
    void metadataWriteUsesDeterministicMcpToolSequence() {
        List<String> calls = new ArrayList<>();
        String metaConfig = (String) ReflectionTestUtils.getField(service, "DEMO_META_CONFIG");
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored -> "[{\"fileName\":\"meta\",\"nodes\":[]}]"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "true"),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(metaConfig))
        );

        Optional<reactor.core.publisher.Flux<String>> routed = service.findResponse(
                demoSession(),
                "chat-1",
                "我已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统",
                new User(),
                context);
        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applyMetaConfigResponse",
                context);

        assertThat(routed).isPresent();
        assertThat(response).contains(
                "元数据配置已通过 MCP 审批",
                "zenvis:meta-config-record",
                "data_access.generate_demo_push_config");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_add",
                "config_apply",
                "config_read");
    }

    @Test
    void rejectedMetadataApprovalStopsDeterministicFlow() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored -> "[{\"fileName\":\"meta\",\"nodes\":[]}]"),
                callback("config_add", calls,
                        ignored -> "{\"status\":\"rejected\",\"message\":\"用户拒绝了MCP工具调用\"}"),
                callback("config_apply", calls, ignored -> "true"),
                callback("config_read", calls, ignored -> "\"{}\"")
        );

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applyMetaConfigResponse",
                context);

        assertThat(response)
                .contains("元数据配置写入失败", "status=rejected", "未生成成功记录")
                .doesNotContain("zenvis:meta-config-record");
        assertThat(calls).containsExactly("config_tree", "config_add");
    }

    @Test
    void pushTaskCreationUsesDeterministicMcpToolSequenceAndLogGate() {
        List<String> calls = new ArrayList<>();
        AtomicInteger listCalls = new AtomicInteger();
        McpToolContext context = toolContext(
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> {
                    if (listCalls.incrementAndGet() == 1) {
                        return "[]";
                    }
                    return """
                            [{"id":12,"source":"SYSTEM","mark":"data-access-demo:user-event:chat-1",
                              "name":"用户事件数据推送服务","description":"demo","status":"running",
                              "config":"[sources.demo]\\ntype = \\"demo_logs\\""}]
                            """;
                }),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> """
                        {"taskId":12,"sourceMark":"data-access-demo:user-event:chat-1",
                         "taskStatus":"running","logType":"system","truncated":false,
                         "content":"=== Vectum run current started ===\\nVector started"}
                        """)
        );

        Optional<reactor.core.publisher.Flux<String>> routed = service.findResponse(
                demoSession(),
                "chat-1",
                "我已确认创建用户事件数据推送服务，请根据上一条确认卡和数据推送配置创建并启动数据推送服务。",
                new User(),
                context);
        String response = ReflectionTestUtils.invokeMethod(
                service,
                "createPushTaskResponse",
                "chat-1",
                context);

        assertThat(routed).isPresent();
        assertThat(response).contains(
                "数据推送服务已通过 MCP 审批创建",
                "zenvis:vectum-task-record",
                "\"status\": \"running\"");
        assertThat(calls).containsExactly(
                "push_task_detect_format",
                "push_task_list_by_source_mark",
                "push_task_create_and_start",
                "push_task_list_by_source_mark",
                "push_task_get_log");
    }

    @Test
    void metadataAndPushConfirmationCardsExplainMcpApproval() {
        String metadata = ReflectionTestUtils.invokeMethod(service, "buildMetadataConfigResponse");
        String push = ReflectionTestUtils.invokeMethod(service, "buildPushConfigResponse");

        assertThat(metadata).contains(
                "\"fileName\":\"user_event.json\"",
                "\"configKind\":\"meta\"",
                "\"label\": \"用户事件数据\"",
                "config_add 和 config_apply 的 MCP 审批");
        assertThat(metadata).doesNotContain("调试信息");
        assertThat(push).contains(
                "\"action\":\"data_access.create_demo_push_task\"",
                "调用 push_task_create_and_start",
                "平台弹出 MCP 审批");
    }

    @Test
    void genericAddConfirmationDoesNotAssumeMetadataFlow() {
        Optional<reactor.core.publisher.Flux<String>> routed = service.findResponse(
                demoSession(),
                "chat-1",
                "我已确认添加配置到系统。",
                new User(),
                toolContext());

        assertThat(routed).isEmpty();
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "deterministic demo tools");
    }

    private ToolCallback callback(String name,
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

    private ChatSession demoSession() {
        return new ChatSession().setMessages(JacksonUtil.toJson(List.of(
                new Message("assistant", "演示配置：user_event.json")
        )));
    }
}
