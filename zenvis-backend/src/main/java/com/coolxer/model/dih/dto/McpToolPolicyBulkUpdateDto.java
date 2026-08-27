package com.coolxer.model.dih.dto;

import com.coolxer.commons.enums.McpApprovalPolicy;
import lombok.Data;

import java.util.List;

@Data
public class McpToolPolicyBulkUpdateDto {
    private List<String> toolKeys;
    private McpApprovalPolicy policy;
}
