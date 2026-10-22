package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.api.ResponseWrap;
import com.coolxer.plugin.lab.model.LabCapability;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteRequests;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LabJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestAcceptsCanonicalSnakeCaseFields() throws Exception {
        LabNoteRequests.Upsert request = mapper.readValue(
                "{\"title\":\"契约测试\",\"content\":\"正文\",\"status\":\"draft\",\"owner\":\"tester\"}",
                LabNoteRequests.Upsert.class);

        assertThat(request.title()).isEqualTo("契约测试");
        assertThat(request.status()).isEqualTo("draft");
    }

    @Test
    void responseUsesSnakeCaseWithoutCamelCaseAliases() throws Exception {
        LabNote note = new LabNote(1L, "标题", "正文", "draft", "demo",
                LocalDateTime.of(2026, 10, 21, 8, 30),
                LocalDateTime.of(2026, 10, 21, 9, 0));

        String json = mapper.writeValueAsString(ResponseWrap.success(new PageRows<>(List.of(note), 1)));

        assertThat(json).contains("\"create_time\"", "\"update_time\"");
        assertThat(json).contains("\"create_time\":\"2026-10-21T08:30:00\"");
        assertThat(json).contains("\"update_time\":\"2026-10-21T09:00:00\"");
        assertThat(json).doesNotContain("createTime", "updateTime");
    }

    @Test
    void capabilityCatalogUsesSnakeCaseContract() throws Exception {
        String json = mapper.writeValueAsString(
                new LabCapability("mcp", "只读 MCP", "Agent", "描述", "lab", true));

        assertThat(json).contains("\"demo_entry\":\"lab\"", "\"read_only\":true");
        assertThat(json).doesNotContain("demoEntry", "readOnly");
    }
}
