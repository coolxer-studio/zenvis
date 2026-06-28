package com.coolxer.service.dih.agent;

import com.coolxer.service.dih.agent.nl2sql.connector.accessor.ClickHouseAccessor;
import com.coolxer.service.dih.agent.nl2sql.service.LlmService;
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

    // TODO heyjd 这里使用不太好
    //required a single bean, but 2 were found:
//    @Bean
//    public DbConfig dbConfig() {
//        DbConfig dbConfig = new DbConfig();
//        dbConfig.setDialectType("clickhouse");
//        dbConfig.setSchema("zenvis");
//        return dbConfig;
//    }


    @Bean
    public LlmService llmService(ChatModel chatModel) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        return new LlmService(chatClient);
    }

    @Bean
    public ClickHouseAccessor clickHouseAccessor() {
        return new ClickHouseAccessor();
    }


}
