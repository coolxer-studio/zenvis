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
                          "agentTypes": ["agent_report"],
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
                          "agentTypes": ["agent_report"],
                          "entry": "SKILL.md"
                        }
                        """,
                "不应自动加载的提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt("agent_report", List.of("selected-skill"));

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
                              "maxAccumulatedToolResultChars": 48000,
                              "maxAccumulatedToolResultTokens": 12000
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
                    assertThat(runtime.getLimits().getMaxAccumulatedToolResultTokens()).isEqualTo(12000);
                });
    }

    @Test
    void taskSelectionLoadsOnlySelectedSkillEvenWhenMatchingSkillExceedsPromptBudget() throws Exception {
        writeSkill(
                skillRoot.resolve("default-analysis-skill"),
                """
                        {
                          "id": "default-analysis-skill",
                          "name": "通用研判",
                          "enabled": true,
                          "agentTypes": ["agent_analysis_task"],
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
                          "agentTypes": ["agent_skill"],
                          "entry": "SKILL.md"
                        }
                        """,
                "JMR 事件编号直接检索提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildTaskSkillPrompt(
                "agent_analysis_task",
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
                          "agentTypes": ["agent_report"],
                          "entry": "SKILL.md"
                        }
                        """,
                "停用提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThatThrownBy(() -> service.buildAgentSkillPrompt(
                "agent_report",
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
                          "agentTypes": ["agent_report"],
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
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_REPORT);
                    assertThat(entry.getLabel()).isEqualTo("专项研判");
                });
        assertThat(service.requireEnabledChatEntry("skill:analysis-chat-skill").getSkillId())
                .isEqualTo("analysis-chat-skill");
        assertThatThrownBy(() -> service.requireEnabledChatEntry("skill:disabled-chat-skill"))
                .hasMessageContaining("已停用或不存在");
    }

    @Test
    void builtinChatSkillKeepsBuiltinAgentType() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-visualization-agent");
        writeSkill(
                skillRoot.resolve("data-visualization-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getChatEntries(true))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getChatType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION);
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION);
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
                    assertThat(entry.getAgentType()).isEqualTo(SkillService.GENERIC_SKILL_AGENT_TYPE);
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
    void allThreeBuiltinAgentTypesAreRecognized() {
        SkillService service = newSkillService();

        assertThat(List.of(
                BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS,
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
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

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_DATA_ACCESS,
                List.of("data-access-agent")
        );

        assertThat(prompt)
                .contains("创建元数据配置")
                .contains("数据推送服务分支")
                .contains("意图路由（最高优先级）")
                .contains("用户明确要求创建、添加、启动、修复、重启或查看数据推送服务")
                .contains("允许跳过元数据配置创建")
                .contains("直接进入“数据推送任务执行与自动修复状态机”")
                .contains("不得仅因缺少 Meta 配置而阻塞")
                .contains("Meta 尚未生成或应用不构成阻塞条件")
                .contains("确认回传以最近可见卡片的对象为准")
                .contains("不得改判 Meta 或以未生成 Meta 为由阻塞")
                .contains("演示示例审批边界（最高优先级）")
                .contains("后端确定性演示编排器完成全过程")
                .contains("不进入本 Skill 的模型执行循环")
                .contains("不依赖任何 AI 模型配置")
                .contains("`config_add` 和 `config_apply` 分别触发平台 MCP 审批")
                .contains("创建新任务时 `push_task_create_and_start` 触发平台 MCP 审批")
                .contains("不调用模型补救或编造结果")
                .contains("元数据配置执行状态机（最高优先级）")
                .contains("审批通过并得到工具返回值后，必须立即进入状态机下一步")
                .contains("禁止虚假进度")
                .contains("严禁声称“流程已启动”")
                .contains("元数据配置检查提醒")
                .contains("数据推送配置检查提醒")
                .contains("zenvis:notice")
                .contains("\"fileName\":\"<最终文件名>.json\"")
                .contains("\"configKind\":\"meta\"")
                .contains("config_add")
                .contains("config_apply")
                .contains("config_read")
                .contains("数据推送任务执行与自动修复状态机（最高优先级）")
                .contains("直接创建与运行诊断")
                .contains("用户配置优先")
                .contains("首次创建必须逐字使用该配置")
                .contains("不做启动前预检")
                .contains("创建后必查")
                .contains("无论返回 `true`、`false`、空值或异常")
                .contains("创建工具返回失败不等于任务未落库")
                .contains("push_task_get_log")
                .contains("push_task_repair_and_restart")
                .contains("历史任务优先")
                .contains("直接复用该 `taskId` 调用 `push_task_get_log`")
                .contains("不得为了再次取得同一个 ID 而先调用 `push_task_list_by_source_mark`")
                .contains("由真实查询/日志结果生成的完整诊断卡")
                .contains("sourceMark：<真实 sourceMark>")
                .contains("日志调用成功后补做无意义的列表查询")
                .contains("数据推送任务运行失败（第 <n>/5 轮）")
                .contains("数据推送任务日志（第 <n>/5 轮）")
                .contains("失败原因与配置修改（第 <n>/5 轮）")
                .contains("修复前置门槛")
                .contains("`taskId`、`sourceMark`、轮次、状态、失败阶段、日志类型、日志证据")
                .contains("配置路径、旧值、新值和日志依据")
                .contains("每次 `push_task_repair_and_restart` 调用必须对应且仅对应一个轮次")
                .contains("最终可见回复必须按轮次顺序重放每轮的三张完整卡片")
                .contains("先重放全部诊断卡，再输出成功记录")
                .contains("修复审批通过且工具返回后，必须在当前工具循环中直接复用该 `taskId`")
                .contains("最多自动修复 5 轮")
                .contains("DNS/网络不可达")
                .contains("状态严格为 `running`")
                .contains("最终必须调用 `push_task_get_log")
                .contains("Vectum 数据推送服务")
                .contains("Vector 仅作为 Vectum 任务配置");
        assertThat(prompt)
                .doesNotContain(
                        "工作固定为两步：创建元数据配置（必须）",
                        "元数据配置是必做步骤",
                        "只有 meta 元数据配置已生成并经用户选择添加/确认后",
                        "推送到 ZenVis ClickHouse 的字段必须与第一步已确认 meta 配置一致",
                        "哪个已确认实体",
                        "push_task_vector_",
                        "push_task_vector_capabilities",
                        "push_task_vector_component_schema",
                        "push_task_generate_config",
                        "push_task_validate_config",
                        "validationId",
                        "vectorVersion",
                        "schemaFingerprint",
                        "configFingerprint",
                        "metaFingerprint",
                        "testsStatus")
                .doesNotContain("menu_create")
                .doesNotContain("菜单 MCP")
                .doesNotContain("amis")
                .doesNotContain("可视化配置流程")
                .doesNotContain("validate_vector_config.sh");

        var runtime = service.resolveRuntimeConfig(List.of("data-access-agent"));
        assertThat(runtime).isNotNull();
        assertThat(runtime.getPromptMode()).isNull();
        assertThat(runtime.getTools().getLocal()).containsExactly(
                "config_tree",
                "config_add",
                "config_apply",
                "config_read",
                "push_task_detect_format",
                "push_task_list_by_source_mark",
                "push_task_create_and_start",
                "push_task_get_log",
                "push_task_repair_and_restart",
                "push_task_delete_by_source_mark"
        );
        assertThat(runtime.getTools().getMcp()).isEmpty();
        assertThat(runtime.getLimits().getMaxToolCalls()).isEqualTo(32);
        assertThat(runtime.getLimits().getMaxRepeatedFailures()).isEqualTo(2);
        assertThat(runtime.getLimits().getMaxToolResultChars()).isEqualTo(8_000);
        assertThat(runtime.getLimits().getMaxAccumulatedToolResultChars()).isEqualTo(64_000);
        assertThat(runtime.getLimits().getMaxAccumulatedToolResultTokens()).isEqualTo(48_000);
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
                .contains("entity_aggregate")
                .contains("entity_histogram")
                .contains("entity_scatter")
                .contains("config_ensure_root")
                .contains("dashboard_create")
                .contains("menu_create")
                .contains("示例入口处理规则")
                .contains("zenvis:visualization-chart-preview")
                .contains("zenvis:visualization-chart-record")
                .contains("zenvis:visualization-config-record")
                .contains("zenvis:dashboard-config-record")
                .contains("zenvis:menu-config-record")
                .contains("不生成 SQL")
                .contains("data_visualization.confirm_query_plan")
                .contains("data_visualization.add_chart_library")
                .contains("data_visualization.apply_config");
        assertThat(prompt)
                .doesNotContain("config_modify")
                .doesNotContain("使用系统预置的固定结果")
                .doesNotContain("menu_delete")
                .doesNotContain("dashboard_delete");
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
                .contains("汇总多来源结果")
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
