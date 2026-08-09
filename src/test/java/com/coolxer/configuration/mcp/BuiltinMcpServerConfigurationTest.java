package com.coolxer.configuration.mcp;

import com.coolxer.service.dih.mcp.BuiltinMcpServiceDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinMcpServerConfigurationTest {

    @Test
    void registersOnlyGroupedSseAndMessageRoutes() {
        ToolCallback[] callbacks = BuiltinMcpServiceDefinition.allToolNames().stream()
                .map(FakeToolCallback::new)
                .toArray(ToolCallback[]::new);
        try (BuiltinMcpServerConfiguration.BuiltinMcpServerRegistry registry =
                     new BuiltinMcpServerConfiguration.BuiltinMcpServerRegistry(
                             ToolCallbackProvider.from(callbacks),
                             "test", Duration.ofSeconds(5), Duration.ofSeconds(30))) {
            for (BuiltinMcpServiceDefinition service : BuiltinMcpServiceDefinition.orderedValues()) {
                assertThat(matches(registry, "GET", service.sseEndpoint())).isTrue();
                assertThat(matches(registry, "POST", service.messageEndpoint())).isTrue();
                assertThat(registry.toolNames(service.code()))
                        .containsExactlyElementsOf(service.toolNames());
            }
            assertThat(matches(registry, "GET", "/sse")).isFalse();
            assertThat(matches(registry, "POST", "/mcp/message")).isFalse();
            assertThat(matches(registry, "POST", "/mcp/retrieval/message")).isTrue();
            assertThat(matches(registry, "POST", "/mcp/entity/message")).isTrue();
        }
    }

    private boolean matches(BuiltinMcpServerConfiguration.BuiltinMcpServerRegistry registry,
                            String method,
                            String path) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest(method, path);
        ServerRequest request = ServerRequest.create(servletRequest, List.of());
        return registry.routerFunction().route(request).isPresent();
    }

    private record FakeToolCallback(String name) implements ToolCallback {

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
            return "{}";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }
}
