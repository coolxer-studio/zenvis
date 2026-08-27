package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.enums.McpInvocationChannel;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public record McpInvocationContext(
        McpInvocationChannel channel,
        Integer requesterUserId,
        String chatId,
        String turnId,
        String agentType,
        String mcpSessionId,
        String mcpClientInfo,
        Consumer<McpApprovalEvent> eventConsumer,
        Integer analysisTaskId,
        String executionId,
        AnalysisTaskApprovalMode taskApprovalMode,
        BooleanSupplier cancelled
) {
    public static final String TOOL_CONTEXT_KEY = "zenvis_mcp_invocation_context";
    public static final String ANALYSIS_TASK_AGENT_TYPE = "agent_analysis_task";
    public static final String BUILTIN_DATA_ACCESS_DEMO =
            "builtin-data-access-demo";
    public static final String BUILTIN_DATA_VISUALIZATION_DEMO =
            "builtin-data-visualization-demo";

    public McpInvocationContext(McpInvocationChannel channel,
                                Integer requesterUserId,
                                String chatId,
                                String turnId,
                                String agentType,
                                String mcpSessionId,
                                String mcpClientInfo,
                                Consumer<McpApprovalEvent> eventConsumer) {
        this(channel, requesterUserId, chatId, turnId, agentType, mcpSessionId, mcpClientInfo,
                eventConsumer, null, null, null, null);
    }

    public static McpInvocationContext background(String agentType) {
        return new McpInvocationContext(
                McpInvocationChannel.BACKGROUND_AGENT,
                null,
                null,
                java.util.UUID.randomUUID().toString(),
                agentType,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static McpInvocationContext backgroundTask(Integer taskId,
                                                      String executionId,
                                                      Integer requesterUserId,
                                                      AnalysisTaskApprovalMode approvalMode,
                                                      Consumer<McpApprovalEvent> eventConsumer,
                                                      BooleanSupplier cancelled) {
        return new McpInvocationContext(
                McpInvocationChannel.BACKGROUND_AGENT,
                requesterUserId,
                null,
                executionId,
                ANALYSIS_TASK_AGENT_TYPE,
                null,
                "analysis-task:" + taskId,
                eventConsumer,
                taskId,
                executionId,
                approvalMode,
                cancelled
        );
    }

    public boolean isTaskCancelled() {
        return cancelled != null && cancelled.getAsBoolean();
    }

    /**
     * Deterministic demos are meant to demonstrate the real MCP approval
     * boundary. Their default-ASK tools must therefore request approval on
     * every invocation instead of inheriting an ALLOW override or a previous
     * chat grant.
     */
    public boolean requiresExplicitDemoApproval() {
        return isExplicitApprovalDemoClient(mcpClientInfo);
    }

    public static boolean isExplicitApprovalDemoClient(String clientInfo) {
        return BUILTIN_DATA_ACCESS_DEMO.equals(clientInfo)
                || BUILTIN_DATA_VISUALIZATION_DEMO.equals(clientInfo);
    }

    public void emit(McpApprovalEvent event) {
        if (eventConsumer != null && event != null) {
            eventConsumer.accept(event);
        }
    }
}
