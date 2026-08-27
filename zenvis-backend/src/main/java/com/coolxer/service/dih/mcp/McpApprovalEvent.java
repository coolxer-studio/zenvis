package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.McpApprovalVo;

public record McpApprovalEvent(String event, McpApprovalVo data) {
    public static McpApprovalEvent required(McpApprovalVo data) {
        return new McpApprovalEvent("approval_required", data);
    }

    public static McpApprovalEvent updated(McpApprovalVo data) {
        return new McpApprovalEvent("approval_updated", data);
    }
}
