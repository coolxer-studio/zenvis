package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-turn state used to keep recursive model tool execution bounded.
 */
public final class ToolRuntimeContext {

    public static final String TOOL_CONTEXT_KEY = ToolRuntimeContext.class.getName();

    private final int maxToolCalls;
    private final int maxRepeatedFailures;
    private final int maxToolResultChars;
    private final int maxAccumulatedToolResultChars;
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger invalidArgumentAttempts = new AtomicInteger();
    private final AtomicInteger accumulatedToolResultChars = new AtomicInteger();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final Map<String, Integer> failureCounts = new LinkedHashMap<>();
    private volatile String stopReason;

    public ToolRuntimeContext(SkillRuntimeLimitsVo limits) {
        this.maxToolCalls = positive(limits == null ? null : limits.getMaxToolCalls());
        this.maxRepeatedFailures = positive(limits == null ? null : limits.getMaxRepeatedFailures());
        this.maxToolResultChars = positive(limits == null ? null : limits.getMaxToolResultChars());
        this.maxAccumulatedToolResultChars = positive(
                limits == null ? null : limits.getMaxAccumulatedToolResultChars());
    }

    public boolean hasLimits() {
        return maxToolCalls > 0
                || maxRepeatedFailures > 0
                || maxToolResultChars > 0
                || maxAccumulatedToolResultChars > 0;
    }

    public synchronized boolean reserveToolCalls(int count) {
        int requested = Math.max(count, 0);
        if (stopRequested.get()) {
            return false;
        }
        if (maxToolCalls > 0 && toolCalls.get() + requested > maxToolCalls) {
            requestStop("tool_call_budget_exhausted");
            return false;
        }
        toolCalls.addAndGet(requested);
        return true;
    }

    public int remainingToolCalls() {
        if (maxToolCalls <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxToolCalls - toolCalls.get(), 0);
    }

    public int registerInvalidArguments() {
        int attempts = invalidArgumentAttempts.incrementAndGet();
        if (maxRepeatedFailures > 0 && attempts >= maxRepeatedFailures) {
            requestStop("invalid_tool_arguments_repeated");
        }
        return attempts;
    }

    public synchronized int registerFailure(String signature) {
        String normalized = signature == null || signature.isBlank() ? "unknown_failure" : signature;
        int count = failureCounts.merge(normalized, 1, Integer::sum);
        if (maxRepeatedFailures > 0 && count >= maxRepeatedFailures) {
            requestStop("repeated_tool_failure");
        }
        return count;
    }

    public synchronized ResultAllowance reserveResultChars(int requestedChars) {
        int requested = Math.max(requestedChars, 0);
        int allowed = requested;
        if (maxToolResultChars > 0) {
            allowed = Math.min(allowed, maxToolResultChars);
        }
        if (maxAccumulatedToolResultChars > 0) {
            int remaining = Math.max(
                    maxAccumulatedToolResultChars - accumulatedToolResultChars.get(), 0);
            allowed = Math.min(allowed, remaining);
        }
        accumulatedToolResultChars.addAndGet(allowed);
        boolean truncated = allowed < requested;
        if (truncated && maxAccumulatedToolResultChars > 0
                && accumulatedToolResultChars.get() >= maxAccumulatedToolResultChars) {
            requestStop("tool_result_budget_exhausted");
        }
        return new ResultAllowance(allowed, requested, truncated);
    }

    public void requestStop(String reason) {
        stopReason = reason;
        stopRequested.set(true);
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    public String stopReason() {
        return stopReason;
    }

    public int toolCalls() {
        return toolCalls.get();
    }

    public int accumulatedToolResultChars() {
        return accumulatedToolResultChars.get();
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public int maxRepeatedFailures() {
        return maxRepeatedFailures;
    }

    private static int positive(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    public record ResultAllowance(int allowedChars, int requestedChars, boolean truncated) {
    }
}
