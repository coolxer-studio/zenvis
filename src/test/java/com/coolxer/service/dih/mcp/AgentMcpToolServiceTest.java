package com.coolxer.service.dih.mcp;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMcpToolServiceTest {

    @Test
    void resolveUsesLocalToolsWhenNoExternalMcpServerIsConnected() {
        ToolCallback localTool = new FakeToolCallback("policy_config_tree", "获取配置文件树");
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                new MockEnvironment(),
                ToolCallbackProvider.from(localTool)
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("policy_config_tree")
                .contains("zenvis:meta-config-record")
                .contains("绝不能作为工具调用");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .contains("policy_config_tree");
    }

    @Test
    void resolveReturnsEmptyWhenMcpIsGloballyDisabled() {
        Environment environment = new MockEnvironment()
                .withProperty("app.ai.mcp.enabled", "false");
        AgentMcpToolService service = new AgentMcpToolService(
                new EmptyMcpClientService(),
                environment,
                ToolCallbackProvider.from(new FakeToolCallback("policy_config_tree", "获取配置文件树"))
        );

        assertThat(service.resolve("agent_data_access").hasTools()).isFalse();
        assertThat(service.resolve("agent_data_visualization").hasTools()).isFalse();
    }

    @Test
    void resolveDataVisualizationAgentUsesRetrievalAndControlledWriteTools() {
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_write", "外部写入工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "根据条件检索数据"),
                        new FakeToolCallback("entity_view", "获取指定实体的单条记录详情"),
                        new FakeToolCallback("retrieval_create_rule", "创建一个新的检索规则"),
                        new FakeToolCallback("entity_update", "更新指定实体的记录"),
                        new FakeToolCallback("policy_config_apply", "应用配置"),
                        new FakeToolCallback("policy_config_delete", "删除配置"),
                        new FakeToolCallback("dashboard_create", "创建看板"),
                        new FakeToolCallback("dashboard_delete", "删除看板"),
                        new FakeToolCallback("menu_create", "创建菜单"),
                        new FakeToolCallback("menu_update", "更新菜单")
                )
        );

        McpToolContext context = service.resolve("agent_data_visualization");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("retrieval_search", "entity_view", "policy_config_apply", "dashboard_create", "menu_create")
                .doesNotContain("retrieval_create_rule", "entity_update", "policy_config_delete", "dashboard_delete", "menu_update", "external_write");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("retrieval_search", "entity_view", "policy_config_apply", "dashboard_create", "menu_create");
    }

    @Test
    void resolveDataVisualizationAgentCanUseControlledWriteToolsWithoutRetrievalTools() {
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_search", "外部查询工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("policy_config_tree", "获取配置文件树"),
                        new FakeToolCallback("dashboard_create", "创建看板")
                )
        );

        McpToolContext context = service.resolve("agent_data_visualization");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("dashboard_create");
    }

    @Test
    void resolveNonDataVisualizationAgentKeepsLocalAndExternalTools() {
        AgentMcpToolService service = new AgentMcpToolService(
                new ExternalMcpClientService(new FakeToolCallback("external_search", "外部查询工具")),
                new MockEnvironment(),
                ToolCallbackProvider.from(
                        new FakeToolCallback("retrieval_search", "根据条件检索数据"),
                        new FakeToolCallback("policy_config_tree", "获取配置文件树")
                )
        );

        McpToolContext context = service.resolve("agent_data_access");

        assertThat(context.hasTools()).isTrue();
        assertThat(context.systemPrompt())
                .contains("retrieval_search", "policy_config_tree", "external_search");
        assertThat(context.toolCallbackProvider().getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("retrieval_search", "policy_config_tree", "external_search");
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
