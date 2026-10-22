package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.service.LabCapabilityService;
import com.coolxer.plugin.lab.service.LabNoteService;
import com.coolxer.plugin.lab.service.LabNoteServiceTestSupport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabMcpControllerTest {

    @Test
    void exposesTwoReadOnlyTools() {
        LabMcpController controller = new LabMcpController(
                new LabCapabilityService(), LabNoteServiceTestSupport.emptyService());

        List<McpSchema.Tool> tools = controller.mcpTools();

        assertThat(tools).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder(
                        LabMcpController.CAPABILITY_TOOL_NAME,
                        LabMcpController.NOTE_SEARCH_TOOL_NAME);
        assertThat(tools).allSatisfy(tool ->
                assertThat(tool.annotations().readOnlyHint()).isTrue());
        controller.close();
    }

    @Test
    void noteSearchLimitIsValidatedOnTheServer() {
        assertThat(LabMcpController.noteSearchLimit(null))
                .isEqualTo(LabMcpController.NOTE_SEARCH_DEFAULT_LIMIT);
        assertThat(LabMcpController.noteSearchLimit("50")).isEqualTo(50);
        assertThatThrownBy(() -> LabMcpController.noteSearchLimit(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 50");
        assertThatThrownBy(() -> LabMcpController.noteSearchLimit(51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 50");
        assertThatThrownBy(() -> LabMcpController.noteSearchLimit("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 50");
    }

    @Test
    void serializesNoteTimestampsForMcpClients() {
        LabMcpController controller = new LabMcpController(
                new LabCapabilityService(), LabNoteServiceTestSupport.emptyService());

        McpSchema.CallToolResult result = controller.serialize(
                new PageRows<>(List.of(new LabNote(1L, "标题", "正文", "draft", "demo",
                        LocalDateTime.of(2026, 10, 21, 8, 30),
                        LocalDateTime.of(2026, 10, 21, 9, 0))), 1L),
                "序列化失败");

        assertThat(result.isError()).isFalse();
        assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                .contains("\"create_time\":\"2026-10-21T08:30:00\"")
                .contains("\"update_time\":\"2026-10-21T09:00:00\"");
        controller.close();
    }
}
