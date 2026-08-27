package com.coolxer.controller.dih;

import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerTest {

    @Test
    void implementedBuiltinAgentsDoNotUsePlaceholderRoute() {
        ChatController controller = new ChatController();

        Boolean visualizationPlaceholder = ReflectionTestUtils.invokeMethod(
                controller,
                "isPlaceholderBuiltinAgent",
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION
        );
        Boolean reportPlaceholder = ReflectionTestUtils.invokeMethod(
                controller,
                "isPlaceholderBuiltinAgent",
                BuiltinAgentSkillRegistry.AGENT_REPORT
        );

        assertThat(visualizationPlaceholder).isFalse();
        assertThat(reportPlaceholder).isFalse();
    }
}
