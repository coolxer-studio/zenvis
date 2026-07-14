package com.coolxer.service.system.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AgentLlmService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void taskSkillSelectionOnlyAcceptsEnabledSkillsRegardlessOfAgentType() throws Exception {
        createSkill("enabled-any-agent", true, "agent_dispose", "已启用 Skill Prompt");
        createSkill("disabled-skill", false, "agent_analysis", "不应加载");

        SkillService skillService = createSkillService();

        assertThat(skillService.getEnabledOptions())
                .extracting(option -> option.getValue())
                .containsExactly("enabled-any-agent");
        assertThat(skillService.buildTaskSkillPrompt("agent_analysis", List.of("enabled-any-agent")))
                .contains("已启用 Skill Prompt");
        assertThatThrownBy(() -> skillService.validateEnabledSkillIds(List.of("disabled-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled-skill");
    }

    @Test
    void callAiAnalyzeUsesGenericAgentLlmServiceAndClearsContext() {
        AnalysisTask task = new AnalysisTask()
                .setName("每日研判")
                .setDescription("关注异常波动")
                .setModel("requested-model")
                .setPrompt("分析最近风险");

        McpToolContext mcpToolContext = McpToolContext.empty();
        FakeAIBaseService aiBaseService = new FakeAIBaseService();
        FakeAgentMcpToolService agentMcpToolService = new FakeAgentMcpToolService(mcpToolContext);
        FakeAgentLlmService agentLlmService = new FakeAgentLlmService();
        FakeSkillService skillService = new FakeSkillService();

        AnalysisTaskServiceImpl service = new AnalysisTaskServiceImpl();
        ReflectionTestUtils.setField(service, "aiBaseService", aiBaseService);
        ReflectionTestUtils.setField(service, "agentMcpToolService", agentMcpToolService);
        ReflectionTestUtils.setField(service, "agentLlmService", agentLlmService);
        ReflectionTestUtils.setField(service, "skillService", skillService);

        String result = ReflectionTestUtils.invokeMethod(service, "callAiAnalyze", task);

        assertThat(result).isEqualTo("分析结果");
        assertThat(aiBaseService.requestedModel).isEqualTo("requested-model");
        assertThat(agentMcpToolService.agentType).isEqualTo("agent_analysis");
        assertThat(agentLlmService.model).isEqualTo("resolved-model");
        assertThat(agentLlmService.mcpToolContext).isSameAs(mcpToolContext);
        assertThat(agentLlmService.systemPrompt).contains("分析 Skill Prompt");
        assertThat(agentLlmService.userPrompt).contains("每日研判").contains("分析最近风险");
        assertThat(agentLlmService.modelCleared).isTrue();
        assertThat(agentLlmService.mcpContextCleared).isTrue();
    }

    private void createSkill(String id, boolean enabled, String agentType, String content) throws Exception {
        Path skill = skillRoot.resolve(id);
        Files.createDirectories(skill);
        Files.writeString(skill.resolve("skill.json"), """
                {
                  "id": "%s",
                  "name": "%s",
                  "enabled": %s,
                  "agentTypes": ["%s"],
                  "entry": "SKILL.md"
                }
                """.formatted(id, id, enabled, agentType));
        Files.writeString(skill.resolve("SKILL.md"), content);
    }

    private SkillService createSkillService() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "skillPath", skillRoot.toString());
        SkillService skillService = new SkillService(customWebConfig, JacksonConfig.OBJECT_MAPPER.copy());
        skillService.reload();
        return skillService;
    }

    private static final class FakeAIBaseService extends AIBaseService {
        private String requestedModel;

        private FakeAIBaseService() {
            super("", "", "");
        }

        @Override
        public String resolveChatModel(String requestedModel, boolean deepThinking, boolean hasImageAttachment) {
            this.requestedModel = requestedModel;
            return "resolved-model";
        }
    }

    private static final class FakeAgentMcpToolService extends AgentMcpToolService {
        private final McpToolContext mcpToolContext;
        private String agentType;

        private FakeAgentMcpToolService(McpToolContext mcpToolContext) {
            super(null, null, null);
            this.mcpToolContext = mcpToolContext;
        }

        @Override
        public McpToolContext resolve(String agentType) {
            this.agentType = agentType;
            return mcpToolContext;
        }
    }

    private static final class FakeAgentLlmService extends AgentLlmService {
        private String model;
        private McpToolContext mcpToolContext;
        private String systemPrompt;
        private String userPrompt;
        private boolean modelCleared;
        private boolean mcpContextCleared;

        private FakeAgentLlmService() {
            super(null);
        }

        @Override
        public void setModel(String model) {
            this.model = model;
        }

        @Override
        public void clearModel() {
            this.modelCleared = true;
        }

        @Override
        public void setMcpToolContext(McpToolContext context) {
            this.mcpToolContext = context;
        }

        @Override
        public void clearMcpToolContext() {
            this.mcpContextCleared = true;
        }

        @Override
        public String callWithSystemPrompt(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return "分析结果";
        }
    }

    private static final class FakeSkillService extends SkillService {
        private FakeSkillService() {
            super(new CustomWebConfig(), JacksonConfig.OBJECT_MAPPER.copy());
        }

        @Override
        public String buildEnabledSkillPrompt(String agentType) {
            return "分析 Skill Prompt";
        }
    }
}
