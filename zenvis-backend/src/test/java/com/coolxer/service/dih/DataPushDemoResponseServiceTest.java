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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DataPushDemoResponseServiceTest {

    private final DataPushDemoResponseService service =
            new DataPushDemoResponseService();

    @Test
    void missingMetaStopsBeforeAnyWriteTool() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_read", calls, ignored -> "{}"),
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> "[]"),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> "{}")
        );

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "buildPushConfigResponse",
                "chat-1",
                context);

        assertThat(response)
                .contains("missing_meta", "前往元数据配置", "agent_metadata_config")
                .doesNotContain("push_task_create_and_start");
        assertThat(calls).containsExactly("config_tree");
    }

    @Test
    void pushTaskCreationUsesDeterministicMcpToolSequenceAndLogGate() {
        List<String> calls = new ArrayList<>();
        AtomicInteger listCalls = new AtomicInteger();
        String metaContent = resource("demo/metadata-config/user-event-meta.json");
        McpToolContext context = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"user_event.json\",\"nodes\":[]}]"),
                callback("config_read", calls, ignored -> metaContent),
                callback("push_task_detect_format", calls, ignored -> "\"toml\""),
                callback("push_task_list_by_source_mark", calls, ignored -> {
                    if (listCalls.incrementAndGet() == 1) {
                        return "[]";
                    }
                    return """
                            [{"id":12,"source":"SYSTEM","mark":"data-push-demo:user-event:chat-1",
                              "name":"用户事件数据推送服务","description":"demo","status":"running",
                              "config":"[sources.demo]\\ntype = \\"demo_logs\\""}]
                            """;
                }),
                callback("push_task_create_and_start", calls, ignored -> "true"),
                callback("push_task_get_log", calls, ignored -> """
                        {"taskId":12,"sourceMark":"data-push-demo:user-event:chat-1",
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
                "\"status\": \"running\"")
                .doesNotContain("zenvis:meta-config-record", "config_add", "config_apply");
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
    void pushConfirmationCardRequiresRealMetaReference() {
        List<String> calls = new ArrayList<>();
        String metaContent = resource("demo/metadata-config/user-event-meta.json");
        McpToolContext context = toolContext(
                callback("config_tree", calls,
                        ignored -> "[{\"fileName\":\"user_event.json\",\"nodes\":[]}]"),
                callback("config_read", calls, ignored -> metaContent)
        );

        String push = ReflectionTestUtils.invokeMethod(
                service, "buildPushConfigResponse", "chat-1", context);

        assertThat(push).contains(
                "\"action\":\"data_push.confirm_plan\"",
                "\"metaRef\":{\"fileName\":\"user_event.json\"",
                "\"contentDigest\":",
                "push_task_create_and_start",
                "平台弹出 MCP 审批");
        assertThat(calls).containsExactly("config_tree", "config_read");
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "data push demo tools");
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

    private String resource(String path) {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
