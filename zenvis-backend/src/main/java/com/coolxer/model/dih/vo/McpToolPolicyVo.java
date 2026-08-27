package com.coolxer.model.dih.vo;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;
import lombok.Data;

import java.util.Date;

@Data
public class McpToolPolicyVo {
    private String toolKey;
    private McpToolSourceType sourceType;
    private Integer serverId;
    private String serverCode;
    private String serverName;
    private String toolName;
    private String aiToolName;
    private String title;
    private String description;
    private Boolean readOnlyHint;
    private Boolean destructiveHint;
    private McpToolRiskLevel riskLevel;
    private McpApprovalPolicy defaultApprovalPolicy;
    private McpApprovalPolicy configuredApprovalPolicy;
    private McpApprovalPolicy effectiveApprovalPolicy;
    private Boolean available;
    private Date lastSeenTime;

    public McpToolPolicyVo(McpToolPolicyConfig config) {
        this.toolKey = config.getToolKey();
        this.sourceType = config.getSourceType();
        this.serverId = config.getServerId();
        this.serverCode = config.getServerCode();
        this.serverName = config.getServerName();
        this.toolName = config.getToolName();
        this.aiToolName = config.getAiToolName();
        this.title = config.getTitle();
        this.description = config.getDescription();
        this.readOnlyHint = config.getReadOnlyHint();
        this.destructiveHint = config.getDestructiveHint();
        this.riskLevel = config.getRiskLevel();
        this.defaultApprovalPolicy = config.getDefaultPolicy();
        this.configuredApprovalPolicy = config.getConfiguredPolicy();
        this.effectiveApprovalPolicy = config.effectivePolicy();
        this.available = config.getAvailable();
        this.lastSeenTime = config.getLastSeenTime();
    }
}
