package com.coolxer.controller.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void exposesGenericAnalyticsToolsAndRemovesRetiredTools() {
        Set<String> names = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(new RetrievalMcpTool())
                        .build()
                        .getToolCallbacks())
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).contains(
                "entity_overview", "entity_summary", "entity_trend",
                "entity_distribution", "entity_value_statistics",
                "entity_relations", "entity_relation_timeline",
                "entity_aggregate", "entity_histogram", "entity_scatter");
        assertThat(names).doesNotContain("entity_count", "entity_statistics");
    }

    @Test
    void newAnalyticsToolSchemasExposeLogicalFieldsButNoSqlSurface() throws Exception {
        for (String toolName : Set.of(
                "entity_aggregate", "entity_histogram", "entity_scatter")) {
            JsonNode schema = objectMapper.readTree(
                    findTool(toolName).getToolDefinition().inputSchema());
            String schemaText = schema.toString();

            assertThat(schema.path("properties").path("request").path("type").asText())
                    .isEqualTo("object");
            assertThat(schemaText)
                    .contains("\"entity\"")
                    .doesNotContain("\"sql\"", "\"table_name\"", "\"column_name\"");
        }
    }

    @Test
    void listToolsBoundOversizedMcpPageRequests() {
        RetrievalMcpTool tool = new RetrievalMcpTool();
        RetrievalService retrievalService = mock(RetrievalService.class);
        EntityCoreService entityCoreService = mock(EntityCoreService.class);
        ReflectionTestUtils.setField(tool, "retrievalService", retrievalService);
        ReflectionTestUtils.setField(tool, "entityCoreService", entityCoreService);

        RetrievalRequestDto retrievalRequest = new RetrievalRequestDto();
        retrievalRequest.setSize(500);
        tool.searchByCriteria(retrievalRequest);
        tool.entityList("event", new LinkedHashMap<>(Map.of("per_page", 500)));

        assertThat(retrievalRequest.getSize()).isEqualTo(50);
        verify(retrievalService).retrievalByCriteria(retrievalRequest);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(entityCoreService).getPageList(eq("event"), captor.capture());
        assertThat(captor.getValue()).containsEntry("perPage", 50);
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
