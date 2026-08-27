package com.coolxer.commons.enums;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTypeDeserializerTest {

    @Test
    void unknownMessageTypeFallsBackToText() throws Exception {
        Message message = JacksonConfig.OBJECT_MAPPER.readValue(
                """
                        {
                          "sender": "ai",
                          "content": "legacy message",
                          "type": "legacy-chart-v2"
                        }
                        """,
                Message.class
        );

        assertThat(message.getType()).isEqualTo(MessageType.TEXT);
    }
}
