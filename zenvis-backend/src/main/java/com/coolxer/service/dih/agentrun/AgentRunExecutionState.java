package com.coolxer.service.dih.agentrun;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个 Run 步骤中由内部控制工具写入的瞬时状态。
 */
@Getter
public class AgentRunExecutionState {
    private final String runId;
    private final String stepId;
    private final AtomicBoolean planUpdated = new AtomicBoolean();
    private final AtomicBoolean stepUpdated = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean waitingUser = new AtomicBoolean();
    private final AtomicBoolean reportUpdated = new AtomicBoolean();
    private final AtomicBoolean approvalPending = new AtomicBoolean();
    private final AtomicBoolean toolSucceeded = new AtomicBoolean();
    private final AtomicBoolean budgetExhausted = new AtomicBoolean();
    private volatile String finalSummary = "";
    private volatile String userQuestion = "";
    @Setter
    private volatile String reportDocumentId = "";

    public AgentRunExecutionState(String runId, String stepId) {
        this.runId = runId;
        this.stepId = stepId;
    }

    public void markPlanUpdated() {
        planUpdated.set(true);
    }

    public void markStepUpdated() {
        stepUpdated.set(true);
    }

    public void finish(String summary) {
        finalSummary = summary == null ? "" : summary;
        finished.set(true);
    }

    public void requestUser(String question) {
        userQuestion = question == null ? "" : question;
        waitingUser.set(true);
    }

    public void markReportUpdated() {
        reportUpdated.set(true);
    }

    public void markApprovalPending() {
        approvalPending.set(true);
    }

    public void markToolSucceeded() {
        toolSucceeded.set(true);
    }

    public void markBudgetExhausted() {
        budgetExhausted.set(true);
    }

    public boolean hasProgress() {
        return planUpdated.get() || stepUpdated.get() || finished.get()
                || waitingUser.get() || reportUpdated.get() || toolSucceeded.get();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public boolean isWaitingUser() {
        return waitingUser.get();
    }

    public boolean isApprovalPending() {
        return approvalPending.get();
    }

    public boolean isBudgetExhausted() {
        return budgetExhausted.get();
    }
}
