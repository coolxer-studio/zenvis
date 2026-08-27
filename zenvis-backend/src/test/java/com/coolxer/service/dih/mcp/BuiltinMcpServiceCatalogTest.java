package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinMcpServiceCatalogTest {

    @Test
    void exposesFixedServicesAndToolDetailsWithEffectivePolicy() {
        Map<String, McpToolDescriptor> descriptors = new LinkedHashMap<>();
        ToolCallback[] callbacks = BuiltinMcpServiceDefinition.allToolNames().stream()
                .map(toolName -> {
                    BuiltinMcpServiceDefinition service = BuiltinMcpServiceDefinition
                            .findByTool(toolName).orElseThrow();
                    descriptors.put(toolName, new McpToolDescriptor(
                            McpToolDescriptor.localKey(toolName),
                            McpToolSourceType.LOCAL,
                            null,
                            service.code(),
                            service.serviceName(),
                            toolName,
                            toolName,
                            toolName,
                            "description-" + toolName,
                            true,
                            false,
                            McpToolRiskLevel.LOW,
                            McpApprovalPolicy.ALLOW
                    ));
                    return new FakeToolCallback(toolName);
                })
                .toArray(ToolCallback[]::new);
        McpToolPolicyService policyService = mock(McpToolPolicyService.class);
        when(policyService.register(any())).thenAnswer(invocation -> {
            McpToolDescriptor descriptor = invocation.getArgument(0);
            return new McpToolPolicyConfig()
                    .setToolKey(descriptor.toolKey())
                    .setDefaultPolicy(descriptor.defaultPolicy())
                    .setConfiguredPolicy(McpApprovalPolicy.ASK);
        });
        when(policyService.findByToolKey(any())).thenAnswer(invocation -> {
            String toolKey = invocation.getArgument(0);
            return Optional.of(new McpToolPolicyConfig()
                    .setToolKey(toolKey)
                    .setDefaultPolicy(McpApprovalPolicy.ALLOW)
                    .setConfiguredPolicy(McpApprovalPolicy.ASK));
        });
        McpApprovalToolCallbackProvider provider = new McpApprovalToolCallbackProvider(
                ToolCallbackProvider.from(callbacks),
                descriptors,
                mock(McpApprovalService.class),
                policyService
        );
        BuiltinMcpServiceCatalog catalog = new BuiltinMcpServiceCatalog(provider, policyService);

        assertThat(catalog.listServices())
                .extracting("code", "toolCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("retrieval", 22),
                        org.assertj.core.groups.Tuple.tuple("entity", 5),
                        org.assertj.core.groups.Tuple.tuple("config", 7),
                        org.assertj.core.groups.Tuple.tuple("push-task", 6),
                        org.assertj.core.groups.Tuple.tuple("visualization", 21),
                        org.assertj.core.groups.Tuple.tuple("analysis-task", 16));
        assertThat(catalog.listTools("retrieval"))
                .hasSize(22)
                .allSatisfy(tool -> {
                    assertThat(tool.getServerCode()).isEqualTo("retrieval");
                    assertThat(tool.getToolKey()).isEqualTo("local::" + tool.getName());
                    assertThat(tool.getInputSchema()).isInstanceOf(Map.class);
                    assertThat(tool.getEffectiveApprovalPolicy()).isEqualTo(McpApprovalPolicy.ASK);
                });
        assertThatThrownBy(() -> catalog.listTools("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不存在");
    }

    private record FakeToolCallback(String name) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description("description-" + name)
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return "{}";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }
}
