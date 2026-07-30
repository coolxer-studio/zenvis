package com.coolxer.service.dih.mcp;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.model.dih.vo.SkillRuntimeToolsVo;
import com.coolxer.service.dih.agent.skill.SkillService;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMcpToolServiceTest {

    @Test
    void resolveUsesLocalToolsWhenNoExternalMcpServerIsConnected() {
        ToolCallback localTool = new FakeToolCallback("config_tree", "获取配置文件树");
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                new MockEnvironment(),
                ToolCallbackProvider.from(localTool)
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("config_tree")
                .contains("zenvis:meta-config-record")
                .contains("绝不能作为工具调用");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .contains("config_tree");
        assertThat(context.toolRuntimeContext()).isNotNull();
        assertThat(context.toolRuntimeContext().maxToolCalls()).isEqualTo(8);
        assertThat(context.toolRuntimeContext().maxRepeatedFailures()).isEqualTo(2);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultChars()).isEqualTo(24_000);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultTokens()).isEqualTo(12_000);
    }

    @Test
    void resolveUsesConfiguredPlatformLimitsWhenSkillDoesNotDeclareRuntime() {
        Environment environment = new MockEnvironment()
                .withProperty("app.ai.dih.agent.default-limits.max-tool-calls", "5")
                .withProperty("app.ai.dih.agent.default-limits.max-tool-result-chars", "4000")
                .withProperty("app.ai.dih.agent.default-limits.max-accumulated-tool-result-chars", "10000")
                .withProperty("app.ai.dih.agent.default-limits.max-accumulated-tool-result-tokens", "3000");
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                environment,
                ToolCallbackProvider.from(new FakeToolCallback("retrieval_search", "检索"))
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.toolRuntimeContext()).isNotNull();
        assertThat(context.toolRuntimeContext().maxToolCalls()).isEqualTo(5);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultChars()).isEqualTo(10_000);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultTokens()).isEqualTo(3_000);
    }

    @Test
    void resolveReturnsEmptyWhenMcpIsGloballyDisabled() {
        Environment environment = new MockEnvironment()
                .withProperty("app.ai.mcp.enabled", "false");
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                environment,
                ToolCallbackProvider.from(new FakeToolCallback("config_tree", "获取配置文件树"))
        );

        assertThat(service.resolve("agent_data_access").hasTools()).isFalse();
        assertThat(service.resolve("agent_data_visualization").hasTools()).isFalse();
    }

    @Test
    void resolveDataVisualizationAgentUsesRetrievalAndControlledWriteTools() {
        SkillService skillService = mock(SkillService.class);
        List<String> allowedTools = List.of(
                "retrieval_search", "entity_view",
                "entity_aggregate", "entity_histogram", "entity_scatter",
                "config_apply", "dashboard_create", "menu_create");
        when(skillService.resolveRuntimeConfig(List.of("data-visualization-agent")))
                .thenReturn(new SkillRuntimeConfigVo(
                        null,
                        new SkillRuntimeToolsVo(allowedTools, Map.of()),
                        new SkillRuntimeLimitsVo(8, 2, 8000, 24000, 12000)));
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_write", "外部写入工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "根据条件检索数据"),
                        new FakeToolCallback("entity_view", "获取指定实体的单条记录详情"),
                        new FakeToolCallback("entity_aggregate", "多维聚合"),
                        new FakeToolCallback("entity_histogram", "直方图"),
                        new FakeToolCallback("entity_scatter", "散点图"),
                        new FakeToolCallback("retrieval_create_rule", "创建一个新的检索规则"),
                        new FakeToolCallback("entity_update", "更新指定实体的记录"),
                        new FakeToolCallback("config_apply", "应用配置"),
                        new FakeToolCallback("config_delete", "删除配置"),
                        new FakeToolCallback("dashboard_create", "创建看板"),
                        new FakeToolCallback("dashboard_delete", "删除看板"),
                        new FakeToolCallback("menu_create", "创建菜单"),
                        new FakeToolCallback("menu_update", "更新菜单")
                ),
                null,
                skillService
        );

        McpToolContext context = service.resolve(
                "agent_data_visualization", List.of("data-visualization-agent"));

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("retrieval_search", "entity_view", "config_apply", "dashboard_create", "menu_create")
                .doesNotContain("retrieval_create_rule", "entity_update", "config_delete", "dashboard_delete", "menu_update", "external_write");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(
                        "retrieval_search", "entity_view",
                        "entity_aggregate", "entity_histogram", "entity_scatter",
                        "config_apply", "dashboard_create", "menu_create");
    }

    @Test
    void newVisualizationAnalyticsToolsAreHiddenFromOtherAgents() {
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "检索"),
                        new FakeToolCallback("entity_aggregate", "多维聚合"),
                        new FakeToolCallback("entity_histogram", "直方图"),
                        new FakeToolCallback("entity_scatter", "散点图"))
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("retrieval_search");
        assertThat(context.systemPrompt())
                .doesNotContain("entity_aggregate", "entity_histogram", "entity_scatter");
    }

    @Test
    void resolveDataVisualizationAgentCanUseControlledWriteToolsWithoutRetrievalTools() {
        SkillService skillService = mock(SkillService.class);
        when(skillService.resolveRuntimeConfig(List.of("data-visualization-agent")))
                .thenReturn(new SkillRuntimeConfigVo(
                        null,
                        new SkillRuntimeToolsVo(
                                List.of("config_tree", "dashboard_create"), Map.of()),
                        new SkillRuntimeLimitsVo(8, 2, 8000, 24000, 12000)));
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_search", "外部查询工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("config_tree", "获取配置文件树"),
                        new FakeToolCallback("dashboard_create", "创建看板")
                ),
                null,
                skillService
        );

        McpToolContext context = service.resolve(
                "agent_data_visualization", List.of("data-visualization-agent"));

        assertThat(context.hasTools()).isTrue();
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("config_tree", "dashboard_create");
    }

    @Test
    void resolveNonDataVisualizationAgentKeepsLocalAndExternalTools() {
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_search", "外部查询工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "根据条件检索数据"),
                        new FakeToolCallback("config_tree", "获取配置文件树")
                )
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("retrieval_search", "config_tree", "external_search");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("retrieval_search", "config_tree", "external_search");
    }

    @Test
    void resolveOmitsDeniedToolsAndMarksAskTools() {
        McpToolPolicyService policyService = mock(McpToolPolicyService.class);
        when(policyService.effectivePolicy(anyString(), any()))
                .thenAnswer(call -> call.getArgument(0, String.class).endsWith("write")
                        ? com.coolxer.commons.enums.McpApprovalPolicy.ASK
                        : com.coolxer.commons.enums.McpApprovalPolicy.DENY);
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("local_read", "read"),
                        new FakeToolCallback("local_write", "write")),
                policyService
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.systemPrompt())
                .contains("local_write", "调用前需要用户审批")
                .doesNotContain("local_read");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("local_write");
    }

    @Test
    void resolveSelectedSkillExposesOnlyItsFiveReadOnlyToolsWithoutLoopbackDuplicates() {
        SkillService skillService = mock(SkillService.class);
        SkillRuntimeConfigVo runtime = new SkillRuntimeConfigVo(
                SkillRuntimeConfigVo.PROMPT_MODE_SKILL_ONLY,
                new SkillRuntimeToolsVo(
                        List.of("retrieval_search", "retrieval_list_attribute"),
                        Map.of("jmr", List.of(
                                "dictionary_lookup",
                                "payload_decode_base64",
                                "ioc_lookup"))
                ),
                new SkillRuntimeLimitsVo(16, 2, 12000, 48000, 12000)
        );
        when(skillService.resolveRuntimeConfig(List.of("jmr-continuous-threat-analysis")))
                .thenReturn(runtime);
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(
                        new FakeToolCallback("jmr_dictionary_lookup", "批量字典"),
                        new FakeToolCallback("jmr_payload_decode_base64", "载荷解码"),
                        new FakeToolCallback("jmr_ioc_lookup", "IOC"),
                        new FakeToolCallback("zenvis_retrieval_search", "回环重复检索"),
                        new FakeToolCallback("external_write", "外部写入")
                ),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "根据条件检索数据"),
                        new FakeToolCallback("retrieval_list_attribute", "获取字段"),
                        new FakeToolCallback("retrieval_list_candidate", "数字字段候选"),
                        new FakeToolCallback("entity_update", "更新实体")
                ),
                null,
                skillService
        );

        McpToolContext context = service.resolve(
                "agent_skill",
                List.of("jmr-continuous-threat-analysis"));

        assertThat(context.hasTools()).isTrue();
        assertThat(context.skillRuntime()).isSameAs(runtime);
        assertThat(context.toolRuntimeContext()).isNotNull();
        assertThat(context.systemPrompt())
                .contains("retrieval_search", "retrieval_list_attribute")
                .contains("jmr_dictionary_lookup", "jmr_payload_decode_base64", "jmr_ioc_lookup")
                .doesNotContain(
                        "retrieval_list_candidate",
                        "entity_update",
                        "zenvis_retrieval_search",
                        "external_write");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(
                        "retrieval_search",
                        "retrieval_list_attribute",
                        "jmr_dictionary_lookup",
                        "jmr_payload_decode_base64",
                        "jmr_ioc_lookup");
    }

    @Test
    void resolveDataAccessSkillExposesOnlyConfiguredWorkflowTools() {
        List<String> allowedTools = List.of(
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
        SkillService skillService = mock(SkillService.class);
        SkillRuntimeConfigVo runtime = new SkillRuntimeConfigVo(
                null,
                new SkillRuntimeToolsVo(allowedTools, Map.of()),
                new SkillRuntimeLimitsVo(32, 2, 8000, 64000, 48000)
        );
        when(skillService.resolveRuntimeConfig(List.of("data-access-agent"))).thenReturn(runtime);
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_write", "外部写入")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("config_tree", "配置树"),
                        new FakeToolCallback("config_add", "新增配置"),
                        new FakeToolCallback("config_apply", "应用配置"),
                        new FakeToolCallback("config_read", "读取配置"),
                        new FakeToolCallback("push_task_detect_format", "检测格式"),
                        new FakeToolCallback("push_task_list_by_source_mark", "查询推送任务"),
                        new FakeToolCallback("push_task_create_and_start", "创建并启动推送任务"),
                        new FakeToolCallback("push_task_get_log", "读取推送任务日志"),
                        new FakeToolCallback("push_task_repair_and_restart", "修复并重启推送任务"),
                        new FakeToolCallback("push_task_delete_by_source_mark", "删除推送任务"),
                        new FakeToolCallback("entity_update", "更新实体"),
                        new FakeToolCallback("menu_create", "创建菜单")
                ),
                null,
                skillService
        );

        McpToolContext context = service.resolve(
                "agent_data_access",
                List.of("data-access-agent")
        );

        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyElementsOf(allowedTools);
        assertThat(context.systemPrompt())
                .contains(allowedTools.toArray(String[]::new))
                .doesNotContain(
                        "push_task_vector_capabilities",
                        "push_task_vector_component_schema",
                        "push_task_generate_config",
                        "push_task_validate_config",
                        "entity_update",
                        "menu_create",
                        "external_write");
        assertThat(context.toolRuntimeContext().maxToolCalls()).isEqualTo(32);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultChars()).isEqualTo(64_000);
        assertThat(context.toolRuntimeContext().maxAccumulatedToolResultTokens()).isEqualTo(48_000);
    }

    private record FakeToolCallback(String name, String description) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description(description)
                    .inputSchema("{}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return "{}";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

    private static class EmptyMcpClientService implements McpClientService {

        @Override
        public PageRowsVo<McpServerVo> getPageList(McpServerSearchDto searchDto) {
            return null;
        }

        @Override
        public McpServerVo create(McpServerDto dto) {
            return null;
        }

        @Override
        public Boolean update(Integer id, McpServerDto dto) {
            return false;
        }

        @Override
        public void delete(Integer id) {
        }

        @Override
        public McpServerVo info(Integer id) {
            return null;
        }

        @Override
        public McpServerVo setEnabled(Integer id, boolean enabled) {
            return null;
        }

        @Override
        public McpServerVo refresh(Integer id) {
            return null;
        }

        @Override
        public List<McpServerVo> refreshAll() {
            return List.of();
        }

        @Override
        public List<McpToolVo> listTools(Integer serverId) {
            return List.of();
        }

        @Override
        public Object callTool(McpToolCallDto callDto) {
            return null;
        }

        @Override
        public boolean hasAvailableTools() {
            return false;
        }

        @Override
        public boolean hasAvailableTools(List<String> serverCodes) {
            return false;
        }

        @Override
        public String buildEnabledMcpPrompt() {
            return "";
        }

        @Override
        public String buildEnabledMcpPrompt(List<String> serverCodes) {
            return "";
        }

        @Override
        public ToolCallbackProvider getToolCallbackProvider() {
            return ToolCallbackProvider.from();
        }

        @Override
        public ToolCallbackProvider getToolCallbackProvider(List<String> serverCodes) {
            return ToolCallbackProvider.from();
        }

        @Override
        public List<McpSyncClient> getActiveClients() {
            return List.of();
        }

        @Override
        public List<McpSyncClient> getActiveClients(List<String> serverCodes) {
            return List.of();
        }
    }

    private static class ExternalMcpClientService extends EmptyMcpClientService {

        private final ToolCallback[] toolCallbacks;

        private ExternalMcpClientService(ToolCallback... toolCallbacks) {
            this.toolCallbacks = toolCallbacks;
        }

        @Override
        public boolean hasAvailableTools(List<String> serverCodes) {
            return toolCallbacks.length > 0;
        }

        @Override
        public String buildEnabledMcpPrompt(List<String> serverCodes) {
            if (toolCallbacks.length == 0) {
                return "";
            }
            StringBuilder prompt = new StringBuilder("### MCP服务：外部服务 (external)\n");
            for (ToolCallback callback : toolCallbacks) {
                prompt.append("- ")
                        .append(callback.getToolDefinition().name())
                        .append("：")
                        .append(callback.getToolDefinition().description())
                        .append("\n");
            }
            return prompt.toString();
        }

        @Override
        public ToolCallbackProvider getToolCallbackProvider(List<String> serverCodes) {
            return ToolCallbackProvider.from(toolCallbacks);
        }
    }
}
