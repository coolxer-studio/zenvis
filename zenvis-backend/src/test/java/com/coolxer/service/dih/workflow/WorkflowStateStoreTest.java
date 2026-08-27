package com.coolxer.service.dih.workflow;

import com.coolxer.dao.mysql.entity.ChatSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStateStoreTest {

    private final WorkflowStateStore store = new WorkflowStateStore();

    @Test
    void publicExtraDataUpdateCannotOverwriteServerWorkflowSection() {
        ChatSession session = new ChatSession();
        session.setExtraData("""
                {"dataVisualization":{"chartLibrary":[]},"agentWorkflows":{"token":"server"}}
                """);

        String merged = store.preserveReserved(
                session.getExtraData(),
                """
                        {"dataVisualization":{"chartLibrary":[{"id":"chart-1"}]},
                         "agentWorkflows":{"token":"client"}}
                        """);

        Map<String, Object> root = store.parse(merged);
        assertThat(store.map(root.get("agentWorkflows")))
                .containsEntry("token", "server");
        assertThat(store.map(root.get("dataVisualization")))
                .containsKey("chartLibrary");
    }

    @Test
    void upsertAndLoadActiveRoundTripsWithoutDatabaseMigration() {
        ChatSession session = new ChatSession();
        AgentWorkflowState state = new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setAgentType("agent_data_visualization")
                .setStep(AgentWorkflowStep.ENTITY_SELECTION)
                .setStatus("active");

        store.upsert(session, state);

        assertThat(store.loadActive(session, "agent_data_visualization"))
                .get()
                .extracting(AgentWorkflowState::getWorkflowId)
                .isEqualTo("workflow-1");
    }
}
