package com.coolxer.service.dih.mcp;

import com.coolxer.controller.retrieval.RetrievalMcpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSchemaNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void inlinesNestedSpringAiDefinitionsForStrictProviders() throws Exception {
        String rawSchema = findTool("entity_trend").getToolDefinition().inputSchema();
        assertThat(rawSchema).contains("\"$defs\"").contains("#/$defs/RequestCriteriaDto");

        String normalized = ToolSchemaNormalizer.normalize(rawSchema);
        JsonNode schema = objectMapper.readTree(normalized);
        JsonNode criteriaItems = schema.path("properties")
                .path("request")
                .path("properties")
                .path("criteria_list")
                .path("items");

        assertThat(normalized).doesNotContain("\"$defs\"").doesNotContain("\"$ref\"");
        assertThat(criteriaItems.path("type").asText()).isEqualTo("object");
        assertThat(criteriaItems.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("attribute", "operator", "value_list");
        assertThat(criteriaItems.path("properties").has("value_list")).isTrue();
    }

    @Test
    void analyticsMetricSchemaOnlyRequiresOperation() throws Exception {
        String rawSchema = findTool("entity_trend").getToolDefinition().inputSchema();
        JsonNode metricSchema = objectMapper.readTree(ToolSchemaNormalizer.normalize(rawSchema))
                .path("properties")
                .path("request")
                .path("properties")
                .path("series")
                .path("items")
                .path("properties")
                .path("metric");

        assertThat(metricSchema.path("properties").fieldNames())
                .toIterable()
                .contains("field", "percentile", "operation");
        assertThat(metricSchema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactly("operation");
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
