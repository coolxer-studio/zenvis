package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;

import java.util.List;
import java.util.Map;

/**
 * Agent-specific workflow rules used by the shared orchestrator and action API.
 */
public interface WorkflowDefinition {

    String agentType();

    String workflowType();

    String defaultObjectType();

    boolean supportsPart(ChatMessagePart part);

    AgentWorkflowStep resolveStep(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata);

    String resolveObjectType(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata);

    List<String> allowedActions(
            AgentWorkflowStep step,
            Map<String, Object> metadata);

    boolean isAllowedForState(AgentWorkflowStep step, String action);

    AgentWorkflowStep transition(AgentWorkflowState state, String action);

    Map<String, Object> continuation(AgentWorkflowState state, String action);

    void updateContext(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata);

    default void rememberAnswers(
            AgentWorkflowState state,
            List<Map<String, Object>> answers) {
        state.getContext().put("answers", answers == null ? List.of() : answers);
    }

    /**
     * Returns an error message when the action would use a modified candidate.
     */
    default String validateAction(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata,
            String action) {
        return "";
    }

    default void decorate(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        // Optional domain metadata.
    }
}
