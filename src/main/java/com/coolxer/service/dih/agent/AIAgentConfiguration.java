package com.coolxer.service.dih.agent;

import com.coolxer.service.dih.AgentLlmService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AIAgentConfiguration {
    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public AgentLlmService agentLlmService(ChatModel chatModel) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        return new AgentLlmService(chatClient);
    }
}
