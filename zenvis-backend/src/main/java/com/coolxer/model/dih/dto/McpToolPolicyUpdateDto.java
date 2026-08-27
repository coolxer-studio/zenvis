package com.coolxer.model.dih.dto;

import com.coolxer.commons.enums.McpApprovalPolicy;
import lombok.Data;

@Data
public class McpToolPolicyUpdateDto {
    private String toolKey;
    private McpApprovalPolicy policy;
}
