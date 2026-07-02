package com.coolxer.service.system.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisTaskServiceImplTest {

    @TempDir
    Path skillRoot;

    @Test
    void buildAnalysisSystemPromptLoadsAnalysisSkillPrompt() throws Exception {
        Path analysisSkill = skillRoot.resolve("analysis-agent");
        Files.createDirectories(analysisSkill);
        Files.writeString(analysisSkill.resolve("skill.json"), """
                {
                  "id": "analysis-agent",
                  "name": "研判分析",
                  "enabled": true,
                  "agentTypes": ["agent_analysis"],
                  "entry": "SKILL.md"
                }
                """);
        Files.writeString(analysisSkill.resolve("SKILL.md"), "研判 Skill Prompt");

        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "skillPath", skillRoot.toString());
        SkillService skillService = new SkillService(customWebConfig, JacksonConfig.OBJECT_MAPPER.copy());
        skillService.reload();

        AnalysisTaskServiceImpl service = new AnalysisTaskServiceImpl();
        ReflectionTestUtils.setField(service, "skillService", skillService);

        String systemPrompt = ReflectionTestUtils.invokeMethod(service, "buildAnalysisSystemPrompt");

        assertThat(systemPrompt)
                .contains("ZenVis 的数据分析任务 Agent")
                .contains("【已加载 Skill】")
                .contains("研判 Skill Prompt");
    }
}
