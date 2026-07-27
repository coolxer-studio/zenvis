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
                          "agentTypes": ["agent_data_analysis"],
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
                          "agentTypes": ["agent_data_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "不应自动加载的提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt("agent_data_analysis", List.of("selected-skill"));

        assertThat(prompt)
                .contains("只应加载的提示词")
                .doesNotContain("不应自动加载的提示词");
    }

    @Test
    void loadsAndMergesOptionalSkillRuntimePolicyWithoutChangingLegacySkills() throws Exception {
        writeSkill(
                skillRoot.resolve("jmr-runtime"),
                """
                        {
                          "id": "jmr-runtime",
                          "name": "JMR",
                          "enabled": true,
                          "runtime": {
                            "promptMode": "skill_only",
                            "tools": {
                              "local": ["retrieval_search", "retrieval_list_attribute"],
                              "mcp": {
                                "jmr": ["dictionary_lookup", "payload_decode_base64", "ioc_lookup"]
                              }
                            },
                            "limits": {
                              "maxToolCalls": 16,
                              "maxRepeatedFailures": 2,
                              "maxToolResultChars": 12000,
                              "maxAccumulatedToolResultChars": 48000
                            }
                          },
                          "entry": "SKILL.md"
                        }
                        """,
                "JMR 提示词"
        );
        writeSkill(
                skillRoot.resolve("legacy"),
                """
                        {
                          "id": "legacy",
                          "name": "旧 Skill",
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "旧提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.resolveRuntimeConfig(List.of("legacy"))).isNull();
        assertThat(service.resolveRuntimeConfig(List.of("jmr-runtime")))
                .satisfies(runtime -> {
                    assertThat(runtime.getPromptMode()).isEqualTo("skill_only");
                    assertThat(runtime.getTools().getLocal())
                            .containsExactly("retrieval_search", "retrieval_list_attribute");
                    assertThat(runtime.getTools().getMcp().get("jmr"))
                            .containsExactly("dictionary_lookup", "payload_decode_base64", "ioc_lookup");
                    assertThat(runtime.getLimits().getMaxToolCalls()).isEqualTo(16);
                    assertThat(runtime.getLimits().getMaxRepeatedFailures()).isEqualTo(2);
                    assertThat(runtime.getLimits().getMaxToolResultChars()).isEqualTo(12000);
                    assertThat(runtime.getLimits().getMaxAccumulatedToolResultChars()).isEqualTo(48000);
                });
    }

    @Test
    void taskSelectionLoadsOnlySelectedSkillEvenWhenMatchingSkillExceedsPromptBudget() throws Exception {
        writeSkill(
                skillRoot.resolve("data-analysis-agent"),
                """
                        {
                          "id": "data-analysis-agent",
                          "name": "通用研判",
                          "enabled": true,
                          "agentTypes": ["agent_data_analysis"],
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
                          "agentTypes": ["agent_data_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "JMR 事件编号直接检索提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildTaskSkillPrompt(
                "agent_data_analysis",
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
                          "agentTypes": ["agent_data_analysis"],
                          "entry": "SKILL.md"
                        }
                        """,
                "停用提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThatThrownBy(() -> service.buildAgentSkillPrompt(
                "agent_data_analysis",
                List.of("disabled-skill", "missing-skill")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled-skill")
                .hasMessageContaining("missing-skill");
    }

    @Test
    void chatEntriesIncludeOnlyEnabledOptInSkillsAndResolveDefaults() throws Exception {
        writeSkill(
                skillRoot.resolve("analysis-chat-skill"),
                """
                        {
                          "id": "analysis-chat-skill",
                          "name": "专项研判",
                          "description": "专项研判说明",
                          "enabled": true,
                          "agentTypes": ["agent_data_analysis"],
                          "chat": {
                            "enabled": true,
                            "icon": "data-analysis",
                            "order": 20
                          },
                          "entry": "SKILL.md"
                        }
                        """,
                "专项研判提示词"
        );
        writeSkill(
                skillRoot.resolve("generic-chat-skill"),
                """
                        {
                          "id": "generic-chat-skill",
                          "name": "通用能力",
                          "enabled": true,
                          "chat": {
                            "enabled": true,
                            "label": "通用技能",
                            "order": 10
                          },
                          "entry": "SKILL.md"
                        }
                        """,
                "通用提示词"
        );
        writeSkill(
                skillRoot.resolve("hidden-skill"),
                """
                        {
                          "id": "hidden-skill",
                          "name": "后台能力",
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "不展示"
        );
        writeSkill(
                skillRoot.resolve("disabled-chat-skill"),
                """
                        {
                          "id": "disabled-chat-skill",
                          "name": "停用入口",
                          "enabled": false,
                          "chat": {"enabled": true},
                          "entry": "SKILL.md"
                        }
                        """,
                "停用提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getChatEntries(true))
                .extracting("skillId")
                .containsExactly("generic-chat-skill", "analysis-chat-skill");
        assertThat(service.getChatEntries(true).get(0))
                .satisfies(entry -> {
                    assertThat(entry.getChatType()).isEqualTo("skill:generic-chat-skill");
                    assertThat(entry.getAgentType()).isEqualTo(SkillService.GENERIC_SKILL_AGENT_TYPE);
                    assertThat(entry.getLabel()).isEqualTo("通用技能");
                    assertThat(entry.getIcon()).isEqualTo("magic-stick");
                });
        assertThat(service.getChatEntries(true).get(1))
                .satisfies(entry -> {
                    assertThat(entry.getChatType()).isEqualTo("skill:analysis-chat-skill");
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
                    assertThat(entry.getLabel()).isEqualTo("专项研判");
                });
        assertThat(service.requireEnabledChatEntry("skill:analysis-chat-skill").getSkillId())
                .isEqualTo("analysis-chat-skill");
        assertThatThrownBy(() -> service.requireEnabledChatEntry("skill:disabled-chat-skill"))
                .hasMessageContaining("已停用或不存在");
    }

    @Test
    void builtinChatSkillKeepsBuiltinAgentType() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-analysis-agent");
        writeSkill(
                skillRoot.resolve("data-analysis-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getChatEntries(true))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getChatType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
                });
    }

    @Test
    void installedPluginDataAnalysisSkillIsVisibleAsDynamicChatEntry() throws Exception {
        Path repoSkill = Path.of(
                "../deploy/open_config/skill_config/plugins/com.coolxer.plugin.jmr/"
                        + "jmr-continuous-threat-analysis"
        );
        writeSkill(
                skillRoot.resolve("plugins")
                        .resolve("com.coolxer.plugin.jmr")
                        .resolve("jmr-continuous-threat-analysis"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getChatEntries(true))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSkillId()).isEqualTo("jmr-continuous-threat-analysis");
                    assertThat(entry.getChatType()).isEqualTo("skill:jmr-continuous-threat-analysis");
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
                    assertThat(entry.getLabel()).isEqualTo("僵木蠕研判");
                });
    }

    @Test
    void selectedSkillPromptRejectsContentOverConfiguredLimitWithoutTruncating() throws Exception {
        writeSkill(
                skillRoot.resolve("oversized-skill"),
                """
                        {
                          "id": "oversized-skill",
                          "name": "超长能力",
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "完整提示词".repeat(100)
        );

        SkillService service = newSkillService();
        ReflectionTestUtils.setField(service, "maxSelectedPromptChars", 100);
        service.reload();

        assertThatThrownBy(() -> service.buildAgentSkillPrompt("agent_skill", List.of("oversized-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过上限 100")
                .hasMessageContaining("oversized-skill");
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
                BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS,
                BuiltinAgentSkillRegistry.AGENT_CONFIG_MANAGEMENT,
                BuiltinAgentSkillRegistry.AGENT_REPORT
        )).allSatisfy(agentType -> assertThat(service.isBuiltinAgentType(agentType)).isTrue());
    }

    @Test
    void builtinDataAccessSkillDocumentsCheckedMetadataAndVectumWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-access-agent");
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
                .contains("config_add")
                .contains("config_apply")
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
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-visualization-agent");
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
                .contains("entity_distribution")
                .contains("entity_trend")
                .contains("config_ensure_root")
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
                .doesNotContain("config_modify")
                .doesNotContain("menu_delete")
                .doesNotContain("dashboard_delete");
    }

    @Test
    void builtinDataAnalysisSkillDocumentsThreeStageAndContinuousWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-analysis-agent");
        writeSkill(
                skillRoot.resolve("data-analysis-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);

        assertThat(prompt)
                .contains("数据集准备")
                .contains("分析服务")
                .contains("分析报告")
                .contains("持续分析任务")
                .contains("Retrieval/Entity MCP")
                .contains("analysis.confirm_dataset")
                .contains("analysis.confirm_service_result")
                .contains("analysis.create_continuous_task")
                .contains("zenvis:data-analysis-record")
                .contains("zenvis:notice")
                .contains("不得生成分析结论");
    }

    @Test
    void builtinConfigManagementSkillDocumentsGenericConfigurationWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/config-management-agent");
        writeSkill(
                skillRoot.resolve("config-management-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_CONFIG_MANAGEMENT);

        assertThat(prompt)
                .contains("配置生成")
                .contains("试验场验证")
                .contains("正式生效")
                .contains("config_schema")
                .contains("config_tree")
                .contains("config_read")
                .contains("config_validate")
                .contains("config_ensure_root")
                .contains("config_add")
                .contains("config_apply")
                .contains("zenvis:config-record")
                .contains("config.confirm_trial")
                .contains("config.confirm_apply")
                .contains("blocked")
                .contains("读回核验");
    }

    @Test
    void builtinReportSkillDocumentsReportDraftAndEditingWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/report-agent");
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
