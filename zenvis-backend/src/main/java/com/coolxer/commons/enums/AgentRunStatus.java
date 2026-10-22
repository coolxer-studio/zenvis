package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 持久化 Agent Run 状态。
 */
@Getter
public enum AgentRunStatus {
    QUEUED("等待执行"),
    RUNNING("执行中"),
    WAITING_APPROVAL("等待审批"),
    WAITING_USER("等待用户"),
    PAUSED("已暂停"),
    SUCCEEDED("已完成"),
    FAILED("已失败"),
    CANCELING("取消中"),
    CANCELLED("已取消");

    private final String description;

    AgentRunStatus(String description) {
        this.description = description;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public boolean isWaiting() {
        return this == WAITING_APPROVAL || this == WAITING_USER || this == PAUSED;
    }
}
