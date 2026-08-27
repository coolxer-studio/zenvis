package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * AI分析任务状态
 */
@Getter
public enum AnalysisTaskStatus {

    /**
     * 等待执行
     */
    PENDING("等待执行"),

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 等待 MCP 工具审批
     */
    WAITING_APPROVAL("等待审批"),

    /**
     * 取消请求已提交，等待后台执行线程退出
     */
    CANCELING("取消中"),

    /**
     * 执行成功
     */
    SUCCESS("执行成功"),

    /**
     * 执行失败
     */
    FAILED("执行失败"),

    /**
     * 已取消
     */
    CANCELED("已取消");

    private final String description;

    AnalysisTaskStatus(String description) {
        this.description = description;
    }

    public boolean isRunning() {
        return this == RUNNING || this == WAITING_APPROVAL || this == CANCELING;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }
}
