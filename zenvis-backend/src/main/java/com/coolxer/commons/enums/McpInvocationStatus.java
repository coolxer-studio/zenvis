package com.coolxer.commons.enums;

public enum McpInvocationStatus {
    PENDING,
    APPROVED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    REJECTED,
    DENIED,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == REJECTED
                || this == DENIED || this == EXPIRED || this == CANCELLED;
    }
}
