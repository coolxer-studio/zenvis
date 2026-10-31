package com.coolxer.model.dih.dto;

import com.coolxer.configuration.JacksonConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunInteractionResponseDtoTest {

    @Test
    void readsCamelCaseStateRevisionFromFrontend() throws Exception {
        AgentRunInteractionResponseDto request = JacksonConfig.OBJECT_MAPPER.readValue("""
                {
                  "action": "retry",
                  "stateRevision": 7
                }
                """, AgentRunInteractionResponseDto.class);

        assertThat(request.getAction()).isEqualTo("retry");
        assertThat(request.getStateRevision()).isEqualTo(7);
    }

    @Test
    void keepsSnakeCaseStateRevisionCompatible() throws Exception {
        AgentRunInteractionResponseDto request = JacksonConfig.OBJECT_MAPPER.readValue("""
                {
                  "action": "retry",
                  "state_revision": 7
                }
                """, AgentRunInteractionResponseDto.class);

        assertThat(request.getStateRevision()).isEqualTo(7);
    }
}
