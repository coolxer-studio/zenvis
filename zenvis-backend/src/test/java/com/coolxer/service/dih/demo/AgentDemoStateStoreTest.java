package com.coolxer.service.dih.demo;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDemoStateStoreTest {

    private final AgentDemoStateStore store = new AgentDemoStateStore();

    @Test
    void demoStateRoundTripsInSessionExtraData() {
        ChatSession session = new ChatSession()
                .setType("agent_data_visualization");

        store.activate(
                session,
                "data-visualization:user-event",
                "agent_data_visualization",
                "initial");

        assertThat(store.activeDemoId(
                session, "agent_data_visualization"))
                .contains("data-visualization:user-event");
        assertThat(store.isActive(
                session,
                "data-visualization:user-event",
                "agent_data_visualization"))
                .isTrue();
        assertThat(session.getExtraData())
                .contains("\"stage\":\"initial\"");
    }

    @Test
    void publicExtraDataCannotOverwriteServerDemoState() {
        ChatSession session = new ChatSession();
        store.activate(
                session,
                "data-access:user-event",
                "agent_data_access");

        String merged = store.preserveReserved(
                session.getExtraData(),
                """
                        {"dataVisualization":{"chartLibrary":[]},"agentDemos":{"client":true}}
                        """);

        Map<String, Object> root = JacksonUtil.toMap(
                merged, new TypeReference<Map<String, Object>>() {
                });
        @SuppressWarnings("unchecked")
        Map<String, Object> demoState = (Map<String, Object>) root.get(
                AgentDemoStateStore.RESERVED_KEY);
        assertThat(demoState)
                .containsKey("activeByAgent")
                .doesNotContainEntry("client", true);
    }
}
