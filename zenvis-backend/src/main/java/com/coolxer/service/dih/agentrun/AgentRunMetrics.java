package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Run 运行指标。指标不可用时不影响任务执行。
 */
@Component
public class AgentRunMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong queueLength = new AtomicLong();

    public AgentRunMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("dih.agent.run.queue.length", queueLength);
    }

    public void queueLength(long value) {
        queueLength.set(Math.max(value, 0));
    }

    public void stepDuration(Duration duration) {
        meterRegistry.timer("dih.agent.run.step.duration").record(duration);
    }

    public void approvalDuration(Duration duration) {
        meterRegistry.timer("dih.agent.run.approval.duration").record(duration);
    }

    public void recovery() {
        meterRegistry.counter("dih.agent.run.recovery").increment();
    }

    public void truncation() {
        meterRegistry.counter("dih.agent.run.truncation").increment();
    }

    public void terminal(AgentRunStatus status) {
        String outcome = status == null ? "unknown" : status.name().toLowerCase(Locale.ROOT);
        meterRegistry.counter("dih.agent.run.terminal", "status", outcome).increment();
    }

    public void failure(String stage) {
        meterRegistry.counter("dih.agent.run.failure", "stage", String.valueOf(stage)).increment();
    }
}
