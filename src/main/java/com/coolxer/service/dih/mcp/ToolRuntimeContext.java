package com.coolxer.service.dih.mcp;

import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.service.dih.DihTokenEstimator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-turn state used to keep recursive model tool execution bounded.
 */
public final class ToolRuntimeContext {

    public static final String TOOL_CONTEXT_KEY = ToolRuntimeContext.class.getName();
    /**
     * Conservative allowance for the truncation JSON envelope and the terminal
     * runtime instruction appended when the cumulative result budget is exhausted.
     */
    private static final int TRUNCATION_ENVELOPE_TOKEN_RESERVE = 256;

    private final int maxToolCalls;
    private final int maxRepeatedFailures;
    private final int maxToolResultChars;
    private final int maxAccumulatedToolResultChars;
    private final int maxAccumulatedToolResultTokens;
    private final DihTokenEstimator tokenEstimator = new DihTokenEstimator();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger invalidArgumentAttempts = new AtomicInteger();
    private final AtomicInteger accumulatedToolResultChars = new AtomicInteger();
    private final AtomicInteger accumulatedToolResultTokens = new AtomicInteger();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final Map<String, Integer> failureCounts = new LinkedHashMap<>();
    private volatile String stopReason;

    public ToolRuntimeContext(SkillRuntimeLimitsVo limits) {
        this.maxToolCalls = positive(limits == null ? null : limits.getMaxToolCalls());
        this.maxRepeatedFailures = positive(limits == null ? null : limits.getMaxRepeatedFailures());
        this.maxToolResultChars = positive(limits == null ? null : limits.getMaxToolResultChars());
        this.maxAccumulatedToolResultChars = positive(
                limits == null ? null : limits.getMaxAccumulatedToolResultChars());
        this.maxAccumulatedToolResultTokens = positive(
                limits == null ? null : limits.getMaxAccumulatedToolResultTokens());
    }

    public boolean hasLimits() {
        return maxToolCalls > 0
                || maxRepeatedFailures > 0
                || maxToolResultChars > 0
                || maxAccumulatedToolResultChars > 0
                || maxAccumulatedToolResultTokens > 0;
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

    public synchronized ResultAllowance reserveResult(String result) {
        String data = result == null ? "" : result;
        int requestedChars = data.length();
        int requestedTokens = tokenEstimator.estimate(data);
        int allowedChars = requestedChars;
        if (maxToolResultChars > 0) {
            allowedChars = Math.min(allowedChars, maxToolResultChars);
        }
        if (maxAccumulatedToolResultChars > 0) {
            int remaining = Math.max(
                    maxAccumulatedToolResultChars - accumulatedToolResultChars.get(), 0);
            allowedChars = Math.min(allowedChars, remaining);
        }
        allowedChars = safePrefixLength(data, allowedChars);

        int remainingTokens = maxAccumulatedToolResultTokens > 0
                ? Math.max(maxAccumulatedToolResultTokens - accumulatedToolResultTokens.get(), 0)
                : Integer.MAX_VALUE;
        boolean needsTruncationEnvelope =
                allowedChars < requestedChars || requestedTokens > remainingTokens;
        int envelopeTokens = needsTruncationEnvelope
                ? Math.min(TRUNCATION_ENVELOPE_TOKEN_RESERVE, remainingTokens)
                : 0;
        int contentTokenBudget = remainingTokens == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(remainingTokens - envelopeTokens, 0);
        allowedChars = Math.min(
                allowedChars,
                prefixLengthWithinTokenBudget(data, allowedChars, contentTokenBudget));

        String allowedContent = data.substring(0, allowedChars);
        int allowedContentTokens = tokenEstimator.estimate(allowedContent);
        boolean truncated = allowedChars < requestedChars;
        int accountedTokens = allowedContentTokens + (truncated ? envelopeTokens : 0);

        accumulatedToolResultChars.addAndGet(allowedChars);
        accumulatedToolResultTokens.addAndGet(accountedTokens);
        if (truncated
                && ((maxAccumulatedToolResultChars > 0
                && accumulatedToolResultChars.get() >= maxAccumulatedToolResultChars)
                || (maxAccumulatedToolResultTokens > 0
                && accumulatedToolResultTokens.get() >= maxAccumulatedToolResultTokens))) {
            requestStop("tool_result_budget_exhausted");
        }
        return new ResultAllowance(
                allowedChars,
                requestedChars,
                allowedContentTokens,
                requestedTokens,
                truncated);
    }

    private int prefixLengthWithinTokenBudget(String data, int maxChars, int maxTokens) {
        if (maxChars <= 0 || maxTokens <= 0) {
            return 0;
        }
        int safeMaxChars = safePrefixLength(data, Math.min(maxChars, data.length()));
        if (tokenEstimator.estimate(data.substring(0, safeMaxChars)) <= maxTokens) {
            return safeMaxChars;
        }
        int low = 0;
        int high = safeMaxChars;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            candidate = safePrefixLength(data, candidate);
            if (candidate <= low) {
                high = Math.max(low, candidate);
                continue;
            }
            if (tokenEstimator.estimate(data.substring(0, candidate)) <= maxTokens) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return safePrefixLength(data, low);
    }

    private int safePrefixLength(String data, int requestedLength) {
        int length = Math.max(0, Math.min(requestedLength, data.length()));
        if (length > 0
                && length < data.length()
                && Character.isHighSurrogate(data.charAt(length - 1))) {
            return length - 1;
        }
        return length;
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

    public int accumulatedToolResultTokens() {
        return accumulatedToolResultTokens.get();
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public int maxRepeatedFailures() {
        return maxRepeatedFailures;
    }

    public int maxAccumulatedToolResultChars() {
        return maxAccumulatedToolResultChars;
    }

    public int maxAccumulatedToolResultTokens() {
        return maxAccumulatedToolResultTokens;
    }

    private static int positive(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    public record ResultAllowance(int allowedChars,
                                  int requestedChars,
                                  int allowedTokens,
                                  int requestedTokens,
                                  boolean truncated) {
    }
}
