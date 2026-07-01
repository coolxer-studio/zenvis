package com.coolxer.service.dih.mcp;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AgentMcpToolService {

    private static final String DEFAULT_AGENT_TYPE = "ask";

    private static final String GLOBAL_ENABLED_PROPERTY = "app.ai.mcp.enabled";

    private static final String AGENT_SCOPE_PREFIX = "app.ai.mcp.agent-scopes.";

    private static final String DEFAULT_SCOPE_PROPERTY = AGENT_SCOPE_PREFIX + "default";

    private static final String ALL_SCOPE = "*";

    private static final String MCP_TOOL_USAGE_PROMPT = """
            【MCP工具使用规则】
            当前业务 Agent 可以使用下列 MCP 工具获取外部系统信息或执行操作。
            仅当用户问题确实需要外部系统数据、动作或上下文时才调用工具；如果直接回答更合适，请直接回答。
            调用工具前先确认必要参数；参数不足时先向用户追问，不要编造参数。
            对具有写入、删除、执行任务等副作用的工具，先用自然语言说明将要执行的动作并请求用户确认。
            工具返回后，请用中文归纳结果，保留关键字段、异常信息和下一步建议。

            【可用 MCP 工具】
            %s
            """;

    private final McpClientService mcpClientService;

    private final Environment environment;

    public AgentMcpToolService(McpClientService mcpClientService, Environment environment) {
        this.mcpClientService = mcpClientService;
        this.environment = environment;
    }

    public McpToolContext resolve(String agentType) {
        Scope scope = resolveScope(agentType);
        if (!scope.enabled() || !mcpClientService.hasAvailableTools(scope.serverCodes())) {
            return McpToolContext.empty();
        }

        String mcpPrompt = mcpClientService.buildEnabledMcpPrompt(scope.serverCodes());
        if (StringUtils.isBlank(mcpPrompt)) {
            return McpToolContext.empty();
        }
        return new McpToolContext(
                mcpClientService.getToolCallbackProvider(scope.serverCodes()),
                MCP_TOOL_USAGE_PROMPT.formatted(mcpPrompt)
        );
    }

    private Scope resolveScope(String agentType) {
        boolean globallyEnabled = Boolean.parseBoolean(environment.getProperty(GLOBAL_ENABLED_PROPERTY, "true"));
        if (!globallyEnabled) {
            return Scope.disabled();
        }

        String normalizedAgentType = StringUtils.defaultIfBlank(agentType, DEFAULT_AGENT_TYPE);
        String configuredScope = environment.getProperty(AGENT_SCOPE_PREFIX + normalizedAgentType);
        if (StringUtils.isBlank(configuredScope)) {
            configuredScope = environment.getProperty(DEFAULT_SCOPE_PROPERTY, ALL_SCOPE);
        }

        if (isDisabledScope(configuredScope)) {
            return Scope.disabled();
        }
        if (StringUtils.isBlank(configuredScope) || ALL_SCOPE.equals(configuredScope.trim()) || "all".equalsIgnoreCase(configuredScope.trim())) {
            return Scope.all();
        }

        List<String> serverCodes = Arrays.stream(configuredScope.split(","))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .toList();
        return serverCodes.isEmpty() ? Scope.disabled() : new Scope(true, serverCodes);
    }

    private boolean isDisabledScope(String configuredScope) {
        String normalized = StringUtils.trimToEmpty(configuredScope);
        return "none".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)
                || "false".equalsIgnoreCase(normalized)
                || "disabled".equalsIgnoreCase(normalized);
    }

    private record Scope(boolean enabled, List<String> serverCodes) {

        private static Scope disabled() {
            return new Scope(false, List.of());
        }

        private static Scope all() {
            return new Scope(true, List.of());
        }
    }
}
