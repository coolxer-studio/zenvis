package com.coolxer.service.dih.mcp;

/**
 * 外部写工具已经发出，但结果未落库。禁止盲目重放。
 */
public class McpToolResultUnknownException extends RuntimeException {

    private final String requestId;

    public McpToolResultUnknownException(String requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
