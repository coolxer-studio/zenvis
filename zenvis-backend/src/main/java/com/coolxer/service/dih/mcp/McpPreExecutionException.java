package com.coolxer.service.dih.mcp;

/**
 * 工具在产生任何副作用之前已确定失败。
 */
public class McpPreExecutionException extends IllegalStateException {

    public McpPreExecutionException(String message) {
        super(message);
    }

    public McpPreExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
