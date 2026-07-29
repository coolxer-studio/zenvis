package com.coolxer.service.system.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AgentLlmService;
import com.coolxer.service.dih.ChatMessagePartParser;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTaskServiceImplTest {

    @TempDir
    Path skillRoot;

    @Test
    void buildAnalysisSystemPromptLoadsAnalysisSkillPrompt() throws Exception {
        Path analysisSkill = skillRoot.resolve("analysis-task-skill");
        Files.createDirectories(analysisSkill);
        Files.writeString(analysisSkill.resolve("skill.json"), """
                {
                  "id": "analysis-task-skill",
                  "name": "后台分析",
                  "enabled": true,
                  "agentTypes": ["agent_analysis_task"],
                  "entry": "SKILL.md"
                }
                """);
        Files.writeString(analysisSkill.resolve("SKILL.md"), "后台分析 Skill Prompt");

        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "skillPath", skillRoot.toString());
        SkillService skillService = new SkillService(customWebConfig, JacksonConfig.OBJECT_MAPPER.copy());
        skillService.reload();

        AnalysisTaskServiceImpl service = new AnalysisTaskServiceImpl();
        ReflectionTestUtils.setField(service, "skillService", skillService);
        AnalysisTask task = new AnalysisTask().setSkillIds(Set.of("analysis-task-skill"));

        String systemPrompt = ReflectionTestUtils.invokeMethod(service, "buildAnalysisSystemPrompt", task);

        assertThat(systemPrompt)
                .contains("ZenVis 的 AI分析任务 Agent")
                .contains("【已加载 Skill】")
                .contains("后台分析 Skill Prompt");
    }

    @Test
    void taskSkillSelectionOnlyAcceptsEnabledSkillsRegardlessOfAgentType() throws Exception {
        createSkill("enabled-any-agent", true, "agent_report", "已启用 Skill Prompt");
        createSkill("disabled-skill", false, "agent_data_access", "不应加载");
        createSkill("matching-but-not-selected", true, "agent_analysis_task", "同类型但未选中");

        SkillService skillService = createSkillService();

        assertThat(skillService.getEnabledOptions())
                .extracting(option -> option.getValue())
                .containsExactlyInAnyOrder("enabled-any-agent", "matching-but-not-selected");
        assertThat(skillService.buildTaskSkillPrompt(
                McpInvocationContext.ANALYSIS_TASK_AGENT_TYPE,
                List.of("enabled-any-agent")))
                .contains("已启用 Skill Prompt")
                .doesNotContain("同类型但未选中");
        assertThatThrownBy(() -> skillService.validateEnabledSkillIds(List.of("disabled-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled-skill");
    }

    @Test
    void callAiAnalyzeUsesGenericAgentLlmServiceAndClearsContext() {
        AnalysisTask task = new AnalysisTask()
                .setName("每日数据分析")
                .setDescription("关注异常波动")
                .setModel("requested-model")
                .setPrompt("分析最近风险")
                .setSkillIds(Set.of("example-analysis-skill"));

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
        assertThat(agentMcpToolService.agentType).isEqualTo(McpInvocationContext.ANALYSIS_TASK_AGENT_TYPE);
        assertThat(agentLlmService.model).isEqualTo("resolved-model");
        assertThat(agentLlmService.mcpToolContext).isSameAs(mcpToolContext);
        assertThat(agentLlmService.systemPrompt).contains("分析 Skill Prompt");
        assertThat(agentLlmService.userPrompt).contains("每日数据分析").contains("分析最近风险");
        assertThat(agentLlmService.modelCleared).isTrue();
        assertThat(agentLlmService.mcpContextCleared).isTrue();
    }

    @Test
    void detailResultUsesSharedChatMessagePartParser() {
        AnalysisTask task = new AnalysisTask()
                .setName("每日数据分析")
                .setResult("""
                        # 分析结论

                        ```java
                        System.out.println("risk");
                        ```

                        ```zenvis:data-analysis-record
                        {"recordId":"report-001","stage":"report_output","status":"completed","title":"分析报告","timeline":[{"title":"分析目标","content":"识别趋势"},{"title":"分析过程","content":"调用统计服务"},{"title":"分析结论","content":"发现波动"}]}
                        ```

                        ```zenvis:visualization-chart-preview
                        {"title":"风险趋势","option":{"xAxis":{"data":["今天"]},"series":[{"data":[1]}]}}
                        ```
                        """);

        AnalysisTaskServiceImpl service = new AnalysisTaskServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessagePartParser", new ChatMessagePartParser());

        AnalysisTaskVo detail = ReflectionTestUtils.invokeMethod(service, "toDetailVo", task);

        assertThat(detail).isNotNull();
        assertThat(detail.getResultParts())
                .extracting(part -> part.getType())
                .contains("markdown", "code", "data-analysis-record", "visualization-chart-preview");
    }

    @Test
    void detailResultHandlesEmptyHistoryAndListVoDoesNotIncludeParts() throws Exception {
        AnalysisTask task = new AnalysisTask().setName("历史任务").setResult(null);
        AnalysisTaskServiceImpl service = new AnalysisTaskServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessagePartParser", new ChatMessagePartParser());

        AnalysisTaskVo detail = ReflectionTestUtils.invokeMethod(service, "toDetailVo", task);
        AnalysisTaskVo listItem = ReflectionTestUtils.invokeMethod(service, "toVo", task);

        assertThat(detail).isNotNull();
        assertThat(detail.getResultParts()).isEmpty();
        assertThat(listItem).isNotNull();
        assertThat(listItem.getResultParts()).isNull();
        assertThat(JacksonConfig.OBJECT_MAPPER.writeValueAsString(detail))
                .contains("\"result_parts\":[]");
        assertThat(JacksonConfig.OBJECT_MAPPER.writeValueAsString(listItem))
                .doesNotContain("result_parts");
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

        @Override
        public McpToolContext resolve(String agentType, List<String> selectedSkillIds) {
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
        public String buildTaskSkillPrompt(String agentType, List<String> selectedSkillIds) {
            return "分析 Skill Prompt";
        }
    }
}
