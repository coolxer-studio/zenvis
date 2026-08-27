package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.enums.McpToolRiskLevel;

public record McpToolDescriptor(
        String toolKey,
        McpToolSourceType sourceType,
        Integer serverId,
        String serverCode,
        String serverName,
        String toolName,
        String aiToolName,
        String title,
        String description,
        Boolean readOnlyHint,
        Boolean destructiveHint,
        McpToolRiskLevel riskLevel,
        McpApprovalPolicy defaultPolicy
) {
    public static String localKey(String toolName) {
        return "local::" + toolName;
    }

    public static String externalKey(Integer serverId, String toolName) {
        return "external::" + serverId + "::" + toolName;
    }
}
