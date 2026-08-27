package com.coolxer.service.dih;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.service.dih.rag.RagContextService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIChatServiceExecutionBoundaryTest {

    @Test
    void normalAndDeepQaRetrieveRagBeforeCreatingModelStream() {
        RagContextService ragContextService = mock(RagContextService.class);
        when(ragContextService.retrieve(any(), any())).thenReturn(new RagContextService.RagContext(
                "RAG资料",
                true,
                true,
                1,
                2,
                "ok"
        ));
        AIChatService service = service(ragContextService);

        service.qaChat("chat-1", "model-1", "普通问题", List.of(), null, false);
        service.qaChat("chat-2", "qwen3", "深度问题", List.of(), null, true);

        verify(ragContextService).retrieve("普通问题", "ask");
        verify(ragContextService).retrieve("深度问题", "deep_think");
    }

    @Test
    void agentChatNeverRetrievesRag() {
        RagContextService ragContextService = mock(RagContextService.class);
        AIChatService service = service(ragContextService);

        service.agentChat(
                "chat-1",
                "model-1",
                "Agent系统提示",
                "执行任务",
                List.of(),
                null,
                McpToolContext.empty()
        );

        verify(ragContextService, never()).retrieve(any(), any());
    }

    @Test
    void toolCallingUsesLowTemperatureAndDisablesParallelCalls() throws Exception {
        AIChatService service = service(mock(RagContextService.class));
        Method method = AIChatService.class.getDeclaredMethod(
                "buildRuntimeOptions", String.class, boolean.class);
        method.setAccessible(true);

        OpenAiChatOptions toolOptions =
                (OpenAiChatOptions) method.invoke(service, "model-1", true);
        OpenAiChatOptions qaOptions =
                (OpenAiChatOptions) method.invoke(service, "model-1", false);

        assertThat(toolOptions.getTemperature()).isEqualTo(0.1);
        assertThat(toolOptions.getParallelToolCalls()).isFalse();
        assertThat(toolOptions.getMaxTokens()).isEqualTo(4096);
        assertThat(qaOptions.getTemperature()).isEqualTo(0.8);
        assertThat(qaOptions.getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void preflightCompactsInputBeforeOutputReserveWouldExceedContextWindow() throws Exception {
        AIChatService service = service(mock(RagContextService.class));
        Method method = AIChatService.class.getDeclaredMethod(
                "prepareChatInput",
                String.class,
                String.class,
                String.class,
                ToolCallbackProvider.class,
                com.coolxer.service.dih.mcp.ToolRuntimeContext.class
        );
        method.setAccessible(true);
        String oversizedPrompt = "数".repeat(98_305);

        Object prepared = method.invoke(
                service, "chat-large", "系统提示", oversizedPrompt, null, null);
        Method promptAccessor = prepared.getClass().getDeclaredMethod("prompt");
        promptAccessor.setAccessible(true);
        String boundedPrompt = (String) promptAccessor.invoke(prepared);

        assertThat(boundedPrompt)
                .contains("内容已按模型上下文预算截断");
        assertThat(boundedPrompt.length()).isLessThan(oversizedPrompt.length());
        assertThat(new DihTokenEstimator().estimate(boundedPrompt))
                .isLessThanOrEqualTo(94_100);
    }

    @Test
    void preflightUsesDedicatedToolResultTokenBudgetInsteadOfCharacterBudget() throws Exception {
        AIChatService service = service(mock(RagContextService.class));
        Method method = prepareChatInputMethod();
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(64, 2, 24_000, 192_000, 48_000));

        Object prepared = method.invoke(
                service, "chat-tool-budget", "系统提示", "开始执行", null, runtimeContext);
        Method promptAccessor = prepared.getClass().getDeclaredMethod("prompt");
        promptAccessor.setAccessible(true);

        assertThat(promptAccessor.invoke(prepared)).isEqualTo("开始执行");
        assertThat(runtimeContext.maxAccumulatedToolResultChars()).isEqualTo(192_000);
        assertThat(runtimeContext.maxAccumulatedToolResultTokens()).isEqualTo(48_000);
    }

    @Test
    void preflightReportsTokenBudgetBreakdownWhenFixedContextCannotFit() throws Exception {
        AIChatService service = service(mock(RagContextService.class));
        Method method = prepareChatInputMethod();
        ToolRuntimeContext runtimeContext = new ToolRuntimeContext(
                new SkillRuntimeLimitsVo(64, 2, 24_000, 64_000, 94_000));

        assertThatThrownBy(() -> method.invoke(
                service, "chat-over-budget", "系统提示", "开始执行", null, runtimeContext))
                .hasRootCauseInstanceOf(AgentCapabilityUnavailableException.class)
                .hasRootCauseMessage(
                        "智能体固定上下文预算不足：最大输入 94208 Token，固定占用 94132 Token"
                                + "（系统提示词 4、工具定义 0、工具结果预留 94000）。"
                                + "请降低 Skill 的 maxAccumulatedToolResultTokens，"
                                + "或精简 Skill/工具定义；不要通过增大字符预算替代 Token 预算。");
    }

    @Test
    void completedChatReplacesExpandedAttachmentBodyWithReferenceInModelMemory() {
        InMemoryChatMemoryRepository memoryRepository = new InMemoryChatMemoryRepository();
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("分析完成"))))
        ));
        ChatAttachmentService attachmentService =
                new ChatAttachmentService(new CustomWebConfig());
        AIChatService service = new AIChatService(
                memoryRepository,
                chatModel,
                new PromptTemplate("普通问答系统提示"),
                new PromptTemplate("深度思考系统提示"),
                mock(RagContextService.class),
                attachmentService,
                "",
                "",
                "model-1"
        );
        ChatAttachment attachment = ChatAttachment.builder()
                .fileId("e59f426f-ed24-4bf7-8930-643eb3ed38c7")
                .fileName("evidence.log")
                .fileSize(1024L)
                .build();
        String expandedPrompt =
                "分析附件\n\n---\n以下是用户本轮消息上传的附件内容，请结合这些附件回答。"
                        + "\nRAW_ATTACHMENT_BODY";

        service.agentChat(
                "chat-attachment",
                "model-1",
                "Agent系统提示",
                expandedPrompt,
                List.of(attachment),
                null,
                McpToolContext.empty()
        ).collectList().block();

        assertThat(memoryRepository.findByConversationId("chat-attachment"))
                .extracting(Message::getText)
                .anyMatch(text -> text.contains("evidence.log"))
                .noneMatch(text -> text.contains("RAW_ATTACHMENT_BODY"));
    }

    private AIChatService service(RagContextService ragContextService) {
        ChatMemoryRepository memoryRepository = mock(ChatMemoryRepository.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>empty());
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        when(attachmentService.hasImageAttachment(any())).thenReturn(false);
        return new AIChatService(
                memoryRepository,
                chatModel,
                new PromptTemplate("普通问答系统提示"),
                new PromptTemplate("深度思考系统提示"),
                ragContextService,
                attachmentService,
                "",
                "",
                "model-1"
        );
    }

    private Method prepareChatInputMethod() throws NoSuchMethodException {
        Method method = AIChatService.class.getDeclaredMethod(
                "prepareChatInput",
                String.class,
                String.class,
                String.class,
                ToolCallbackProvider.class,
                ToolRuntimeContext.class
        );
        method.setAccessible(true);
        return method;
    }
}
