package com.coolxer.model.dih.vo;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpApprovalVoTest {

    @Test
    void serializesOnlyFullPayloadFieldNames() {
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId("request-1")
                .setArguments("{\"id\":1}")
                .setResult("{\"ok\":true}")
                .setResultLength(11L);

        JsonNode json = JacksonConfig.OBJECT_MAPPER.valueToTree(new McpApprovalVo(invocation));

        assertThat(json.path("arguments").asText()).isEqualTo("{\"id\":1}");
        assertThat(json.path("result").asText()).isEqualTo("{\"ok\":true}");
        assertThat(json.path("result_length").asLong()).isEqualTo(11L);
        assertThat(json.has("arguments_summary")).isFalse();
        assertThat(json.has("result_summary")).isFalse();
    }
}
