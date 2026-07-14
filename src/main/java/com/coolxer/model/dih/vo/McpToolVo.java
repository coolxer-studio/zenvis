package com.coolxer.model.dih.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolRiskLevel;

@Data
@Builder
public class McpToolVo implements Serializable {

    private Integer serverId;

    private String serverCode;

    private String serverName;

    private String name;

    private String aiToolName;

    private String title;

    private String description;

    private Object inputSchema;

    private Map<String, Object> outputSchema;

    private Boolean readOnlyHint;

    private Boolean destructiveHint;

    private Boolean idempotentHint;

    private Boolean openWorldHint;

    private String toolKey;

    private McpToolRiskLevel riskLevel;

    private McpApprovalPolicy defaultApprovalPolicy;

    private McpApprovalPolicy configuredApprovalPolicy;

    private McpApprovalPolicy effectiveApprovalPolicy;

    private Boolean available;
}
