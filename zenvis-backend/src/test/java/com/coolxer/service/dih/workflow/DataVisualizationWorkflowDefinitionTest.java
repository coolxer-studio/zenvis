package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataVisualizationWorkflowDefinitionTest {

    private final DataVisualizationWorkflowDefinition definition =
            new DataVisualizationWorkflowDefinition();

    @Test
    void retryFromBlockedQueryFailureReturnsToDataQuery() {
        AgentWorkflowState state = blockedState();
        state.setFailures(List.of(
                Map.of("stage", "DATA_QUERY", "retryStep", "DATA_QUERY"),
                Map.of("stage", "BLOCKED", "retryStep", "BLOCKED")));

        assertThat(definition.transition(state, "retry"))
                .isEqualTo(AgentWorkflowStep.DATA_QUERY);
    }

    @Test
    void retryNeverRemainsBlockedWhenNoQueryIsAvailable() {
        AgentWorkflowState state = blockedState();
        state.setContext(new LinkedHashMap<>());
        state.setFailures(List.of(
                Map.of("stage", "BLOCKED", "retryStep", "BLOCKED")));

        assertThat(definition.transition(state, "retry"))
                .isEqualTo(AgentWorkflowStep.ENTITY_META);
    }

    @Test
    void failedQueryCardResolvesToBlockedRetryState() {
        AgentWorkflowState state = blockedState();
        ChatMessagePart part = ChatMessagePart.builder()
                .id("query-failure")
                .type("confirm")
                .status("pending")
                .metadata(Map.of(
                        "action", "data_visualization.execute_approved_query"))
                .build();

        assertThat(definition.resolveStep(state, part, part.getMetadata()))
                .isEqualTo(AgentWorkflowStep.BLOCKED);
        assertThat(definition.allowedActions(
                AgentWorkflowStep.BLOCKED, part.getMetadata()))
                .containsExactly("retry", "revise");
    }

    private AgentWorkflowState blockedState() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("tool", "entity_aggregate");
        query.put("request", Map.of("entity", "user_event"));
        return new AgentWorkflowState()
                .setStep(AgentWorkflowStep.BLOCKED)
                .setContext(new LinkedHashMap<>(Map.of("query", query)));
    }
}
