package com.coolxer.service.dih.agent.skill;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dto.SkillSearchDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillServiceTest {

    @TempDir
    Path skillRoot;

    @Test
    void pluginSkillWithoutAgentTypesDefaultsToAskOnly() throws Exception {
        writeSkill(
                skillRoot.resolve("plugins").resolve("com.acme.demo").resolve("custom-ask-skill"),
                """
                        {
                          "id": "custom-ask-skill",
                          "name": "自定义问答 Skill",
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "插件问答提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.buildEnabledSkillPrompt("ask")).contains("插件问答提示词");
        assertThat(service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS))
                .doesNotContain("插件问答提示词");

        SkillSearchDto askSearch = new SkillSearchDto();
        askSearch.setAgentType("ask");
        assertThat(service.getPageList(askSearch).getRows())
                .extracting("id")
                .contains("custom-ask-skill");

        SkillSearchDto agentSearch = new SkillSearchDto();
        agentSearch.setAgentType(BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS);
        assertThat(service.getPageList(agentSearch).getRows())
                .extracting("id")
                .doesNotContain("custom-ask-skill");
    }

    @Test
    void explicitAgentSkillsLoadOnlySelectedEnabledSkills() throws Exception {
        writeSkill(
                skillRoot.resolve("selected-skill"),
                """
                        {
                          "id": "selected-skill",
                          "name": "指定能力",
                          "enabled": true,
                          "agentTypes": ["agent_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "只应加载的提示词"
        );
        writeSkill(
                skillRoot.resolve("matching-but-not-selected"),
                """
                        {
                          "id": "matching-but-not-selected",
                          "name": "同类型附加能力",
                          "enabled": true,
                          "agentTypes": ["agent_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "不应自动加载的提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt("agent_analysis", List.of("selected-skill"));

        assertThat(prompt)
                .contains("只应加载的提示词")
                .doesNotContain("不应自动加载的提示词");
    }

    @Test
    void taskSelectionLoadsOnlySelectedSkillEvenWhenMatchingSkillExceedsPromptBudget() throws Exception {
        writeSkill(
                skillRoot.resolve("analysis-agent"),
                """
                        {
                          "id": "analysis-agent",
                          "name": "通用研判",
                          "enabled": true,
                          "agentTypes": ["agent_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "通用研判提示词".repeat(1000)
        );
        writeSkill(
                skillRoot.resolve("jmr-continuous-threat-analysis"),
                """
                        {
                          "id": "jmr-continuous-threat-analysis",
                          "name": "僵木蠕持续安全研判",
                          "enabled": true,
                          "agentTypes": ["agent_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "JMR 事件编号直接检索提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildTaskSkillPrompt(
                "agent_analysis",
                List.of("jmr-continuous-threat-analysis")
        );

        assertThat(prompt)
                .contains("JMR 事件编号直接检索提示词")
                .doesNotContain("通用研判提示词");
    }

    @Test
    void explicitAgentSkillsRejectMissingOrDisabledSkills() throws Exception {
        writeSkill(
                skillRoot.resolve("disabled-skill"),
                """
                        {
                          "id": "disabled-skill",
                          "name": "停用能力",
                          "enabled": false,
                          "agentTypes": ["agent_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "停用提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThatThrownBy(() -> service.buildAgentSkillPrompt(
                "agent_analysis",
                List.of("disabled-skill", "missing-skill")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled-skill")
                .hasMessageContaining("missing-skill");
    }

    @Test
    void disabledBuiltinSkillIsHiddenAndNotLoadedIntoPrompt() throws Exception {
        writeSkill(
                skillRoot.resolve("data-access-agent"),
                """
                        {
                          "id": "data-access-agent",
                          "name": "数据接入",
                          "enabled": false,
                          "agentTypes": ["agent_data_access"],
                          "entry": "SKILL.md"
                        }
                        """,
                "停用的内置 Skill 提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getBuiltinAgentSkills(true))
                .extracting("agentType")
                .doesNotContain(BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS);
        assertThat(service.getBuiltinAgentSkills(null))
                .filteredOn(agent -> BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS.equals(agent.getAgentType()))
                .singleElement()
                .satisfies(agent -> assertThat(agent.getEnabled()).isFalse());
        assertThat(service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS))
                .doesNotContain("停用的内置 Skill 提示词");
    }

    @Test
    void allFiveBuiltinAgentTypesAreRecognized() {
        SkillService service = newSkillService();

        assertThat(List.of(
                BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS,
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                BuiltinAgentSkillRegistry.AGENT_DISPOSE,
                BuiltinAgentSkillRegistry.AGENT_REPORT
        )).allSatisfy(agentType -> assertThat(service.isBuiltinAgentType(agentType)).isTrue());
    }

    @Test
    void builtinDataAccessSkillDocumentsCheckedMetadataAndVectumWorkflow() throws Exception {
        Path repoSkill = Path.of("../open_config/skill_config/data-access-agent");
        writeSkill(
                skillRoot.resolve("data-access-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS);

        assertThat(prompt)
                .contains("创建元数据配置")
                .contains("添加 Vectum 数据推送服务")
                .contains("元数据配置检查提醒")
                .contains("数据推送配置检查提醒")
                .contains("zenvis:notice")
                .contains("policy_config_add")
                .contains("policy_config_apply")
                .contains("Vectum 数据推送服务")
                .contains("Vector 仅作为 Vectum 任务配置");
        assertThat(prompt)
                .doesNotContain("menu_create")
                .doesNotContain("菜单 MCP")
                .doesNotContain("amis")
                .doesNotContain("可视化配置流程");
    }

    @Test
    void builtinDataVisualizationSkillDocumentsVisualizationWorkflow() throws Exception {
        Path repoSkill = Path.of("../open_config/skill_config/data-visualization-agent");
        writeSkill(
                skillRoot.resolve("data-visualization-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION);

        assertThat(prompt)
                .contains("意图确认")
                .contains("amis")
                .contains("静态 HTML")
                .contains("open_config")
                .contains("retrieval_list_display_entity")
                .contains("retrieval_search")
                .contains("entity_statistics")
                .contains("policy_config_ensure_root")
                .contains("dashboard_create")
                .contains("menu_create")
                .contains("内置演示示例处理规则")
                .contains("zenvis:visualization-chart-preview")
                .contains("zenvis:visualization-chart-record")
                .contains("zenvis:visualization-config-record")
                .contains("zenvis:dashboard-config-record")
                .contains("zenvis:menu-config-record")
                .contains("不生成 SQL")
                .contains("data_visualization.add_chart_library")
                .contains("data_visualization.apply_config");
        assertThat(prompt)
                .doesNotContain("policy_config_modify")
                .doesNotContain("menu_delete")
                .doesNotContain("dashboard_delete");
    }

    @Test
    void builtinAnalysisSkillDocumentsDirectAndContinuousWorkflow() throws Exception {
        Path repoSkill = Path.of("../open_config/skill_config/analysis-agent");
        writeSkill(
                skillRoot.resolve("analysis-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_ANALYSIS);

        assertThat(prompt)
                .contains("一次性研判分析")
                .contains("持续分析任务")
                .contains("Retrieval MCP")
                .contains("retrieval_list_display_entity")
                .contains("retrieval_search")
                .contains("entity_statistics")
                .contains("analysis.start")
                .contains("analysis.create_continuous_task")
                .contains("zenvis:continuous-analysis-task-config")
                .contains("zenvis:disposal-strategy-config")
                .contains("push_task_create_and_start")
                .contains("push_task_list_by_source_mark")
                .contains("analysis_task_create")
                .contains("analysis_task_queue_status");
    }

    @Test
    void builtinDisposeSkillDocumentsPolicyWorkflow() throws Exception {
        Path repoSkill = Path.of("../open_config/skill_config/dispose-agent");
        writeSkill(
                skillRoot.resolve("dispose-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DISPOSE);

        assertThat(prompt)
                .contains("采集/检测策略")
                .contains("标记/评分策略")
                .contains("处置策略")
                .contains("policy_config_schema")
                .contains("policy_config_tree")
                .contains("policy_config_read")
                .contains("policy_config_validate")
                .contains("policy_config_simulate")
                .contains("policy_config_ensure_root")
                .contains("policy_config_apply")
                .contains("zenvis:collection-policy-config")
                .contains("zenvis:tagging-policy-config")
                .contains("zenvis:disposal-policy-config")
                .contains("policy.apply_to_production")
                .contains("需求映射")
                .contains("回滚建议");
    }

    @Test
    void builtinReportSkillDocumentsReportDraftAndEditingWorkflow() throws Exception {
        Path repoSkill = Path.of("../open_config/skill_config/report-agent");
        writeSkill(
                skillRoot.resolve("report-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_REPORT);

        assertThat(prompt)
                .contains("报表制作智能体")
                .contains("新建报表")
                .contains("修改报表")
                .contains("汇总多智能体结果")
                .contains("retrieval_search")
                .contains("entity_trend")
                .contains("analysis_task_view")
                .contains("zenvis:report-document-config")
                .contains("证据记录与查询结果")
                .contains("自然语言修改")
                .contains("不要为了写报表调用写入、删除、创建或执行类工具");
    }

    private SkillService newSkillService() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "skillPath", skillRoot.toString());
        return new SkillService(customWebConfig, JacksonConfig.OBJECT_MAPPER.copy());
    }

    private static void writeSkill(Path skillDir, String manifest, String content) throws Exception {
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.json"), manifest);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }
}
