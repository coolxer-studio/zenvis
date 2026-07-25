package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.vo.SkillChatEntryVo;
import com.coolxer.service.dih.agent.AnalysisAgent;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DihChatExecutionBoundaryTest {

    @Test
    void askDeepThinkingUsesQaWithoutResolvingMcp() {
        Fixture fixture = fixture();
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(true)
        )).thenReturn(Flux.just("深度回答"));

        List<String> response = fixture.service.chat(
                chatDto("ask", true),
                null
        ).collectList().block();

        assertThat(response).containsExactly("深度回答");
        verifyNoInteractions(fixture.agentMcpToolService);
        verify(fixture.chatService).qaChat(
                "chat-1", "model-1", "问题", List.of(), null, true
        );
        verify(fixture.baseService).resolveChatModel("model-1", true, false);
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isTrue();
    }

    @Test
    void agentIgnoresDeepThinkingAndOnlyResolvesAgentMcp() {
        Fixture fixture = fixture();
        when(fixture.skillService.isBuiltinAgentEnabled(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS
        )).thenReturn(true);
        when(fixture.agentMcpToolService.resolve(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                List.of("analysis-agent")
        )).thenReturn(McpToolContext.empty());
        when(fixture.analysisAgent.chat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyList(), any(McpToolContext.class)
        )).thenReturn(Flux.just("智能体回答"));

        List<String> response = fixture.service.chat(
                chatDto(BuiltinAgentSkillRegistry.AGENT_ANALYSIS, true),
                null
        ).collectList().block();

        assertThat(response).containsExactly("智能体回答");
        verify(fixture.agentMcpToolService).resolve(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                List.of("analysis-agent")
        );
        verify(fixture.analysisAgent).chat(
                eq("chat-1"),
                eq("model-1"),
                eq("问题"),
                eq(List.of()),
                isNull(),
                eq(List.of("analysis-agent")),
                eq(McpToolContext.empty())
        );
        verify(fixture.baseService).resolveChatModel("model-1", false, false);
        verify(fixture.chatService, never()).qaChat(
                anyString(), anyString(), anyString(), anyList(), any(), anyBoolean()
        );
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isFalse();
    }

    @Test
    void dynamicSkillUsesInheritedAgentAndOnlySelectedSkill() {
        Fixture fixture = fixture();
        String chatType = "skill:jmr-analysis";
        when(fixture.skillService.requireEnabledChatEntry(chatType))
                .thenReturn(new SkillChatEntryVo(
                        "jmr-analysis",
                        chatType,
                        BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                        "僵木蠕研判",
                        "说明",
                        "data-analysis",
                        60
                ));
        when(fixture.agentMcpToolService.resolve(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                List.of("jmr-analysis")
        ))
                .thenReturn(McpToolContext.empty());
        when(fixture.analysisAgent.chat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyList(), any(McpToolContext.class)
        )).thenReturn(Flux.just("专项研判回答"));

        List<String> response = fixture.service.chat(chatDto(chatType, true), null).collectList().block();

        assertThat(response).containsExactly("专项研判回答");
        verify(fixture.agentMcpToolService).resolve(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS,
                List.of("jmr-analysis")
        );
        verify(fixture.analysisAgent).chat(
                eq("chat-1"),
                eq("model-1"),
                eq("问题"),
                eq(List.of()),
                isNull(),
                eq(List.of("jmr-analysis")),
                any(McpToolContext.class)
        );
        verify(fixture.skillService, never()).isBuiltinAgentEnabled(anyString());
        assertThat(capturedSessionDefaults(fixture).getType()).isEqualTo(chatType);
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isFalse();
    }

    @Test
    void genericDynamicSkillUsesNoMcpOrRag() {
        Fixture fixture = fixture();
        String chatType = "skill:generic";
        when(fixture.skillService.requireEnabledChatEntry(chatType))
                .thenReturn(new SkillChatEntryVo(
                        "generic",
                        chatType,
                        SkillService.GENERIC_SKILL_AGENT_TYPE,
                        "通用技能",
                        null,
                        "magic-stick",
                        1000
                ));
        when(fixture.skillService.buildAgentSkillPrompt(
                SkillService.GENERIC_SKILL_AGENT_TYPE,
                List.of("generic")
        )).thenReturn("通用技能完整提示词");
        when(fixture.chatService.agentChat(
                anyString(), anyString(), anyString(), anyString(), anyList(), isNull(), any(McpToolContext.class)
        )).thenReturn(Flux.just("通用技能回答"));

        List<String> response = fixture.service.chat(chatDto(chatType, false), null).collectList().block();

        assertThat(response).containsExactly("通用技能回答");
        verifyNoInteractions(fixture.agentMcpToolService);
        verify(fixture.chatService).agentChat(
                eq("chat-1"),
                eq("model-1"),
                org.mockito.ArgumentMatchers.contains("通用技能完整提示词"),
                eq("问题"),
                eq(List.of()),
                isNull(),
                any(McpToolContext.class)
        );
        verify(fixture.chatService, never()).qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyBoolean()
        );
    }

    @Test
    void legacyMcpAgentBecomesAskWithoutTools() {
        Fixture fixture = fixture();
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false)
        )).thenReturn(Flux.just("普通回答"));

        List<String> response = fixture.service.chat(
                chatDto("mcp_agent", false),
                null
        ).collectList().block();

        assertThat(response).containsExactly("普通回答");
        verifyNoInteractions(fixture.agentMcpToolService);
        assertThat(capturedSessionDefaults(fixture).getType()).isEqualTo("ask");
    }

    @Test
    void historicalLegacyMcpAgentAliasAlsoBecomesAskWithoutTools() {
        Fixture fixture = fixture();
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false)
        )).thenReturn(Flux.just("普通回答"));

        List<String> response = fixture.service.chat(
                chatDto("agent_mcp", false),
                null
        ).collectList().block();

        assertThat(response).containsExactly("普通回答");
        verifyNoInteractions(fixture.agentMcpToolService);
        assertThat(capturedSessionDefaults(fixture).getType()).isEqualTo("ask");
    }

    @Test
    void disabledBuiltinSkillReturnsExplicitCapabilityError() {
        Fixture fixture = fixture();
        when(fixture.skillService.isBuiltinAgentEnabled(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS
        )).thenReturn(false);

        List<String> response = fixture.service.chat(
                chatDto(BuiltinAgentSkillRegistry.AGENT_ANALYSIS, false),
                null
        ).collectList().block();

        assertThat(response).containsExactly(
                "智能体能力不可用：以下 Skill 不存在或未启用: analysis-agent"
        );
        verifyNoInteractions(fixture.agentMcpToolService);
        verify(fixture.baseService, never()).isModelSupported(any());
    }

    @Test
    void unknownChatTypeIsRejectedBeforeModelOrToolResolution() {
        Fixture fixture = fixture();

        List<String> response = fixture.service.chat(
                chatDto("unknown", false),
                null
        ).collectList().block();

        assertThat(response).containsExactly("会话类型不支持。");
        verifyNoInteractions(fixture.agentMcpToolService);
        verify(fixture.baseService, never()).isModelSupported(any());
    }

    private ChatSessionDto capturedSessionDefaults(Fixture fixture) {
        ArgumentCaptor<ChatSessionDto> captor = ArgumentCaptor.forClass(ChatSessionDto.class);
        verify(fixture.chatSessionService).appendMessage(
                eq("chat-1"),
                captor.capture(),
                any(Message.class),
                isNull()
        );
        return captor.getValue();
    }

    private ChatDto chatDto(String type, boolean deepThink) {
        ChatDto dto = new ChatDto();
        dto.setChatId("chat-1");
        dto.setModel("model-1");
        dto.setMessage("问题");
        dto.setType(type);
        dto.setDeepThink(deepThink);
        dto.setAttachments(List.of());
        return dto;
    }

    private Fixture fixture() {
        AIChatService chatService = mock(AIChatService.class);
        AIBaseService baseService = mock(AIBaseService.class);
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        ChatTitleService titleService = mock(ChatTitleService.class);
        AgentMcpToolService agentMcpToolService = mock(AgentMcpToolService.class);
        SkillService skillService = mock(SkillService.class);
        AnalysisAgent analysisAgent = mock(AnalysisAgent.class);
        ChatSession chatSession = mock(ChatSession.class);

        when(baseService.isModelSupported("model-1")).thenReturn(true);
        when(baseService.resolveChatModel(eq("model-1"), anyBoolean(), eq(false)))
                .thenReturn("model-1");
        when(attachmentService.hasImageAttachment(anyList())).thenReturn(false);
        when(attachmentService.appendAttachmentContext(eq("问题"), anyList(), isNull()))
                .thenReturn("问题");
        when(titleService.generateTitle("问题")).thenReturn("问题");
        when(chatSessionService.appendMessage(
                eq("chat-1"), any(ChatSessionDto.class), any(Message.class), isNull()
        )).thenReturn(chatSession);
        when(chatSessionService.appendMessage(
                eq(chatSession), any(Message.class), isNull()
        )).thenReturn(chatSession);

        DihChatApplicationService service = new DihChatApplicationService(
                chatService,
                baseService,
                chatSessionService,
                null,
                null,
                null,
                null,
                null,
                analysisAgent,
                null,
                null,
                null,
                null,
                null,
                attachmentService,
                titleService,
                agentMcpToolService,
                skillService,
                null,
                null,
                null,
                null
        );
        return new Fixture(
                service,
                chatService,
                baseService,
                chatSessionService,
                agentMcpToolService,
                skillService,
                analysisAgent
        );
    }

    private record Fixture(
            DihChatApplicationService service,
            AIChatService chatService,
            AIBaseService baseService,
            ChatSessionService chatSessionService,
            AgentMcpToolService agentMcpToolService,
            SkillService skillService,
            AnalysisAgent analysisAgent
    ) {
    }
}
