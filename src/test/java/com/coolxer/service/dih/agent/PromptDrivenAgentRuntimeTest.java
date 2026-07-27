package com.coolxer.service.dih.agent;

import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.AgentCapabilityUnavailableException;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptDrivenAgentRuntimeTest {

    @Test
    void explicitSkillsAndMcpArePassedToAgentChatWithoutQaRagPath() {
        AIChatService chatService = mock(AIChatService.class);
        SkillService skillService = mock(SkillService.class);
        when(skillService.buildAgentSkillPrompt("agent_data_analysis", List.of("data-analysis-agent")))
                .thenReturn("分析 Skill");
        when(chatService.agentChat(
                anyString(), anyString(), anyString(), anyString(), anyList(), isNull(), any(McpToolContext.class)
        )).thenReturn(Flux.just("完成"));
        PromptDrivenAgentRuntime runtime = new PromptDrivenAgentRuntime(chatService, skillService);

        List<String> response = runtime.chat(
                "agent_data_analysis",
                List.of("data-analysis-agent"),
                new PromptTemplate("系统提示"),
                "chat-1",
                "model-1",
                "分析问题",
                List.of(),
                null,
                McpToolContext.empty()
        ).collectList().block();

        assertThat(response).containsExactly("完成");
        verify(skillService).buildAgentSkillPrompt("agent_data_analysis", List.of("data-analysis-agent"));
        verify(chatService).agentChat(
                eq("chat-1"),
                eq("model-1"),
                eq("系统提示\n\n【已加载 Skill】\n分析 Skill"),
                eq("分析问题"),
                eq(List.of()),
                isNull(),
                eq(McpToolContext.empty())
        );
    }

    @Test
    void unavailableExplicitSkillReturnsUserFacingCapabilityError() {
        AIChatService chatService = mock(AIChatService.class);
        SkillService skillService = mock(SkillService.class);
        when(skillService.buildAgentSkillPrompt(anyString(), anyList()))
                .thenThrow(new IllegalArgumentException("以下 Skill 不存在或未启用: missing"));
        PromptDrivenAgentRuntime runtime = new PromptDrivenAgentRuntime(chatService, skillService);

        Flux<String> response = runtime.chat(
                "agent_data_analysis",
                List.of("missing"),
                new PromptTemplate("系统提示"),
                "chat-1",
                "model-1",
                "分析问题",
                List.of(),
                null,
                McpToolContext.empty()
        );

        assertThatThrownBy(response::blockLast)
                .isInstanceOf(AgentCapabilityUnavailableException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void skillOnlyRuntimeDoesNotInheritGenericAnalysisOrSandboxWorkflow() {
        AIChatService chatService = mock(AIChatService.class);
        SkillService skillService = mock(SkillService.class);
        when(skillService.buildAgentSkillPrompt(
                "agent_data_analysis", List.of("jmr-continuous-threat-analysis")))
                .thenReturn("JMR 0001–0004 专项流程");
        when(chatService.agentChat(
                anyString(), anyString(), anyString(), anyString(), anyList(), isNull(), any(McpToolContext.class)
        )).thenReturn(Flux.just("完成"));
        PromptDrivenAgentRuntime runtime = new PromptDrivenAgentRuntime(chatService, skillService);
        SkillRuntimeConfigVo runtimeConfig = new SkillRuntimeConfigVo();
        runtimeConfig.setPromptMode(SkillRuntimeConfigVo.PROMPT_MODE_SKILL_ONLY);
        McpToolContext toolContext = McpToolContext.empty(runtimeConfig);

        runtime.chat(
                "agent_data_analysis",
                List.of("jmr-continuous-threat-analysis"),
                new PromptTemplate("通用数据分析：数据集、分析服务、报告"),
                "chat-1",
                "model-1",
                "分析事件",
                List.of(),
                null,
                toolContext
        ).blockLast();

        org.mockito.ArgumentCaptor<String> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatService).agentChat(
                eq("chat-1"),
                eq("model-1"),
                promptCaptor.capture(),
                eq("分析事件"),
                eq(List.of()),
                isNull(),
                eq(toolContext)
        );
        assertThat(promptCaptor.getValue())
                .contains("专项 Skill 智能体", "JMR 0001–0004 专项流程")
                .doesNotContain("分析服务、报告");
    }
}
