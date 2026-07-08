package com.coolxer.lubinsun.model;

import lombok.Getter;

@Getter
public enum LubinsunTaskStatus {

    DRAFT("草稿"),
    ACCEPTED("已接收"),
    RUNNING("执行中"),
    WAITING_PERMISSION("等待审批"),
    COMPLETED("已完成"),
    FAILED("执行失败"),
    INTERRUPTED("已中断"),
    EXECUTE_FAILED("调用失败");

    private final String description;

    LubinsunTaskStatus(String description) {
        this.description = description;
    }

    public boolean isRunning() {
        return this == ACCEPTED || this == RUNNING || this == WAITING_PERMISSION;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == INTERRUPTED || this == EXECUTE_FAILED;
    }

    public static LubinsunTaskStatus fromPlatformStatus(String status) {
        if (status == null || status.isBlank()) {
            return RUNNING;
        }
        return switch (status.trim().toLowerCase()) {
            case "accepted" -> ACCEPTED;
            case "running" -> RUNNING;
            case "waiting_permission" -> WAITING_PERMISSION;
            case "completed" -> COMPLETED;
            case "failed" -> FAILED;
            case "interrupted" -> INTERRUPTED;
            default -> RUNNING;
        };
    }
}
