package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.StringUtils;

public record McpToolContext(ToolCallbackProvider toolCallbackProvider,
                             String systemPrompt,
                             McpInvocationContext invocationContext,
                             SkillRuntimeConfigVo skillRuntime,
                             ToolRuntimeContext toolRuntimeContext) {

    private static final McpToolContext EMPTY = new McpToolContext(null, "", null, null, null);

    public McpToolContext(ToolCallbackProvider toolCallbackProvider, String systemPrompt) {
        this(toolCallbackProvider, systemPrompt, null, null, null);
    }

    public McpToolContext(ToolCallbackProvider toolCallbackProvider,
                          String systemPrompt,
                          SkillRuntimeConfigVo skillRuntime) {
        this(
                toolCallbackProvider,
                systemPrompt,
                null,
                skillRuntime,
                skillRuntime == null ? null : new ToolRuntimeContext(skillRuntime.getLimits())
        );
    }

    public static McpToolContext empty() {
        return EMPTY;
    }

    public static McpToolContext empty(SkillRuntimeConfigVo skillRuntime) {
        return skillRuntime == null
                ? EMPTY
                : new McpToolContext(null, "", null, skillRuntime,
                new ToolRuntimeContext(skillRuntime.getLimits()));
    }

    public boolean hasTools() {
        return toolCallbackProvider != null && StringUtils.hasText(systemPrompt);
    }

    public McpToolContext withToolCallbackProvider(ToolCallbackProvider provider) {
        return new McpToolContext(
                provider, systemPrompt, invocationContext, skillRuntime, toolRuntimeContext);
    }

    public McpToolContext withInvocationContext(McpInvocationContext context) {
        return new McpToolContext(
                toolCallbackProvider, systemPrompt, context, skillRuntime, toolRuntimeContext);
    }
}
