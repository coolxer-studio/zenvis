package com.coolxer.controller.dih;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.Message;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionControllerTest {

    @TempDir
    Path skillRoot;

    @Test
    void dynamicSkillPrologueUsesManifestContentAndSuggestions() throws Exception {
        Path skillDir = skillRoot.resolve("demo-chat-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.json"), """
                {
                  "id": "demo-chat-skill",
                  "name": "演示技能",
                  "description": "默认说明",
                  "enabled": true,
                  "chat": {
                    "enabled": true,
                    "label": "演示入口",
                    "prologue": "我是动态技能助手。",
                    "promptSuggestions": [
                      {"label": "快速开始", "prompt": "请执行一次演示。"}
                    ]
                  },
                  "entry": "SKILL.md"
                }
                """);
        Files.writeString(skillDir.resolve("SKILL.md"), "演示技能提示词");

        CustomWebConfig config = new CustomWebConfig();
        ReflectionTestUtils.setField(config, "skillPath", skillRoot.toString());
        SkillService skillService = new SkillService(config, JacksonConfig.OBJECT_MAPPER.copy());
        skillService.reload();
        ChatSessionController controller = new ChatSessionController();
        ReflectionTestUtils.setField(controller, "skillService", skillService);

        Message message = ReflectionTestUtils.invokeMethod(
                controller,
                "buildPrologueMessage",
                "skill:demo-chat-skill"
        );

        assertThat(message).isNotNull();
        assertThat(message.getContent()).contains("我是动态技能助手。").contains("快速开始");
        assertThat(message.getParts()).extracting("type")
                .containsExactly("markdown", "prompt-suggestions");
        assertThat(message.getParts().get(1).getMetadata().toString())
                .contains("请执行一次演示。");
    }

    @Test
    void missingDynamicSkillReturnsUnavailablePrologue() {
        CustomWebConfig config = new CustomWebConfig();
        ReflectionTestUtils.setField(config, "skillPath", skillRoot.toString());
        SkillService skillService = new SkillService(config, JacksonConfig.OBJECT_MAPPER.copy());
        skillService.reload();
        ChatSessionController controller = new ChatSessionController();
        ReflectionTestUtils.setField(controller, "skillService", skillService);

        Message message = ReflectionTestUtils.invokeMethod(
                controller,
                "buildPrologueMessage",
                "skill:missing"
        );

        assertThat(message).isNotNull();
        assertThat(message.getContent()).contains("已停用或不存在");
    }
}
