package com.coolxer.service.dih.mcp;

/**
 * Run 场景下工具已持久化为待审批，当前 worker 必须立即退出而不是阻塞等待。
 */
public class McpApprovalPendingException extends RuntimeException {

    private final String requestId;

    public McpApprovalPendingException(String requestId) {
        super("MCP工具审批等待中: " + requestId);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
