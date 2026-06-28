package com.coolxer.service.dih;

import com.coolxer.service.dih.logging.LlmLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AIGeneralCompleteService.class);

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
        String scene = "AIGeneralCompleteService.complete";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        var runtimeOptions = OpenAiChatOptions.builder()
                .temperature(0.8)
                .build();

        LlmLogHelper.logRequest(log, requestId, scene, new GeneralCompleteLogRequest(prompt, 0.8));
        try {
            String response = chatClient.prompt()
                    .options(runtimeOptions)
                    .user(prompt)
                    .call()
                    .content();
            LlmLogHelper.logResponse(log, requestId, scene, response, startedAtNanos);
            return response;
        } catch (RuntimeException e) {
            LlmLogHelper.logError(log, requestId, scene, null, startedAtNanos, e);
            throw e;
        }
    }

    private record GeneralCompleteLogRequest(String prompt, double temperature) {
    }
}
