package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.vo.SkillChatEntryVo;
import com.coolxer.model.dih.vo.SkillRuntimeConfigVo;
import com.coolxer.model.dih.vo.SkillRuntimeToolsVo;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.attachment.AttachmentToolProviderFactory;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.coolxer.service.dih.ContextCompactionObserver;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(true),
                any(McpToolContext.class)
        )).thenReturn(Flux.just("深度回答"));

        List<String> response = fixture.service.chat(
                chatDto("ask", true),
                null
        ).collectList().block();

        assertThat(response).containsExactly("深度回答");
        verifyNoInteractions(fixture.agentMcpToolService);
        verify(fixture.chatService).qaChat(
                "chat-1", "model-1", "问题", List.of(), null, true, McpToolContext.empty()
        );
        verify(fixture.baseService).resolveChatModel("model-1", true, false);
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isTrue();
    }

    @Test
    void askWithAttachmentEmitsToolCallEventAndKeepsBusinessMcpDisabled() {
        Fixture fixture = fixture();
        ChatAttachment attachment = ChatAttachment.builder()
                .fileId("3ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("report.pdf")
                .parseStatus("ready")
                .build();
        ToolCallback attachmentList = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("attachment_list")
                        .description("列出附件")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"status\":\"ok\",\"data\":{\"count\":1}}";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
        AttachmentToolProviderFactory.AttachmentToolBundle bundle =
                new AttachmentToolProviderFactory.AttachmentToolBundle(
                        ToolCallbackProvider.from(attachmentList),
                        "附件工具提示",
                        List.of(attachment.getFileId()),
                        null);
        when(fixture.attachmentToolProviderFactory.create(
                isNull(), eq("chat-1"), isNull(), anyList())).thenReturn(bundle);
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false),
                any(McpToolContext.class)))
                .thenAnswer(invocation -> {
                    McpToolContext context = invocation.getArgument(6);
                    ToolCallback callback = context.toolCallbackProvider().getToolCallbacks()[0];
                    assertThat(callback.call("{}")).contains("\"count\":1");
                    return Flux.just("附件回答");
                });
        ChatDto dto = chatDto("ask", false);
        dto.setAttachments(List.of(attachment));
        dto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        List<String> response = fixture.service.chat(dto, null).collectList().block();

        assertThat(response).isNotNull();
        assertThat(response).anySatisfy(chunk -> assertThat(chunk)
                .contains("\"event\":\"tool_call\"", "attachment_list", "\"status\":\"succeeded\""));
        assertThat(String.join("", response)).contains("附件回答");
        verifyNoInteractions(fixture.agentMcpToolService);
    }

    @Test
    void imageEvidenceRunsBeforeToolizedAttachmentAnswerWithoutNativeImageToolConflict() {
        Fixture fixture = fixture();
        ChatAttachment image = ChatAttachment.builder()
                .fileId("4ab0c6d7-2907-45a3-bd8f-8f9754b386dd")
                .fileName("chart.png")
                .contentType("image/png")
                .kind("image")
                .parseStatus("ready")
                .build();
        ToolCallback attachmentList = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("attachment_list")
                        .description("列出附件")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{\"status\":\"ok\"}";
            }
        };
        AttachmentToolProviderFactory.AttachmentToolBundle bundle =
                new AttachmentToolProviderFactory.AttachmentToolBundle(
                        ToolCallbackProvider.from(attachmentList),
                        "附件工具提示",
                        List.of(image.getFileId()),
                        null);
        when(fixture.attachmentToolProviderFactory.create(
                isNull(), eq("chat-1"), isNull(), anyList())).thenReturn(bundle);
        when(fixture.attachmentService.hasImageAttachment(anyList())).thenReturn(true);
        when(fixture.baseService.resolveChatModel("model-1", false, true))
                .thenReturn("vision-model");
        when(fixture.chatService.analyzeImageAttachments(
                any(), any(), any(), anyList(), any()))
                .thenReturn(Mono.just("图中显示收入 18%"));
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false),
                any(McpToolContext.class)))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(2);
                    assertThat(prompt).contains("平台图片取证阶段结果", "图中显示收入 18%");
                    return Flux.just("结合图片与附件的回答");
                });
        ChatDto dto = chatDto("ask", false);
        dto.setAttachments(List.of(image));

        List<String> response = fixture.service.chat(dto, null).collectList().block();

        assertThat(response).contains("结合图片与附件的回答");
        verify(fixture.chatService).analyzeImageAttachments(
                eq("chat-1"), eq("vision-model"), eq("问题"), eq(List.of(image)), isNull());
    }

    @Test
    void agentDispatchResolvesAutoModelInsteadOfSendingItToProvider() {
        Fixture fixture = fixture();
        when(fixture.baseService.resolveChatModel("auto", false, false))
                .thenReturn("deepseek-v4-flash");

        assertThat(fixture.service.resolveDispatchModel("auto", false, chatDto("ask", false)))
                .isEqualTo("deepseek-v4-flash");
        assertThat(fixture.service.resolveDispatchModel("model-1", false, chatDto("ask", false)))
                .isEqualTo("model-1");
        verify(fixture.baseService).resolveChatModel("auto", false, false);
        verify(fixture.baseService, never()).resolveChatModel("model-1", false, false);
    }

    @Test
    void agentIgnoresDeepThinkingAndOnlyResolvesAgentMcp() {
        Fixture fixture = fixture();
        when(fixture.skillService.isBuiltinAgentEnabled(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION
        )).thenReturn(true);
        when(fixture.agentMcpToolService.resolve(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                List.of("data-visualization-agent")
        )).thenReturn(McpToolContext.empty());
        when(fixture.visualizationAgent.chat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyList(),
                any(McpToolContext.class), any(ContextCompactionObserver.class)
        )).thenReturn(Flux.just("智能体回答"));

        List<String> response = fixture.service.chat(
                chatDto(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION, true),
                null
        ).collectList().block();

        assertThat(response).containsExactly("智能体回答");
        verify(fixture.agentMcpToolService).resolve(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                List.of("data-visualization-agent")
        );
        verify(fixture.visualizationAgent).chat(
                eq("chat-1"),
                eq("model-1"),
                argThat(prompt -> prompt.startsWith("问题")
                        && prompt.contains("普通数据可视化请求没有可用的 MCP 工具")),
                eq(List.of()),
                isNull(),
                eq(List.of("data-visualization-agent")),
                eq(McpToolContext.empty()),
                any(ContextCompactionObserver.class)
        );
        verify(fixture.baseService).resolveChatModel("model-1", false, false);
        verify(fixture.chatService, never()).qaChat(
                anyString(), anyString(), anyString(), anyList(), any(), anyBoolean(),
                any(McpToolContext.class)
        );
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isFalse();
    }

    @Test
    void visualizationAgentBootstrapsEntityMetaThroughVisibleMcpCall() {
        Fixture fixture = fixture();
        AtomicInteger calls = new AtomicInteger();
        ToolCallback entityMetaTool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("retrieval_list_display_entity")
                        .description("获取展示用实体Meta列表")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                calls.incrementAndGet();
                return "{\"entityList\":[{\"name\":\"probe_agent_message\","
                        + "\"label\":\"探针消息\"}]}";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
        McpToolContext toolContext = new McpToolContext(
                ToolCallbackProvider.from(entityMetaTool),
                "允许 retrieval_list_display_entity"
        );
        when(fixture.skillService.isBuiltinAgentEnabled(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION
        )).thenReturn(true);
        when(fixture.agentMcpToolService.resolve(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                List.of("data-visualization-agent")
        )).thenReturn(toolContext);
        when(fixture.visualizationAgent.chat(
                anyString(), anyString(), anyString(), anyList(), isNull(),
                anyList(), any(McpToolContext.class), any(ContextCompactionObserver.class)
        )).thenReturn(Flux.just("实体选择结果"));

        List<String> response = fixture.service.chat(
                chatDto(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION, false),
                null
        ).collectList().block();

        assertThat(calls).hasValue(1);
        assertThat(response)
                .anySatisfy(chunk -> assertThat(chunk)
                        .contains("MCP调用成功", "retrieval_list_display_entity"));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.visualizationAgent).chat(
                eq("chat-1"),
                eq("model-1"),
                promptCaptor.capture(),
                eq(List.of()),
                isNull(),
                eq(List.of("data-visualization-agent")),
                any(McpToolContext.class),
                any(ContextCompactionObserver.class)
        );
        assertThat(promptCaptor.getValue())
                .contains("平台已执行实体 Meta MCP",
                        "retrieval_list_display_entity",
                        "probe_agent_message",
                        "探针消息");
    }

    @Test
    void dynamicSkillUsesInheritedAgentAndOnlySelectedSkill() {
        Fixture fixture = fixture();
        String chatType = "skill:jmr-analysis";
        when(fixture.skillService.requireEnabledChatEntry(chatType))
                .thenReturn(new SkillChatEntryVo(
                        "jmr-analysis",
                        chatType,
                        BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                        "僵木蠕研判",
                        "说明",
                        "data-analysis",
                        60
                ));
        when(fixture.agentMcpToolService.resolve(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                List.of("jmr-analysis")
        ))
                .thenReturn(McpToolContext.empty());
        when(fixture.visualizationAgent.chat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyList(),
                any(McpToolContext.class), any(ContextCompactionObserver.class)
        )).thenReturn(Flux.just("专项研判回答"));

        List<String> response = fixture.service.chat(chatDto(chatType, true), null).collectList().block();

        assertThat(response).containsExactly("专项研判回答");
        verify(fixture.agentMcpToolService).resolve(
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION,
                List.of("jmr-analysis")
        );
        verify(fixture.visualizationAgent).chat(
                eq("chat-1"),
                eq("model-1"),
                eq("问题"),
                eq(List.of()),
                isNull(),
                eq(List.of("jmr-analysis")),
                any(McpToolContext.class),
                any(ContextCompactionObserver.class)
        );
        verify(fixture.skillService, never()).isBuiltinAgentEnabled(anyString());
        assertThat(capturedSessionDefaults(fixture).getType()).isEqualTo(chatType);
        assertThat(capturedSessionDefaults(fixture).getDeepThink()).isFalse();
    }

    @Test
    void genericDynamicSkillWithoutToolRuntimeUsesNoMcpButStillPassesScopedRag() {
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
        when(fixture.skillService.resolveKnowledgeBaseIds(List.of("generic")))
                .thenReturn(List.of(21));
        when(fixture.chatService.agentChat(
                anyString(), anyString(), anyString(), anyString(), anyList(), isNull(), anyList(),
                any(McpToolContext.class), any(ContextCompactionObserver.class)
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
                eq(List.of(21)),
                any(McpToolContext.class),
                any(ContextCompactionObserver.class)
        );
        verify(fixture.chatService, never()).qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), anyBoolean(),
                any(McpToolContext.class)
        );
    }

    @Test
    void genericDynamicSkillUsesOnlyItsExplicitRuntimeTools() {
        Fixture fixture = fixture();
        String chatType = "skill:jmr-analysis";
        when(fixture.skillService.requireEnabledChatEntry(chatType))
                .thenReturn(new SkillChatEntryVo(
                        "jmr-analysis",
                        chatType,
                        SkillService.GENERIC_SKILL_AGENT_TYPE,
                        "僵木蠕研判",
                        null,
                        "data-analysis",
                        60
                ));
        SkillRuntimeConfigVo runtime = new SkillRuntimeConfigVo(
                "skill_only",
                new SkillRuntimeToolsVo(
                        Map.of("retrieval", List.of("retrieval_search")), Map.of()),
                null
        );
        when(fixture.skillService.resolveRuntimeConfig(List.of("jmr-analysis"))).thenReturn(runtime);
        when(fixture.skillService.buildAgentSkillPrompt(
                SkillService.GENERIC_SKILL_AGENT_TYPE,
                List.of("jmr-analysis")
        )).thenReturn("JMR 专项提示词");
        when(fixture.skillService.resolveKnowledgeBaseIds(List.of("jmr-analysis")))
                .thenReturn(List.of(22));
        McpToolContext toolContext = new McpToolContext(
                mock(ToolCallbackProvider.class),
                "仅允许 retrieval_search",
                runtime
        );
        when(fixture.agentMcpToolService.resolve(
                SkillService.GENERIC_SKILL_AGENT_TYPE,
                List.of("jmr-analysis")
        )).thenReturn(toolContext);
        when(fixture.chatService.agentChat(
                anyString(), anyString(), anyString(), anyString(), anyList(), isNull(), anyList(),
                any(McpToolContext.class), any(ContextCompactionObserver.class)
        )).thenReturn(Flux.just("专项回答"));

        List<String> response = fixture.service.chat(chatDto(chatType, false), null).collectList().block();

        assertThat(response).containsExactly("专项回答");
        verify(fixture.agentMcpToolService).resolve(
                SkillService.GENERIC_SKILL_AGENT_TYPE,
                List.of("jmr-analysis")
        );
        verify(fixture.chatService).agentChat(
                eq("chat-1"),
                eq("model-1"),
                org.mockito.ArgumentMatchers.argThat(prompt ->
                        prompt.contains("JMR 专项提示词")
                                && prompt.contains("仅允许 retrieval_search")),
                eq("问题"),
                eq(List.of()),
                isNull(),
                eq(List.of(22)),
                any(McpToolContext.class),
                any(ContextCompactionObserver.class)
        );
    }

    @Test
    void legacyMcpAgentBecomesAskWithoutTools() {
        Fixture fixture = fixture();
        when(fixture.chatService.qaChat(
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false),
                any(McpToolContext.class)
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
                anyString(), anyString(), anyString(), anyList(), isNull(), eq(false),
                any(McpToolContext.class)
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
                BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION
        )).thenReturn(false);

        List<String> response = fixture.service.chat(
                chatDto(BuiltinAgentSkillRegistry.AGENT_DATA_VISUALIZATION, false),
                null
        ).collectList().block();

        assertThat(response).containsExactly(
                "智能体能力不可用：以下 Skill 不存在或未启用: data-visualization-agent"
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
        AttachmentToolProviderFactory attachmentToolProviderFactory =
                mock(AttachmentToolProviderFactory.class);
        ChatTitleService titleService = mock(ChatTitleService.class);
        AgentMcpToolService agentMcpToolService = mock(AgentMcpToolService.class);
        SkillService skillService = mock(SkillService.class);
        DataVisualizationDemoResponseService visualizationDemoResponseService =
                mock(DataVisualizationDemoResponseService.class);
        DataVisualizationAgent visualizationAgent = mock(DataVisualizationAgent.class);
        ChatSession chatSession = mock(ChatSession.class);

        when(baseService.isModelSupported("model-1")).thenReturn(true);
        when(baseService.resolveChatModel(eq("model-1"), anyBoolean(), eq(false)))
                .thenReturn("model-1");
        when(attachmentToolProviderFactory.create(
                any(), anyString(), any(), anyList()))
                .thenReturn(AttachmentToolProviderFactory.AttachmentToolBundle.empty());
        when(attachmentService.hasImageAttachment(anyList())).thenReturn(false);
        when(attachmentService.appendAttachmentContext(
                eq("问题"), anyList(), isNull(), eq("chat-1")))
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
                visualizationDemoResponseService,
                null,
                null,
                null,
                visualizationAgent,
                new ChatMessagePartParser(),
                attachmentService,
                titleService,
                agentMcpToolService,
                skillService,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "attachmentToolProviderFactory",
                attachmentToolProviderFactory);
        return new Fixture(
                service,
                chatService,
                baseService,
                chatSessionService,
                agentMcpToolService,
                skillService,
                visualizationAgent,
                attachmentToolProviderFactory,
                attachmentService
        );
    }

    private record Fixture(
            DihChatApplicationService service,
            AIChatService chatService,
            AIBaseService baseService,
            ChatSessionService chatSessionService,
            AgentMcpToolService agentMcpToolService,
            SkillService skillService,
            DataVisualizationAgent visualizationAgent,
            AttachmentToolProviderFactory attachmentToolProviderFactory,
            ChatAttachmentService attachmentService
    ) {
    }
}
