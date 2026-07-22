package com.coolxer.controller.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalMcpToolSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void displayAttributeSchemaDoesNotRequireOptionalEntityOrRuleId() throws Exception {
        ToolCallback callback = findTool("retrieval_list_display_attribute");

        JsonNode schema = objectMapper.readTree(callback.getToolDefinition().inputSchema());

        assertThat(schema.path("required").isMissingNode() || schema.path("required").isEmpty()).isTrue();
        assertThat(schema.path("properties").has("entity")).isTrue();
        assertThat(schema.path("properties").has("ruleId")).isTrue();
    }

    private ToolCallback findTool(String name) {
        return Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(new RetrievalMcpTool())
                        .build()
                        .getToolCallbacks())
                .filter(tool -> name.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }
}
