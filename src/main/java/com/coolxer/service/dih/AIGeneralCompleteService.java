package com.coolxer.service.dih;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * AI自动补全提示服务
 */

@Service
public class AIGeneralCompleteService {

    private final ChatClient chatClient;

    public AIGeneralCompleteService(
            ChatModel chatModel,
            @Qualifier("completeSystemPromptTemplate") PromptTemplate systemPromptTemplate
    ) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(
                        systemPromptTemplate.getTemplate()
                ).defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    public String complete(String prompt) {
        var runtimeOptions = OpenAiChatOptions.builder()
                .temperature(0.8)
                .build();

        return chatClient.prompt()
                .options(runtimeOptions)
                .user(prompt)
                .call()
                .content();
    }
}
