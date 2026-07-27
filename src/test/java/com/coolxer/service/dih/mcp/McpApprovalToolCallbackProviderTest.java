package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpApprovalToolCallbackProviderTest {

    @Test
    void doesNotForwardInternalRuntimeObjectsAsExternalMcpMetadata() throws Exception {
        AtomicReference<ToolContext> forwardedContext = new AtomicReference<>();
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("jmr_dictionary_lookup")
                        .description("test")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{}";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                forwardedContext.set(toolContext);
                return "{}";
            }
        };
        ToolCallbackProvider delegateProvider = () -> new ToolCallback[]{delegate};
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "external::52::dictionary_lookup",
                McpToolSourceType.EXTERNAL,
                52,
                "jmr",
                "JMR",
                "dictionary_lookup",
                "jmr_dictionary_lookup",
                "JMR 字典",
                "test",
                true,
                false,
                McpToolRiskLevel.LOW,
                McpApprovalPolicy.ALLOW
        );
        McpApprovalService approvalService = mock(McpApprovalService.class);
        when(approvalService.execute(
                any(), anyString(), any(), org.mockito.ArgumentMatchers.<Callable<String>>any()))
                .thenAnswer(invocation ->
                        ((Callable<String>) invocation.getArgument(3)).call());
        McpToolPolicyService policyService = mock(McpToolPolicyService.class);
        McpApprovalToolCallbackProvider provider = new McpApprovalToolCallbackProvider(
                delegateProvider,
                Map.of("jmr_dictionary_lookup", descriptor),
                approvalService,
                policyService
        );
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(16, 2, 12_000, 48_000));
        ToolContext internalContext = new ToolContext(Map.of(
                ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext,
                McpInvocationContext.TOOL_CONTEXT_KEY,
                McpInvocationContext.background("agent_data_analysis")
        ));

        String result = provider.getToolCallbacks()[0].call("{}", internalContext);

        assertThat(result).isEqualTo("{}");
        assertThat(forwardedContext.get()).isNotNull();
        assertThat(forwardedContext.get().getContext()).isEmpty();
        assertThat(internalContext.getContext())
                .containsKey(ToolRuntimeContext.TOOL_CONTEXT_KEY)
                .containsKey(McpInvocationContext.TOOL_CONTEXT_KEY);
    }
}
