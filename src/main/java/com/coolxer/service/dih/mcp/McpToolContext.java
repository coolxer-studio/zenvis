package com.coolxer.service.dih.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.StringUtils;

public record McpToolContext(ToolCallbackProvider toolCallbackProvider, String systemPrompt) {

    private static final McpToolContext EMPTY = new McpToolContext(null, "");

    public static McpToolContext empty() {
        return EMPTY;
    }

    public boolean hasTools() {
        return toolCallbackProvider != null && StringUtils.hasText(systemPrompt);
    }

    public McpToolContext withToolCallbackProvider(ToolCallbackProvider provider) {
        return new McpToolContext(provider, systemPrompt);
    }
}
