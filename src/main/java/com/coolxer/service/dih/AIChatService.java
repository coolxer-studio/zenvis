package com.coolxer.service.dih;

import com.coolxer.configuration.ai.AiEmbeddingProperties;
import com.coolxer.service.dih.advisor.ReasoningContentAdvisor;
import com.coolxer.service.dih.rag.VectorStoreDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 聊天服务
 */

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    private final ChatClient chatClient;

    private final PromptTemplate deepThinkPromptTemplate;

    private final ReasoningContentAdvisor reasoningContentAdvisor;

    private final VectorStoreDelegate vectorStoreDelegate;

    private final AiEmbeddingProperties embeddingProperties;

    public AIChatService(
            @Qualifier("springAiChatMemoryRepository") ChatMemoryRepository chatMemoryRepository,
            ChatModel chatModel,
            @Qualifier("askSystemPromptTemplate") PromptTemplate systemPromptTemplate,
            @Qualifier("deepThinkPromptTemplate") PromptTemplate deepThinkPromptTemplate,
            VectorStoreDelegate vectorStoreDelegate,
            AiEmbeddingProperties embeddingProperties
    ) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(
                        systemPromptTemplate.getTemplate()
                ).defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        this.deepThinkPromptTemplate = deepThinkPromptTemplate;
        this.reasoningContentAdvisor = new ReasoningContentAdvisor(1);
        this.vectorStoreDelegate = vectorStoreDelegate;
        this.embeddingProperties = embeddingProperties;
    }

    public Flux<String> chat(String chatId, String model, String prompt) {

        log.debug("chat model is: {}", model);

        // check if model == "deepseek-r1", output reasoning content.
        if (Objects.equals("deepseek-r1", model)) {
            // add reasoning content advisor.
            chatClient.prompt().advisors(reasoningContentAdvisor);
        }
        var runtimeOptions = buildRuntimeOptions(model);

        var promptSpec = chatClient.prompt()
                .options(runtimeOptions)
                .user(prompt)
                .advisors(memoryAdvisor -> memoryAdvisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                );

        if (!embeddingProperties.isEnabled()) {
            log.debug("Skip chat RAG advisor because app.ai.embedding.enabled=false.");
            return promptSpec.stream().content();
        }

        return promptSpec.advisors(
                        QuestionAnswerAdvisor
                                .builder(vectorStoreDelegate.getVectorStore("redis"))
                                .searchRequest(
                                        SearchRequest.builder()
                                                // TODO all documents retrieved from ADB are under 0.1
//												.similarityThreshold(0.6d)
                                                .topK(6)
                                                .build()
                                )
                                .build()
                )
                .stream()
                .content();
    }

    public Flux<String> deepThinkingChat(String chatId, String model, String prompt) {

        return chatClient.prompt()
                .options(buildRuntimeOptions(model))
                .system(deepThinkPromptTemplate.getTemplate())
                .user(prompt)
                .advisors(memoryAdvisor -> memoryAdvisor
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                ).stream()
                .content();
    }

    private OpenAiChatOptions buildRuntimeOptions(String model) {
        var builder = OpenAiChatOptions.builder()
                .temperature(0.8);
        if (StringUtils.hasText(model)) {
            builder.model(model);
        }
        return builder.build();
    }
}
