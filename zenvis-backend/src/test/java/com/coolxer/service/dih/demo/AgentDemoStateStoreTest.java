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
                .setType("agent_visualization_chart");

        store.activate(
                session,
                "visualization-chart:user-event",
                "agent_visualization_chart",
                "initial");

        assertThat(store.activeDemoId(
                session, "agent_visualization_chart"))
                .contains("visualization-chart:user-event");
        assertThat(store.isActive(
                session,
                "visualization-chart:user-event",
                "agent_visualization_chart"))
                .isTrue();
        assertThat(session.getExtraData())
                .contains("\"stage\":\"initial\"");
    }

    @Test
    void publicExtraDataCannotOverwriteServerDemoState() {
        ChatSession session = new ChatSession();
        store.activate(
                session,
                "metadata-config:user-event",
                "agent_metadata_config");

        String merged = store.preserveReserved(
                session.getExtraData(),
                """
                        {"visualizationChart":{"chartLibrary":[]},"agentDemos":{"client":true}}
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
