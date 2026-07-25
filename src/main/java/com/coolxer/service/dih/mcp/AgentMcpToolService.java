package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import com.coolxer.model.dih.vo.SkillRuntimeToolsVo;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentMcpToolService {

    private static final String DEFAULT_AGENT_TYPE = "ask";

    private static final String GLOBAL_ENABLED_PROPERTY = "app.ai.mcp.enabled";

    private static final String AGENT_SCOPE_PREFIX = "app.ai.mcp.agent-scopes.";

    private static final String DEFAULT_SCOPE_PROPERTY = AGENT_SCOPE_PREFIX + "default";

    private static final String ALL_SCOPE = "*";

    private static final String DATA_VISUALIZATION_AGENT_TYPE = "agent_data_visualization";

    private static final Set<String> DATA_VISUALIZATION_ALLOWED_TOOLS = Set.of(
            "retrieval_search",
            "retrieval_list_rule",
            "retrieval_list_entity",
            "retrieval_list_attribute",
            "retrieval_list_candidate",
            "retrieval_list_display_entity",
            "retrieval_list_display_attribute",
            "entity_count",
            "entity_trend",
            "entity_statistics",
            "entity_list",
            "entity_view",
            "policy_config_ensure_root",
            "policy_config_add",
            "policy_config_apply",
            "policy_config_read",
            "dashboard_create",
            "dashboard_list",
            "dashboard_view",
            "menu_create",
            "menu_list",
            "menu_view",
            "menu_type_options",
            "menu_parent_options"
    );

    private static final String MCP_TOOL_USAGE_PROMPT = """
            【MCP工具使用规则】
            当前业务 Agent 可以使用下列 MCP 工具获取外部系统信息或执行操作。
            仅当用户问题确实需要外部系统数据、动作或上下文时才调用工具；如果直接回答更合适，请直接回答。
            调用工具前先确认必要参数；参数不足时先向用户追问，不要编造参数。
            标记“调用前需要用户审批”的工具由平台在调用时展示审批卡片；参数完整时直接发起工具调用，
            不要在调用前额外进行一轮自然语言确认。用户拒绝或审批超时后，根据工具返回的结构化状态继续说明结果。
            工具返回后，请用中文归纳结果，保留关键字段、异常信息和下一步建议。
            只能调用下方“可用 MCP 工具”中明确列出的工具名。`zenvis:*` 是前端 UI 代码块协议，
            例如 `zenvis:notice`、`zenvis:info-steps`、`zenvis:analysis-record`、`zenvis:analysis-decision`、`zenvis:data-access-decision`、`zenvis:meta-config-record`、
            `zenvis:vectum-task-record`、`zenvis:visualization-chart-preview`、`zenvis:visualization-chart-record`、`zenvis:visualization-config-record`、
            `zenvis:dashboard-config-record`、`zenvis:menu-config-record`、`zenvis:policy-record`，必须作为 Markdown 围栏代码块输出，绝不能作为工具调用。

            【可用 MCP 工具】
            %s
            """;

    private final McpClientService mcpClientService;

    private final Environment environment;

    private final ToolCallbackProvider localToolCallbackProvider;

    private final McpToolPolicyService policyService;

    private final SkillService skillService;

    @Autowired
    public AgentMcpToolService(McpClientService mcpClientService,
                               Environment environment,
                               @Qualifier("retrievalToolCallbackProvider") ToolCallbackProvider localToolCallbackProvider,
                               McpToolPolicyService policyService,
                               SkillService skillService) {
        this.mcpClientService = mcpClientService;
        this.environment = environment;
        this.localToolCallbackProvider = localToolCallbackProvider;
        this.policyService = policyService;
        this.skillService = skillService;
    }

    public AgentMcpToolService(McpClientService mcpClientService,
                               Environment environment,
                               ToolCallbackProvider localToolCallbackProvider,
                               McpToolPolicyService policyService) {
        this.mcpClientService = mcpClientService;
        this.environment = environment;
        this.localToolCallbackProvider = localToolCallbackProvider;
        this.policyService = policyService;
        this.skillService = null;
    }

    public AgentMcpToolService(McpClientService mcpClientService,
                               Environment environment,
                               ToolCallbackProvider localToolCallbackProvider) {
        this.mcpClientService = mcpClientService;
        this.environment = environment;
        this.localToolCallbackProvider = localToolCallbackProvider;
        this.policyService = null;
        this.skillService = null;
    }

    public McpToolContext resolve(String agentType) {
        return resolve(agentType, List.of());
    }

    /**
     * Resolve tools using both the Agent scope and the explicitly selected Skill runtime policy.
     */
    public McpToolContext resolve(String agentType, List<String> selectedSkillIds) {
        SkillRuntimeConfigVo runtime = skillService == null
                ? null
                : skillService.resolveRuntimeConfig(selectedSkillIds);
        Scope scope = resolveScope(agentType, runtime);
        if (!scope.enabled()) {
            return McpToolContext.empty(runtime);
        }

        String normalizedAgentType = normalizeAgentType(agentType);
        boolean dataVisualizationAgent = DATA_VISUALIZATION_AGENT_TYPE.equals(normalizedAgentType);
        SkillRuntimeToolsVo runtimeTools = runtime == null ? null : runtime.getTools();
        Set<String> localAllowlist = runtimeTools == null
                ? (dataVisualizationAgent ? DATA_VISUALIZATION_ALLOWED_TOOLS : null)
                : normalizeToolNames(runtimeTools.getLocal());
        Map<String, Set<String>> externalAllowlist = normalizeExternalToolNames(runtimeTools);

        List<ToolCallback> toolCallbacks = new ArrayList<>();
        Set<String> addedToolNames = new LinkedHashSet<>();
        StringBuilder mcpPrompt = new StringBuilder();
        appendLocalTools(toolCallbacks, addedToolNames, mcpPrompt, localAllowlist);
        if (runtimeTools != null) {
            appendExternalTools(scope, toolCallbacks, addedToolNames, mcpPrompt, externalAllowlist);
        } else if (!dataVisualizationAgent) {
            appendExternalTools(scope, toolCallbacks, addedToolNames, mcpPrompt, null);
        }

        if (StringUtils.isBlank(mcpPrompt)) {
            return McpToolContext.empty(runtime);
        }
        return new McpToolContext(
                ToolCallbackProvider.from(toolCallbacks),
                MCP_TOOL_USAGE_PROMPT.formatted(mcpPrompt.toString().trim()),
                runtime
        );
    }

    private void appendLocalTools(List<ToolCallback> toolCallbacks,
                                  Set<String> addedToolNames,
                                  StringBuilder prompt,
                                  Set<String> allowedToolNames) {
        ToolCallback[] callbacks = localToolCallbackProvider == null ? null : localToolCallbackProvider.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) {
            return;
        }
        StringBuilder localPrompt = new StringBuilder("### MCP服务：ZenVis 内置工具 (local)\n");
        boolean added = false;
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String toolName = callback.getToolDefinition().name();
            if (allowedToolNames != null && !allowedToolNames.contains(toolName)) {
                continue;
            }
            if (!addedToolNames.add(toolName)) {
                continue;
            }
            com.coolxer.commons.enums.McpApprovalPolicy policy = policyService == null
                    ? com.coolxer.commons.enums.McpApprovalPolicy.ALLOW
                    : policyService.effectivePolicy(McpToolDescriptor.localKey(toolName),
                    com.coolxer.commons.enums.McpApprovalPolicy.ASK);
            if (policy == com.coolxer.commons.enums.McpApprovalPolicy.DENY) {
                addedToolNames.remove(toolName);
                continue;
            }
            toolCallbacks.add(callback);
            added = true;
            String description = StringUtils.defaultIfBlank(callback.getToolDefinition().description(), toolName);
            localPrompt.append("- ").append(toolName)
                    .append("：")
                    .append(description)
                    .append(policy == com.coolxer.commons.enums.McpApprovalPolicy.ASK ? "（调用前需要用户审批）" : "")
                    .append("\n");
        }
        if (added) {
            prompt.append(localPrompt).append("\n");
        }
    }

    private void appendExternalTools(Scope scope,
                                     List<ToolCallback> toolCallbacks,
                                     Set<String> addedToolNames,
                                     StringBuilder prompt,
                                     Map<String, Set<String>> allowedToolsByServer) {
        if (!mcpClientService.hasAvailableTools(scope.serverCodes())) {
            return;
        }
        ToolCallbackProvider externalProvider = mcpClientService.getToolCallbackProvider(scope.serverCodes());
        ToolCallback[] externalCallbacks = externalProvider == null ? null : externalProvider.getToolCallbacks();
        if (externalCallbacks == null || externalCallbacks.length == 0) {
            return;
        }

        Set<String> allowedAiToolNames = allowedToolsByServer == null
                ? null
                : toAiToolNames(allowedToolsByServer);
        StringBuilder externalPrompt = new StringBuilder("### MCP服务：已筛选外部工具 (external)\n");
        boolean added = false;
        for (ToolCallback callback : externalCallbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            String toolName = callback.getToolDefinition().name();
            if (allowedAiToolNames != null && !allowedAiToolNames.contains(toolName)) {
                continue;
            }
            if (!addedToolNames.add(toolName)) {
                continue;
            }
            com.coolxer.commons.enums.McpApprovalPolicy policy = policyService == null
                    ? com.coolxer.commons.enums.McpApprovalPolicy.ALLOW
                    : policyService.effectivePolicyByAiToolName(
                    toolName, com.coolxer.commons.enums.McpApprovalPolicy.ASK);
            if (policy == com.coolxer.commons.enums.McpApprovalPolicy.DENY) {
                addedToolNames.remove(toolName);
                continue;
            }
            toolCallbacks.add(callback);
            added = true;
            externalPrompt.append("- ").append(toolName)
                    .append("：")
                    .append(StringUtils.defaultIfBlank(
                            callback.getToolDefinition().description(), toolName))
                    .append(policy == com.coolxer.commons.enums.McpApprovalPolicy.ASK
                            ? "（调用前需要用户审批）" : "")
                    .append("\n");
        }
        if (added) {
            prompt.append(externalPrompt).append("\n");
        }
    }

    private Scope resolveScope(String agentType, SkillRuntimeConfigVo runtime) {
        boolean globallyEnabled = Boolean.parseBoolean(environment.getProperty(GLOBAL_ENABLED_PROPERTY, "true"));
        if (!globallyEnabled) {
            return Scope.disabled();
        }

        String normalizedAgentType = normalizeAgentType(agentType);
        String configuredScope = environment.getProperty(AGENT_SCOPE_PREFIX + normalizedAgentType);
        if (StringUtils.isBlank(configuredScope)) {
            configuredScope = environment.getProperty(DEFAULT_SCOPE_PROPERTY, ALL_SCOPE);
        }

        if (isDisabledScope(configuredScope)) {
            return Scope.disabled();
        }
        List<String> configuredServerCodes;
        if (StringUtils.isBlank(configuredScope)
                || ALL_SCOPE.equals(configuredScope.trim())
                || "all".equalsIgnoreCase(configuredScope.trim())) {
            configuredServerCodes = List.of();
        } else {
            configuredServerCodes = Arrays.stream(configuredScope.split(","))
                    .map(StringUtils::trimToEmpty)
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }

        SkillRuntimeToolsVo runtimeTools = runtime == null ? null : runtime.getTools();
        if (runtimeTools == null || runtimeTools.getMcp() == null) {
            return configuredServerCodes.isEmpty()
                    ? Scope.all()
                    : new Scope(true, configuredServerCodes);
        }
        List<String> skillServerCodes = runtimeTools.getMcp().keySet().stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (skillServerCodes.isEmpty()) {
            return new Scope(true, List.of());
        }
        if (configuredServerCodes.isEmpty()) {
            return new Scope(true, skillServerCodes);
        }
        List<String> intersection = skillServerCodes.stream()
                .filter(configuredServerCodes::contains)
                .toList();
        return intersection.isEmpty() ? Scope.disabled() : new Scope(true, intersection);
    }

    private Set<String> normalizeToolNames(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Set.of();
        }
        return toolNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, Set<String>> normalizeExternalToolNames(SkillRuntimeToolsVo runtimeTools) {
        if (runtimeTools == null || runtimeTools.getMcp() == null) {
            return null;
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        runtimeTools.getMcp().forEach((serverCode, toolNames) -> {
            if (StringUtils.isBlank(serverCode)) {
                return;
            }
            normalized.put(serverCode.trim(), normalizeToolNames(toolNames));
        });
        return normalized;
    }

    private Set<String> toAiToolNames(Map<String, Set<String>> allowedToolsByServer) {
        Set<String> names = new LinkedHashSet<>();
        allowedToolsByServer.forEach((serverCode, toolNames) -> toolNames.forEach(toolName ->
                names.add(McpToolUtils.format(serverCode + "_" + toolName))));
        return names;
    }

    private boolean isDisabledScope(String configuredScope) {
        String normalized = StringUtils.trimToEmpty(configuredScope);
        return "none".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)
                || "false".equalsIgnoreCase(normalized)
                || "disabled".equalsIgnoreCase(normalized);
    }

    private String normalizeAgentType(String agentType) {
        return StringUtils.defaultIfBlank(agentType, DEFAULT_AGENT_TYPE);
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
