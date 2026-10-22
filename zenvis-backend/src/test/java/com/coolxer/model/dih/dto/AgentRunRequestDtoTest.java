package com.coolxer.model.dih.dto;

import com.coolxer.configuration.JacksonConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunRequestDtoTest {

    @Test
    void readsCamelCasePayloadFromFrontend() throws Exception {
        AgentRunRequestDto request = JacksonConfig.OBJECT_MAPPER.readValue("""
                {
                  "chatId": "chat-1",
                  "type": "agent_report",
                  "message": "生成报表",
                  "model": "auto",
                  "clientMessageId": "message-1",
                  "reportAction": {
                    "type": "full_generate",
                    "documentId": "doc-1",
                    "baseRevision": 2,
                    "selectionId": "selection-1",
                    "selectionHash": "hash-1",
                    "sourceRefs": []
                  }
                }
                """, AgentRunRequestDto.class);

        assertThat(request.getChatId()).isEqualTo("chat-1");
        assertThat(request.getClientMessageId()).isEqualTo("message-1");
        assertThat(request.getReportAction()).isNotNull();
        assertThat(request.getReportAction().getDocumentId()).isEqualTo("doc-1");
        assertThat(request.getReportAction().getBaseRevision()).isEqualTo(2L);
        assertThat(request.getReportAction().getSelectionId()).isEqualTo("selection-1");
        assertThat(request.getReportAction().getSelectionHash()).isEqualTo("hash-1");
        assertThat(request.getReportAction().getSourceRefs()).isEmpty();
    }

    @Test
    void keepsSnakeCasePayloadCompatible() throws Exception {
        AgentRunRequestDto request = JacksonConfig.OBJECT_MAPPER.readValue("""
                {
                  "chat_id": "chat-2",
                  "type": "agent_report",
                  "message": "继续",
                  "client_message_id": "message-2"
                }
                """, AgentRunRequestDto.class);

        assertThat(request.getChatId()).isEqualTo("chat-2");
        assertThat(request.getClientMessageId()).isEqualTo("message-2");
    }
}
