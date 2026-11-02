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
                {"visualizationChart":{"chartLibrary":[]},"agentWorkflows":{"token":"server"}}
                """);

        String merged = store.preserveReserved(
                session.getExtraData(),
                """
                        {"visualizationChart":{"chartLibrary":[{"id":"chart-1"}]},
                         "agentWorkflows":{"token":"client"}}
                        """);

        Map<String, Object> root = store.parse(merged);
        assertThat(store.map(root.get("agentWorkflows")))
                .containsEntry("token", "server");
        assertThat(store.map(root.get("visualizationChart")))
                .containsKey("chartLibrary");
    }

    @Test
    void upsertAndLoadActiveRoundTripsWithoutDatabaseMigration() {
        ChatSession session = new ChatSession();
        AgentWorkflowState state = new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setAgentType("agent_visualization_chart")
                .setStep(AgentWorkflowStep.ENTITY_SELECTION)
                .setStatus("active");

        store.upsert(session, state);

        assertThat(store.loadActive(session, "agent_visualization_chart"))
                .get()
                .extracting(AgentWorkflowState::getWorkflowId)
                .isEqualTo("workflow-1");
    }

    @Test
    void staleInMemoryWorkflowCannotRollBackNewerValidatedDigests() {
        String current = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflowId":"workflow-1","agentType":"agent_visualization_app",
                  "step":"APP_VALIDATING","status":"active","stateRevision":10,
                  "context":{"candidateDigest":"new-candidate","validationDigest":"new-validation"}
                }},"activeByAgent":{"agent_visualization_app":"workflow-1"}}}
                """;
        String stale = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflowId":"workflow-1","agentType":"agent_visualization_app",
                  "step":"APP_VALIDATING","status":"active","stateRevision":9,
                  "context":{"candidateDigest":"old-candidate","validationDigest":"old-validation"}
                }},"activeByAgent":{"agent_visualization_app":"workflow-1"}}}
                """;

        String merged = store.mergeReserved(current, stale);

        Map<String, Object> root = store.parse(merged);
        Map<String, Object> section = store.map(root.get(WorkflowStateStore.RESERVED_KEY));
        Map<String, Object> workflow = store.map(
                store.map(section.get("workflows")).get("workflow-1"));
        assertThat(workflow).containsEntry("stateRevision", 10);
        assertThat(store.map(workflow.get("context")))
                .containsEntry("candidateDigest", "new-candidate")
                .containsEntry("validationDigest", "new-validation");
    }

    @Test
    void newerWorkflowSnapshotStillWinsAndNonReservedFieldsArePreserved() {
        String current = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflowId":"workflow-1","agentType":"agent_visualization_app",
                  "status":"active","stateRevision":8,"context":{"candidateDigest":"old"}
                }}},"visualizationChart":{"chartLibrary":[]}}
                """;
        String incoming = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflowId":"workflow-1","agentType":"agent_visualization_app",
                  "status":"active","stateRevision":9,"context":{"candidateDigest":"new"}
                }}},"visualizationChart":{"chartLibrary":[{"id":"chart-1"}]}}
                """;

        String merged = store.mergeReserved(current, incoming);

        Map<String, Object> root = store.parse(merged);
        Map<String, Object> section = store.map(root.get(WorkflowStateStore.RESERVED_KEY));
        Map<String, Object> workflow = store.map(
                store.map(section.get("workflows")).get("workflow-1"));
        assertThat(store.map(workflow.get("context")))
                .containsEntry("candidateDigest", "new");
        assertThat(store.map(root.get("visualizationChart")))
                .containsKey("chartLibrary");
    }

    @Test
    void snakeCaseStateRevisionFromPersistedSessionsIsCompared() {
        String current = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflow_id":"workflow-1","agent_type":"agent_visualization_app",
                  "status":"active","state_revision":10,
                  "context":{"candidateDigest":"new-candidate"}
                }},"activeByAgent":{"agent_visualization_app":"workflow-1"}}}
                """;
        String stale = """
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflow_id":"workflow-1","agent_type":"agent_visualization_app",
                  "status":"active","state_revision":9,
                  "context":{"candidateDigest":"old-candidate"}
                }},"activeByAgent":{"agent_visualization_app":"workflow-1"}}}
                """;

        String merged = store.mergeReserved(current, stale);

        Map<String, Object> root = store.parse(merged);
        Map<String, Object> section = store.map(root.get(WorkflowStateStore.RESERVED_KEY));
        Map<String, Object> workflow = store.map(
                store.map(section.get("workflows")).get("workflow-1"));
        assertThat(workflow).containsEntry("state_revision", 10);
        assertThat(store.map(workflow.get("context")))
                .containsEntry("candidateDigest", "new-candidate");
    }

    @Test
    void upsertCannotDowngradeNewerPersistedWorkflowRevision() {
        ChatSession session = new ChatSession();
        session.setExtraData("""
                {"agentWorkflows":{"workflows":{"workflow-1":{
                  "workflow_id":"workflow-1","agent_type":"agent_visualization_app",
                  "step":"PERSIST_CONFIRMATION","status":"active","state_revision":10,
                  "context":{"candidateDigest":"new-candidate"}
                }},"activeByAgent":{"agent_visualization_app":"workflow-1"}}}
                """);

        store.upsert(session, new AgentWorkflowState()
                .setWorkflowId("workflow-1")
                .setAgentType("agent_visualization_app")
                .setStep(AgentWorkflowStep.APP_VALIDATING)
                .setStatus("active")
                .setStateRevision(9)
                .setContext(Map.of("candidateDigest", "old-candidate")));

        assertThat(store.loadActive(session, "agent_visualization_app"))
                .get()
                .satisfies(state -> {
                    assertThat(state.getStep()).isEqualTo(AgentWorkflowStep.PERSIST_CONFIRMATION);
                    assertThat(state.getStateRevision()).isEqualTo(10);
                    assertThat(state.getContext())
                            .containsEntry("candidateDigest", "new-candidate");
                });
    }
}
