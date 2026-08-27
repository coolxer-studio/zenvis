package com.coolxer.commons.enums;

/**
 * MCP 审批模式 for a background analysis task.
 */
public enum AnalysisTaskApprovalMode {
    /** Automatically approve ASK tools for this task execution. */
    AUTO,
    /** Suspend the task until a human decides each ASK request. */
    MANUAL
}
