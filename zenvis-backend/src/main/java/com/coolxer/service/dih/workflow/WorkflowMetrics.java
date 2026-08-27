package com.coolxer.service.dih.workflow;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
public class WorkflowMetrics {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public void transition(
            String agentType,
            AgentWorkflowStep from,
            AgentWorkflowStep to,
            String previousUpdatedAt) {
        if (meterRegistry == null || to == null) {
            return;
        }
        String agent = safe(agentType);
        String fromStep = from == null ? "NONE" : from.name();
        meterRegistry.counter(
                "dih.agent.workflow.transitions",
                "agent", agent,
                "from", fromStep,
                "to", to.name()).increment();
        if (!StringUtils.hasText(previousUpdatedAt) || from == null) {
            return;
        }
        try {
            Duration duration = Duration.between(
                    Instant.parse(previousUpdatedAt), Instant.now());
            if (!duration.isNegative()) {
                Timer.builder("dih.agent.workflow.state.duration")
                        .tag("agent", agent)
                        .tag("step", fromStep)
                        .register(meterRegistry)
                        .record(duration);
            }
        } catch (RuntimeException ignored) {
            // Older workflow timestamps may not use ISO-8601.
        }
    }

    public void mcpEvidence(String tool, String status) {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "dih.agent.workflow.mcp.calls",
                    "tool", safe(tool),
                    "status", safe(status)).increment();
        }
    }

    public void blocked(AgentWorkflowStep step) {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "dih.agent.workflow.blocked",
                    "step", step == null ? "UNKNOWN" : step.name()).increment();
        }
    }

    public void invalidTransition(AgentWorkflowStep step, String action) {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "dih.agent.workflow.invalid.transitions",
                    "step", step == null ? "UNKNOWN" : step.name(),
                    "action", safe(action)).increment();
        }
    }

    public void readBack(String objectType, String status) {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "dih.agent.workflow.readback",
                    "object", safe(objectType),
                    "status", safe(status)).increment();
        }
    }

    public void chartRenderFailure() {
        if (meterRegistry != null) {
            meterRegistry.counter(
                    "dih.agent.workflow.chart.render.failures").increment();
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }
}
