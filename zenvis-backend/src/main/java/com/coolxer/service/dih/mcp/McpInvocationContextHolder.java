package com.coolxer.service.dih.mcp;

import java.util.function.Supplier;

/** Makes the authenticated MCP requester available to in-process tool services. */
public final class McpInvocationContextHolder {

    private static final ThreadLocal<McpInvocationContext> CURRENT = new ThreadLocal<>();

    private McpInvocationContextHolder() {
    }

    public static McpInvocationContext current() {
        return CURRENT.get();
    }

    public static <T> T callWith(McpInvocationContext context, Supplier<T> action) {
        McpInvocationContext previous = CURRENT.get();
        try {
            if (context == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(context);
            }
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
