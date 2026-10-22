package com.coolxer.service.dih.agentrun;

/** Run 级工具预算耗尽，当前步骤不得继续调用工具。 */
public class AgentRunBudgetExceededException extends RuntimeException {
    public AgentRunBudgetExceededException(String message) {
        super(message);
    }
}
