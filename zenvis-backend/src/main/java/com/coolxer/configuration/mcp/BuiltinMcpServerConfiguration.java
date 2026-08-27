package com.coolxer.configuration.mcp;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.dih.mcp.BuiltinMcpServiceDefinition;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates one MCP transport and server for every built-in service group.
 */
@Slf4j
@Configuration
public class BuiltinMcpServerConfiguration {

    @Bean(destroyMethod = "close")
    public BuiltinMcpServerRegistry builtinMcpServerRegistry(
            @Qualifier("retrievalToolCallbackProvider") ToolCallbackProvider toolCallbackProvider,
            @Value("${app.ai.mcp.builtin-server.version:1.0.0}") String version,
            @Value("${app.ai.mcp.builtin-server.request-timeout:20s}") Duration requestTimeout,
            @Value("${app.ai.mcp.builtin-server.keep-alive-interval:30s}") Duration keepAliveInterval) {
        return new BuiltinMcpServerRegistry(
                toolCallbackProvider,
                version,
                requestTimeout,
                keepAliveInterval
        );
    }

    @Bean("builtinMcpServerRouterFunction")
    public RouterFunction<ServerResponse> builtinMcpServerRouterFunction(
            BuiltinMcpServerRegistry registry) {
        return registry.routerFunction();
    }

    static final class BuiltinMcpServerRegistry implements AutoCloseable {

        private final List<WebMvcSseServerTransportProvider> transports = new ArrayList<>();

        private final Map<String, McpSyncServer> servers = new LinkedHashMap<>();

        private final RouterFunction<ServerResponse> routerFunction;

        BuiltinMcpServerRegistry(ToolCallbackProvider toolCallbackProvider,
                                 String version,
                                 Duration requestTimeout,
                                 Duration keepAliveInterval) {
            Map<String, ToolCallback> callbacksByName = callbacksByName(toolCallbackProvider);
            validateCatalog(callbacksByName.keySet());

            List<RouterFunction<ServerResponse>> routes = new ArrayList<>();
            JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JacksonConfig.OBJECT_MAPPER);
            for (BuiltinMcpServiceDefinition service : BuiltinMcpServiceDefinition.orderedValues()) {
                WebMvcSseServerTransportProvider transport =
                        WebMvcSseServerTransportProvider.builder()
                                .jsonMapper(jsonMapper)
                                .sseEndpoint(service.sseEndpoint())
                                .messageEndpoint(service.messageEndpoint())
                                .keepAliveInterval(keepAliveInterval)
                                .build();
                List<ToolCallback> callbacks = service.toolNames().stream()
                        .map(callbacksByName::get)
                        .toList();
                McpSyncServer server = McpServer.sync(transport)
                        .serverInfo(service.serverName(), version)
                        .instructions(service.description())
                        .capabilities(McpSchema.ServerCapabilities.builder()
                                .tools(false)
                                .build())
                        .requestTimeout(requestTimeout)
                        .tools(McpToolUtils.toSyncToolSpecification(callbacks))
                        .build();
                transports.add(transport);
                servers.put(service.code(), server);
                routes.add(transport.getRouterFunction());
                log.info("内置 MCP 服务已注册: code={}, tools={}, sse={}, message={}",
                        service.code(), callbacks.size(), service.sseEndpoint(), service.messageEndpoint());
            }
            this.routerFunction = routes.stream()
                    .reduce(RouterFunction::and)
                    .orElseGet(() -> RouterFunctions.route().build());
        }

        RouterFunction<ServerResponse> routerFunction() {
            return routerFunction;
        }

        List<String> toolNames(String serviceCode) {
            McpSyncServer server = servers.get(serviceCode);
            return server == null ? List.of() : server.listTools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
        }

        @Override
        public void close() {
            servers.values().forEach(server -> {
                try {
                    server.close();
                } catch (Exception e) {
                    log.debug("关闭内置 MCP Server 失败: {}", e.getMessage());
                }
            });
            transports.forEach(transport -> {
                try {
                    transport.closeGracefully().block(Duration.ofSeconds(5));
                } catch (Exception e) {
                    log.debug("关闭内置 MCP transport 失败: {}", e.getMessage());
                }
            });
        }

        private static Map<String, ToolCallback> callbacksByName(
                ToolCallbackProvider provider) {
            ToolCallback[] callbacks = provider == null ? null : provider.getToolCallbacks();
            if (callbacks == null) {
                return Map.of();
            }
            return Arrays.stream(callbacks).collect(Collectors.toMap(
                    callback -> callback.getToolDefinition().name(),
                    callback -> callback,
                    (left, right) -> {
                        throw new IllegalStateException(
                                "内置 MCP ToolCallback 重名: "
                                        + left.getToolDefinition().name());
                    },
                    LinkedHashMap::new
            ));
        }

        private static void validateCatalog(Set<String> callbackNames) {
            Set<String> catalogNames = BuiltinMcpServiceDefinition.allToolNames();
            Set<String> missing = catalogNames.stream()
                    .filter(name -> !callbackNames.contains(name))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            Set<String> ungrouped = callbackNames.stream()
                    .filter(name -> !catalogNames.contains(name))
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            if (!missing.isEmpty() || !ungrouped.isEmpty()) {
                throw new IllegalStateException(
                        "内置 MCP 服务目录与 ToolCallback 不一致: missing=" + missing
                                + ", ungrouped=" + ungrouped);
            }
        }
    }
}
