package com.coolxer.web;

import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebServiceApplicationTests {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private AIChatService aiChatService;

	@Autowired
	private AgentMcpToolService agentMcpToolService;

	@Autowired
	private SkillService skillService;

	@Test
	void contextLoads() {

		stringRedisTemplate.opsForHash().entries("1");
		System.out.println(1);

	}

	@Test
	void splitAgentSkillsAndRealToolDefinitionsFitEmptyConversationBudget() throws Exception {
		List<String> metadataSkillIds = List.of("metadata-config-agent");
		McpToolContext metadataTools =
				agentMcpToolService.resolve(MetadataConfigAgent.AGENT_TYPE, metadataSkillIds);
		String metadataPrompt =
				skillService.buildAgentSkillPrompt(MetadataConfigAgent.AGENT_TYPE, metadataSkillIds)
						+ "\n\n" + metadataTools.systemPrompt();
		Method method = AIChatService.class.getDeclaredMethod(
				"prepareChatInput",
				String.class,
				String.class,
				String.class,
				ToolCallbackProvider.class,
				com.coolxer.service.dih.mcp.ToolRuntimeContext.class
		);
		method.setAccessible(true);

		Object prepared = method.invoke(
				aiChatService,
				"metadata-config-budget-test",
				metadataPrompt,
				"开始元数据配置",
				metadataTools.toolCallbackProvider(),
				metadataTools.toolRuntimeContext()
		);

		assertThat(prepared).isNotNull();
		assertThat(metadataTools.toolCallbackProvider().getToolCallbacks()).hasSize(8);

		List<String> pushSkillIds = List.of("data-push-service-agent");
		McpToolContext pushTools =
				agentMcpToolService.resolve(DataPushServiceAgent.AGENT_TYPE, pushSkillIds);
		String pushPrompt =
				skillService.buildAgentSkillPrompt(DataPushServiceAgent.AGENT_TYPE, pushSkillIds)
						+ "\n\n" + pushTools.systemPrompt();
		Object preparedPush = method.invoke(
				aiChatService,
				"data-push-budget-test",
				pushPrompt,
				"开始数据推送服务",
				pushTools.toolCallbackProvider(),
				pushTools.toolRuntimeContext()
		);

		assertThat(preparedPush).isNotNull();
		assertThat(pushTools.toolCallbackProvider().getToolCallbacks()).hasSize(8);
		assertThat(pushTools.toolRuntimeContext().maxAccumulatedToolResultChars()).isEqualTo(64_000);
		assertThat(pushTools.toolRuntimeContext().maxAccumulatedToolResultTokens()).isEqualTo(48_000);
	}
}
