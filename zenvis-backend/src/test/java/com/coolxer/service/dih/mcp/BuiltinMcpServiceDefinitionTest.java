package com.coolxer.service.dih.mcp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinMcpServiceDefinitionTest {

    @Test
    void partitionsAllBuiltInToolsExactlyOnce() {
        assertThat(BuiltinMcpServiceDefinition.orderedValues())
                .extracting(BuiltinMcpServiceDefinition::code)
                .containsExactly(
                        "retrieval", "entity", "config", "push-task",
                        "visualization", "analysis-task");
        assertThat(BuiltinMcpServiceDefinition.orderedValues())
                .extracting(service -> service.toolNames().size())
                .containsExactly(22, 5, 7, 6, 21, 16);

        List<String> tools = BuiltinMcpServiceDefinition.orderedValues().stream()
                .flatMap(service -> service.toolNames().stream())
                .toList();
        Set<String> unique = new LinkedHashSet<>(tools);

        assertThat(tools).hasSize(77);
        assertThat(unique).hasSize(77);
        assertThat(BuiltinMcpServiceDefinition.allToolNames()).containsExactlyInAnyOrderElementsOf(unique);
    }

    @Test
    void exposesStableServiceEndpointsAndMembership() {
        BuiltinMcpServiceDefinition retrieval =
                BuiltinMcpServiceDefinition.findByCode("retrieval").orElseThrow();

        assertThat(retrieval.sseEndpoint()).isEqualTo("/mcp/retrieval/sse");
        assertThat(retrieval.messageEndpoint()).isEqualTo("/mcp/retrieval/message");
        assertThat(retrieval.serverName()).isEqualTo("zenvis-retrieval-mcp");
        assertThat(retrieval.containsTool("entity_view")).isTrue();
        assertThat(retrieval.containsTool("entity_update")).isFalse();
        assertThat(BuiltinMcpServiceDefinition.findByTool("entity_update"))
                .contains(BuiltinMcpServiceDefinition.ENTITY);
    }
}
