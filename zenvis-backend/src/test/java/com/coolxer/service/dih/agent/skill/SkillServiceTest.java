package com.coolxer.service.dih.agent.skill;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dto.SkillSaveDto;
import com.coolxer.model.dih.dto.SkillSearchDto;
import com.coolxer.model.dih.vo.SkillVo;
import com.coolxer.service.dih.rag.RagKnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceTest {

    @TempDir
    Path skillRoot;

    @Test
    void pluginSkillUsesPackageSourceAndResolvesKnowledgeBaseCodes() throws Exception {
        writeSkill(
                skillRoot.resolve("plugins").resolve("com.acme.demo").resolve("tracehub-analysis"),
                """
                        {
                          "id": "tracehub-analysis",
                          "name": "TraceHub 研判",
                          "source": "spoofed-source",
                          "knowledgeBaseCodes": ["tracehub-kb"],
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "插件研判提示词"
        );
        RagKnowledgeBaseService ragService = mock(RagKnowledgeBaseService.class);
        when(ragService.resolveManagedKnowledgeBaseIds("com.acme.demo", List.of("tracehub-kb")))
                .thenReturn(List.of(7));
        when(ragService.getNameMap(any())).thenReturn(Map.of(7, "TraceHub 知识库"));

        SkillService service = newSkillService();
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseService", ragService);
        service.reload();

        SkillVo skill = service.detail("tracehub-analysis");
        assertThat(skill.getSource()).isEqualTo("com.acme.demo");
        assertThat(skill.getEditable()).isTrue();
        assertThat(skill.getCustom()).isFalse();
        assertThat(skill.getKnowledgeBaseCodes()).containsExactly("tracehub-kb");
        assertThat(skill.getKnowledgeBaseIds()).containsExactly(7);
        assertThat(skill.getKnowledgeBaseNames()).containsExactly("TraceHub 知识库");

        SkillSearchDto search = new SkillSearchDto();
        search.setSource("acme.demo");
        assertThat(service.getPageList(search).getRows())
                .extracting(SkillVo::getId)
                .containsExactly("tracehub-analysis");
    }

    @Test
    void editingPluginSkillKeepsPackageIdentityAndExplicitKnowledgeBaseBinding() throws Exception {
        Path skillDir = skillRoot.resolve("plugins").resolve("com.acme.demo").resolve("tracehub-analysis");
        writeSkill(
                skillDir,
                """
                        {
                          "id": "tracehub-analysis",
                          "name": "TraceHub 研判",
                          "knowledgeBaseCodes": ["tracehub-kb"],
                          "enabled": true,
                          "entry": "SKILL.md"
                        }
                        """,
                "插件研判提示词"
        );
        RagKnowledgeBaseService ragService = mock(RagKnowledgeBaseService.class);
        when(ragService.resolveManagedKnowledgeBaseIds("com.acme.demo", List.of("tracehub-kb")))
                .thenReturn(List.of(7));

        SkillService service = newSkillService();
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseService", ragService);
        service.reload();

        SkillSaveDto dto = new SkillSaveDto();
        dto.setName("管理员修改后的研判");
        dto.setDescription("临时修改");
        dto.setEnabled(true);
        dto.setKnowledgeBaseIds(List.of(42));
        dto.setPromptMode("skill_only");
        dto.setMaxOutputTokens(8192);
        SkillSaveDto.ServerToolsDto mcpTools = new SkillSaveDto.ServerToolsDto();
        mcpTools.setServer("tracehub");
        mcpTools.setTools(List.of("ioc_lookup", "ip_location_lookup"));
        dto.setMcpTools(List.of(mcpTools));
        service.update("tracehub-analysis", dto);

        SkillVo updated = service.detail("tracehub-analysis");
        assertThat(updated.getName()).isEqualTo("管理员修改后的研判");
        assertThat(updated.getSource()).isEqualTo("com.acme.demo");
        assertThat(updated.getEditable()).isTrue();
        assertThat(updated.getCustom()).isFalse();
        assertThat(updated.getKnowledgeBaseIds()).containsExactly(42);
        assertThat(updated.getRuntime().getPromptMode()).isEqualTo("skill_only");
        assertThat(updated.getRuntime().getTools().getMcp().get("tracehub"))
                .containsExactly("ioc_lookup", "ip_location_lookup");
        assertThat(updated.getRuntime().getLimits().getMaxOutputTokens()).isEqualTo(8192);

        String manifest = Files.readString(skillDir.resolve("skill.json"));
        assertThat(manifest)
                .doesNotContain("knowledgeBaseCodes")
                .contains("\"source\" : \"com.acme.demo\"")
                .contains("\"knowledgeBaseIds\" : [ 42 ]")
                .contains("\"maxOutputTokens\" : 8192");
    }

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
        assertThat(service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG))
                .doesNotContain("插件问答提示词");

        SkillSearchDto askSearch = new SkillSearchDto();
        askSearch.setAgentType("ask");
        assertThat(service.getPageList(askSearch).getRows())
                .extracting("id")
                .contains("custom-ask-skill");

        SkillSearchDto agentSearch = new SkillSearchDto();
        agentSearch.setAgentType(BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG);
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
                              "local": {
                                "retrieval": ["retrieval_search", "retrieval_list_attribute"]
                              },
                              "mcp": {
                                "jmr": ["dictionary_lookup", "payload_decode_base64", "ioc_lookup"]
                              }
                            },
                            "limits": {
                              "maxToolCalls": 16,
                              "maxRepeatedFailures": 2,
                              "maxToolResultChars": 12000,
                              "maxAccumulatedToolResultChars": 48000,
                              "maxAccumulatedToolResultTokens": 12000,
                              "maxOutputTokens": 8192
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
                    assertThat(runtime.getTools().getLocal().get("retrieval"))
                            .containsExactly("retrieval_search", "retrieval_list_attribute");
                    assertThat(runtime.getTools().getMcp().get("jmr"))
                            .containsExactly("dictionary_lookup", "payload_decode_base64", "ioc_lookup");
                    assertThat(runtime.getLimits().getMaxToolCalls()).isEqualTo(16);
                    assertThat(runtime.getLimits().getMaxRepeatedFailures()).isEqualTo(2);
                    assertThat(runtime.getLimits().getMaxToolResultChars()).isEqualTo(12000);
                    assertThat(runtime.getLimits().getMaxAccumulatedToolResultChars()).isEqualTo(48000);
                    assertThat(runtime.getLimits().getMaxAccumulatedToolResultTokens()).isEqualTo(12000);
                    assertThat(runtime.getLimits().getMaxOutputTokens()).isEqualTo(8192);
                });
    }

    @Test
    void rejectsLegacyOrInvalidLocalServiceMappingsAndAcceptsWildcard() throws Exception {
        writeSkill(
                skillRoot.resolve("legacy-local-array"),
                """
                        {"id":"legacy-local-array","name":"legacy","enabled":true,
                         "runtime":{"tools":{"local":["retrieval_search"]}},"entry":"SKILL.md"}
                        """,
                "legacy"
        );
        writeSkill(
                skillRoot.resolve("unknown-local-service"),
                """
                        {"id":"unknown-local-service","name":"unknown","enabled":true,
                         "runtime":{"tools":{"local":{"unknown":["retrieval_search"]}}},"entry":"SKILL.md"}
                        """,
                "unknown"
        );
        writeSkill(
                skillRoot.resolve("wrong-service-tool"),
                """
                        {"id":"wrong-service-tool","name":"wrong","enabled":true,
                         "runtime":{"tools":{"local":{"entity":["retrieval_search"]}}},"entry":"SKILL.md"}
                        """,
                "wrong"
        );
        writeSkill(
                skillRoot.resolve("wildcard-service"),
                """
                        {"id":"wildcard-service","name":"wildcard","enabled":true,
                         "runtime":{"tools":{"local":{"retrieval":["*"]}}},"entry":"SKILL.md"}
                        """,
                "wildcard"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getPageList(new SkillSearchDto()).getRows())
                .extracting(SkillVo::getId)
                .containsExactly("wildcard-service");
        assertThat(service.resolveRuntimeConfig(List.of("wildcard-service"))
                .getTools().getLocal().get("retrieval")).containsExactly("*");
    }

    @Test
    void mergesLocalToolsByServiceAndUsesStrictestPositiveLimits() throws Exception {
        writeSkill(
                skillRoot.resolve("retrieval-a"),
                """
                        {"id":"retrieval-a","name":"A","enabled":true,
                         "runtime":{"tools":{"local":{"retrieval":["retrieval_search"]}},
                         "limits":{"maxToolCalls":12,"maxToolResultChars":12000,"maxOutputTokens":8192}},
                         "entry":"SKILL.md"}
                        """,
                "A"
        );
        writeSkill(
                skillRoot.resolve("retrieval-b"),
                """
                        {"id":"retrieval-b","name":"B","enabled":true,
                         "runtime":{"tools":{"local":{"retrieval":["entity_view"],
                         "config":["config_read"]}},
                         "limits":{"maxToolCalls":8,"maxToolResultChars":16000,"maxOutputTokens":4096}},
                         "entry":"SKILL.md"}
                        """,
                "B"
        );

        SkillService service = newSkillService();
        service.reload();

        var runtime = service.resolveRuntimeConfig(List.of("retrieval-a", "retrieval-b"));
        assertThat(runtime.getTools().getLocal().get("retrieval"))
                .containsExactly("retrieval_search", "entity_view");
        assertThat(runtime.getTools().getLocal().get("config"))
                .containsExactly("config_read");
        assertThat(runtime.getLimits().getMaxToolCalls()).isEqualTo(8);
        assertThat(runtime.getLimits().getMaxToolResultChars()).isEqualTo(12_000);
        assertThat(runtime.getLimits().getMaxOutputTokens()).isEqualTo(4096);
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
        Path repoSkill = Path.of("../deploy/open_config/skill_config/visualization-chart-agent");
        writeSkill(
                skillRoot.resolve("visualization-chart-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getChatEntries(true))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getChatType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_CHART);
                    assertThat(entry.getAgentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_CHART);
                });
    }

    @Test
    void installedPluginDataAnalysisSkillIsVisibleAsDynamicChatEntry() throws Exception {
        writeSkill(
                skillRoot.resolve("plugins")
                        .resolve("com.coolxer.plugin.jmr")
                        .resolve("jmr-continuous-threat-analysis"),
                """
                        {
                          "id": "jmr-continuous-threat-analysis",
                          "name": "僵木蠕研判",
                          "enabled": true,
                          "agentTypes": ["agent_skill"],
                          "chat": {
                            "enabled": true,
                            "label": "僵木蠕研判"
                          },
                          "entry": "SKILL.md"
                        }
                        """,
                "僵木蠕研判提示词"
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
                skillRoot.resolve("metadata-config-agent"),
                """
                        {
                          "id": "metadata-config-agent",
                          "name": "元数据配置",
                          "enabled": false,
                          "agentTypes": ["agent_metadata_config"],
                          "entry": "SKILL.md"
                        }
                        """,
                "停用的内置 Skill 提示词"
        );

        SkillService service = newSkillService();
        service.reload();

        assertThat(service.getBuiltinAgentSkills(true))
                .extracting("agentType")
                .doesNotContain(BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG);
        assertThat(service.getBuiltinAgentSkills(null))
                .filteredOn(agent -> BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG
                        .equals(agent.getAgentType()))
                .singleElement()
                .satisfies(agent -> assertThat(agent.getEnabled()).isFalse());
        assertThat(service.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG))
                .doesNotContain("停用的内置 Skill 提示词");
    }

    @Test
    void allBuiltinAgentTypesAreRecognized() {
        SkillService service = newSkillService();

        assertThat(List.of(
                BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG,
                BuiltinAgentSkillRegistry.AGENT_DATA_PUSH_SERVICE,
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_CHART,
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_APP,
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_DASHBOARD,
                BuiltinAgentSkillRegistry.AGENT_REPORT
        )).allSatisfy(agentType -> assertThat(service.isBuiltinAgentType(agentType)).isTrue());
    }

    @Test
    void builtinMetadataSkillDocumentsOnlyMetadataWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/metadata-config-agent");
        writeSkill(
                skillRoot.resolve("metadata-config-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_METADATA_CONFIG,
                List.of("metadata-config-agent")
        );

        assertThat(prompt)
                .contains("元数据配置智能体")
                .contains("METADATA_INPUT_COLLECTION")
                .contains("META_OVERWRITE_CONFIRMATION")
                .contains("config_tree")
                .contains("config_add")
                .contains("config_apply")
                .contains("config_read")
                .contains("zenvis:meta-config-record")
                .doesNotContain(
                        "数据推送服务分支",
                        "意图路由（最高优先级）",
                        "允许跳过元数据配置创建",
                        "PUSH_INPUT_COLLECTION",
                        "push_task_repair_and_restart");

        var runtime = service.resolveRuntimeConfig(List.of("metadata-config-agent"));
        assertThat(runtime).isNotNull();
        assertThat(runtime.getPromptMode()).isNull();
        assertThat(runtime.getTools().getLocal().get("config")).containsExactly(
                "config_tree",
                "config_read",
                "config_add",
                "config_apply"
        );
        assertThat(runtime.getTools().getLocal()).doesNotContainKey("push-task");
        assertThat(runtime.getLimits().getMaxToolCalls()).isEqualTo(32);
        assertThat(runtime.getLimits().getMaxRepeatedFailures()).isEqualTo(2);
        assertThat(runtime.getLimits().getMaxToolResultChars()).isEqualTo(8_000);
        assertThat(runtime.getLimits().getMaxAccumulatedToolResultChars()).isEqualTo(64_000);
        assertThat(runtime.getLimits().getMaxAccumulatedToolResultTokens()).isEqualTo(48_000);
    }

    @Test
    void builtinDataPushSkillDocumentsOnlyPushWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/data-push-service-agent");
        writeSkill(
                skillRoot.resolve("data-push-service-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_DATA_PUSH_SERVICE,
                List.of("data-push-service-agent")
        );

        assertThat(prompt)
                .contains("数据推送服务智能体")
                .contains("Meta 只读契约（最高优先级）")
                .contains("kind=missing_meta")
                .contains("agent_metadata_config")
                .contains("PUSH_INPUT_COLLECTION")
                .contains("push_task_detect_format")
                .contains("push_task_list_by_source_mark")
                .contains("push_task_create_and_start")
                .contains("push_task_get_log")
                .contains("push_task_repair_and_restart")
                .contains("最多 5 轮")
                .contains("zenvis:vectum-task-record")
                .doesNotContain(
                        "意图路由（最高优先级）",
                        "允许跳过元数据配置创建",
                        "METADATA_INPUT_COLLECTION");

        var runtime = service.resolveRuntimeConfig(List.of("data-push-service-agent"));
        assertThat(runtime).isNotNull();
        assertThat(runtime.getPromptMode()).isNull();
        assertThat(runtime.getTools().getLocal().get("config")).containsExactly(
                "config_tree",
                "config_read"
        );
        assertThat(runtime.getTools().getLocal().get("push-task")).containsExactly(
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
    void builtinVisualizationChartSkillDocumentsOnlyChartWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/visualization-chart-agent");
        writeSkill(
                skillRoot.resolve("visualization-chart-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_CHART,
                List.of("visualization-chart-agent"));

        assertThat(prompt)
                .contains("INTENT_CONFIRMATION")
                .contains("retrieval_list_display_entity")
                .contains("retrieval_search")
                .contains("entity_distribution")
                .contains("entity_trend")
                .contains("entity_aggregate")
                .contains("entity_histogram")
                .contains("entity_scatter")
                .contains("zenvis:visualization-chart-preview")
                .contains("zenvis:visualization-chart-record")
                .contains("禁止生成 SQL")
                .contains("visualization_chart.confirm_query_plan")
                .contains("visualization_chart.add_chart_library")
                .doesNotContain("zenvis:visualization-app-record")
                .doesNotContain("zenvis:visualization-dashboard-record");

        var runtime = service.resolveRuntimeConfig(List.of("visualization-chart-agent"));
        assertThat(runtime.getTools().getLocal()).containsOnlyKeys("retrieval");
        assertThat(runtime.getTools().getLocal().get("retrieval"))
                .doesNotContain("config_apply", "dashboard_create", "menu_create",
                        "push_task_create_and_start");
    }

    @Test
    void builtinVisualizationAppSkillDocumentsAppAndMenuWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/visualization-app-agent");
        writeSkill(
                skillRoot.resolve("visualization-app-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_APP,
                List.of("visualization-app-agent"));

        assertThat(prompt)
                .contains("低代码单页")
                .contains("侧边栏")
                .contains("静态 HTML")
                .contains("config_ensure_root")
                .contains("menu_create")
                .contains("zenvis:visualization-app-record")
                .contains("visualization_app.confirm_query_plan")
                .contains("visualization_app.confirm_plan")
                .contains("visualization_app.confirm_overwrite")
                .contains("visualization_app.confirm_menu")
                .doesNotContain("dashboard_create")
                .doesNotContain("```zenvis:visualization-chart-preview")
                .doesNotContain("visualization-dashboard-record");
    }

    @Test
    void builtinVisualizationDashboardSkillDocumentsDashboardAndMenuWorkflow() throws Exception {
        Path repoSkill = Path.of("../deploy/open_config/skill_config/visualization-dashboard-agent");
        writeSkill(
                skillRoot.resolve("visualization-dashboard-agent"),
                Files.readString(repoSkill.resolve("skill.json")),
                Files.readString(repoSkill.resolve("SKILL.md"))
        );

        SkillService service = newSkillService();
        service.reload();

        String prompt = service.buildAgentSkillPrompt(
                BuiltinAgentSkillRegistry.AGENT_VISUALIZATION_DASHBOARD,
                List.of("visualization-dashboard-agent"));

        assertThat(prompt)
                .contains("数据大屏")
                .contains("config_ensure_root")
                .contains("dashboard_create")
                .contains("dashboard_view")
                .contains("menu_create")
                .contains("zenvis:visualization-dashboard-record")
                .contains("visualization_dashboard.confirm_query_plan")
                .contains("visualization_dashboard.confirm_plan")
                .contains("visualization_dashboard.confirm_overwrite")
                .contains("visualization_dashboard.confirm_menu")
                .contains("不得写入图表库")
                .doesNotContain("```zenvis:visualization-chart-preview")
                .doesNotContain("visualization-app-record");
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
                .contains("report_upsert_section")
                .contains("report_finalize")
                .contains("`comparison` 只允许使用 `NONE` 或 `PREVIOUS_PERIOD`")
                .contains("不要为了写报表调用写入、删除、创建或执行类工具");
        assertThat(service.resolveRuntimeConfig(List.of("report-agent")).getLimits())
                .satisfies(limits -> {
                    assertThat(limits.getMaxToolCalls()).isEqualTo(60);
                    assertThat(limits.getMaxOutputTokens()).isEqualTo(4096);
                });
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
