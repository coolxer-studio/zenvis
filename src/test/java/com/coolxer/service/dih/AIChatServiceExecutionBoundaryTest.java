package com.coolxer.service.dih;

import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.rag.RagContextService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import reactor.core.publisher.Flux;

import java.util.List;

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
}
