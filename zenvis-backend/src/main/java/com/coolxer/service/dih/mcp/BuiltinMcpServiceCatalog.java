package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import com.coolxer.model.dih.vo.BuiltinMcpServiceVo;
import com.coolxer.model.dih.vo.McpToolVo;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only view of the fixed built-in MCP service catalog.
 */
@Service
public class BuiltinMcpServiceCatalog {

    private final McpApprovalToolCallbackProvider toolCallbackProvider;

    private final McpToolPolicyService policyService;

    private final Map<String, ToolCallback> callbacksByName;

    public BuiltinMcpServiceCatalog(
            @Qualifier("retrievalToolCallbackProvider")
            McpApprovalToolCallbackProvider toolCallbackProvider,
            McpToolPolicyService policyService) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.policyService = policyService;
        this.callbacksByName = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(),
                        callback -> callback,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public List<BuiltinMcpServiceVo> listServices() {
        return BuiltinMcpServiceDefinition.orderedValues().stream()
                .map(service -> new BuiltinMcpServiceVo(
                        service.code(),
                        service.serviceName(),
                        service.description(),
                        service.sseEndpoint(),
                        service.messageEndpoint(),
                        service.toolNames().size()
                ))
                .toList();
    }

    public List<McpToolVo> listTools(String serviceCode) {
        BuiltinMcpServiceDefinition service = BuiltinMcpServiceDefinition.findByCode(serviceCode)
                .orElseThrow(() -> new ApiException(
                        ResultCodeEnum.NO_SUPPORTED.getCode(), "内置 MCP 服务不存在"));
        return service.toolNames().stream().map(this::toToolVo).toList();
    }

    private McpToolVo toToolVo(String toolName) {
        ToolCallback callback = callbacksByName.get(toolName);
        McpToolDescriptor descriptor = toolCallbackProvider.descriptor(toolName);
        if (callback == null || descriptor == null) {
            throw new IllegalStateException("内置 MCP 工具未注册: " + toolName);
        }
        McpToolPolicyConfig storedPolicy = policyService.findByToolKey(descriptor.toolKey())
                .orElse(null);
        Object inputSchema;
        try {
            inputSchema = JacksonConfig.OBJECT_MAPPER.readValue(
                    callback.getToolDefinition().inputSchema(), Map.class);
        } catch (Exception e) {
            inputSchema = callback.getToolDefinition().inputSchema();
        }
        return McpToolVo.builder()
                .serverId(null)
                .serverCode(descriptor.serverCode())
                .serverName(descriptor.serverName())
                .name(toolName)
                .aiToolName(toolName)
                .title(descriptor.title())
                .description(callback.getToolDefinition().description())
                .inputSchema(inputSchema)
                .readOnlyHint(descriptor.readOnlyHint())
                .destructiveHint(descriptor.destructiveHint())
                .toolKey(descriptor.toolKey())
                .riskLevel(descriptor.riskLevel())
                .defaultApprovalPolicy(descriptor.defaultPolicy())
                .configuredApprovalPolicy(storedPolicy == null
                        ? null : storedPolicy.getConfiguredPolicy())
                .effectiveApprovalPolicy(storedPolicy == null
                        ? descriptor.defaultPolicy() : storedPolicy.effectivePolicy())
                .available(true)
                .build();
    }
}
