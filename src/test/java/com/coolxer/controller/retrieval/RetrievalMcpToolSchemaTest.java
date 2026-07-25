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

    @Test
    void retrievalSearchSchemaRequiresOneRequestObject() throws Exception {
        ToolCallback callback = findTool("retrieval_search");

        JsonNode schema = objectMapper.readTree(callback.getToolDefinition().inputSchema());
        JsonNode requestSchema = schema.path("properties").path("request");

        assertThat(schema.path("required"))
                .anyMatch(node -> "request".equals(node.asText()));
        assertThat(requestSchema.path("type").asText()).isEqualTo("object");
        assertThat(requestSchema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("entity", "display_list")
                .doesNotContain(
                        "id", "type", "criteria_list", "criteria_logic", "token",
                        "rule_name", "rule_description", "sql", "page", "size",
                        "sort_by", "order");
        assertThat(requestSchema.path("properties").path("criteria_list")
                .path("items").path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("attribute", "operator", "value_list");
        assertThat(requestSchema.path("properties").path("display_list")
                .path("items").path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("entity", "attribute_list");
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
