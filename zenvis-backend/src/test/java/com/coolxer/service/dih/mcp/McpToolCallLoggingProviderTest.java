package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolCallLoggingProviderTest {

    @Test
    void stopsAfterTwoIdenticalExecutionFailures() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback callback = failingCallback(calls);
        ToolCallback wrapped = new McpToolCallLoggingProvider(
                ToolCallbackProvider.from(callback),
                ignored -> {
                }).getToolCallbacks()[0];
        ToolRuntimeContext runtimeContext = runtimeContext(2);
        ToolContext toolContext = new ToolContext(Map.of(
                ToolRuntimeContext.TOOL_CONTEXT_KEY,
                runtimeContext));

        assertThatThrownBy(() -> wrapped.call(
                "{\"componentType\":\"file\"}",
                toolContext))
                .hasMessageContaining("HTTP 400");
        assertThat(runtimeContext.stopRequested()).isFalse();

        assertThatThrownBy(() -> wrapped.call(
                "{\"componentType\":\"file\"}",
                toolContext))
                .hasMessageContaining("HTTP 400");
        assertThat(runtimeContext.stopRequested()).isTrue();
        assertThat(runtimeContext.stopReason()).isEqualTo("repeated_tool_failure");
        assertThat(calls).hasValue(2);

        assertThatThrownBy(() -> wrapped.call(
                "{\"componentType\":\"file\"}",
                toolContext))
                .hasMessageContaining("工具执行已停止");
        assertThat(calls).hasValue(2);
    }

    @Test
    void treatsDifferentArgumentsAsDifferentFailures() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback wrapped = new McpToolCallLoggingProvider(
                ToolCallbackProvider.from(failingCallback(calls)),
                ignored -> {
                }).getToolCallbacks()[0];
        ToolRuntimeContext runtimeContext = runtimeContext(2);
        ToolContext toolContext = new ToolContext(Map.of(
                ToolRuntimeContext.TOOL_CONTEXT_KEY,
                runtimeContext));

        assertThatThrownBy(() -> wrapped.call(
                "{\"componentType\":\"file\"}",
                toolContext));
        assertThatThrownBy(() -> wrapped.call(
                "{\"componentType\":\"demo_logs\"}",
                toolContext));

        assertThat(runtimeContext.stopRequested()).isFalse();
        assertThat(calls).hasValue(2);
    }

    private ToolRuntimeContext runtimeContext(int maxRepeatedFailures) {
        SkillRuntimeLimitsVo limits = new SkillRuntimeLimitsVo();
        limits.setMaxRepeatedFailures(maxRepeatedFailures);
        return new ToolRuntimeContext(limits);
    }

    private ToolCallback failingCallback(AtomicInteger calls) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("push_task_get_log")
                        .description("读取任务日志")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                calls.incrementAndGet();
                throw new IllegalStateException(
                        "Vectum 日志接口返回 HTTP 400");
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}
