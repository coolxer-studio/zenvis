package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpInvocationChannel;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Arrays;
import java.util.Map;

/**
 * Enforces the persisted MCP approval policy immediately before every tool invocation.
 */
public class McpApprovalToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final Map<String, McpToolDescriptor> descriptorsByCallbackName;
    private final McpApprovalService approvalService;
    private final McpToolPolicyService policyService;

    public McpApprovalToolCallbackProvider(ToolCallbackProvider delegate,
                                           Map<String, McpToolDescriptor> descriptorsByCallbackName,
                                           McpApprovalService approvalService,
                                           McpToolPolicyService policyService) {
        this.delegate = delegate;
        this.descriptorsByCallbackName = descriptorsByCallbackName;
        this.approvalService = approvalService;
        this.policyService = policyService;
        descriptorsByCallbackName.values().forEach(policyService::register);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] callbacks = delegate == null ? null : delegate.getToolCallbacks();
        if (callbacks == null) {
            return new ToolCallback[0];
        }
        return Arrays.stream(callbacks).map(this::wrap).toArray(ToolCallback[]::new);
    }

    public McpToolDescriptor descriptor(String callbackName) {
        return descriptorsByCallbackName.get(callbackName);
    }

    private ToolCallback wrap(ToolCallback callback) {
        McpToolDescriptor descriptor = descriptorsByCallbackName.get(callback.getToolDefinition().name());
        return descriptor == null ? callback : new ApprovalToolCallback(callback, descriptor, approvalService);
    }

    private record ApprovalToolCallback(ToolCallback delegate,
                                        McpToolDescriptor descriptor,
                                        McpApprovalService approvalService) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            McpInvocationContext context = McpInvocationContext.background(null);
            return approvalService.execute(descriptor, toolInput, context,
                    () -> McpInvocationContextHolder.callWith(context, () -> delegate.call(toolInput)));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            McpInvocationContext context = resolveContext(toolContext);
            return approvalService.execute(descriptor, toolInput, context,
                    () -> McpInvocationContextHolder.callWith(
                            context,
                            () -> delegate.call(toolInput, externalMcpContext())
                    ));
        }

        /**
         * Spring AI forwards ToolContext entries as JSON-RPC {@code _meta} for
         * external MCP calls. Zenvis stores mutable runtime and approval objects
         * there for local orchestration; they are neither MCP metadata nor safely
         * serializable. The approval context has already been resolved above, so
         * external servers receive an intentionally empty public metadata map.
         */
        private ToolContext externalMcpContext() {
            return new ToolContext(Map.of());
        }

        private McpInvocationContext resolveContext(ToolContext toolContext) {
            if (toolContext != null) {
                Object configured = toolContext.getContext().get(McpInvocationContext.TOOL_CONTEXT_KEY);
                if (configured instanceof McpInvocationContext context) {
                    return context;
                }
                McpSyncServerExchange exchange = McpToolUtils.getMcpExchange(toolContext).orElse(null);
                if (exchange != null) {
                    String clientInfo = exchange.getClientInfo() == null ? null : exchange.getClientInfo().toString();
                    return new McpInvocationContext(
                            McpInvocationChannel.MCP_SERVER,
                            null,
                            null,
                            exchange.sessionId(),
                            null,
                            exchange.sessionId(),
                            clientInfo,
                            null
                    );
                }
            }
            return McpInvocationContext.background(null);
        }
    }
}
