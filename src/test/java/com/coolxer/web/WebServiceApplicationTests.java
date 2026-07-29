package com.coolxer.web;

import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.agent.DataAccessAgent;
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
	void dataAccessSkillAndRealToolDefinitionsFitEmptyConversationBudget() throws Exception {
		List<String> skillIds = List.of("data-access-agent");
		McpToolContext toolContext =
				agentMcpToolService.resolve(DataAccessAgent.AGENT_TYPE, skillIds);
		String systemPrompt =
				skillService.buildAgentSkillPrompt(DataAccessAgent.AGENT_TYPE, skillIds)
						+ "\n\n" + toolContext.systemPrompt();
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
				"data-access-budget-test",
				systemPrompt,
				"开始数据接入",
				toolContext.toolCallbackProvider(),
				toolContext.toolRuntimeContext()
		);

		assertThat(prepared).isNotNull();
		assertThat(toolContext.toolCallbackProvider().getToolCallbacks()).hasSize(10);
		assertThat(toolContext.toolRuntimeContext().maxAccumulatedToolResultChars()).isEqualTo(64_000);
		assertThat(toolContext.toolRuntimeContext().maxAccumulatedToolResultTokens()).isEqualTo(48_000);
	}
}
