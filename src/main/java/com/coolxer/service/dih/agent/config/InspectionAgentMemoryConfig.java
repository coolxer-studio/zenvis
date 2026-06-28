package com.coolxer.service.dih.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InspectionAgent 对话记忆配置
 * 复用项目已有的 MySQL 持久化方案，与 AIChatService 共享 Spring AI JDBC memory 表
 */
@Configuration
public class InspectionAgentMemoryConfig {

	@Bean("inspectionAgentChatMemory")
	public ChatMemory inspectionAgentChatMemory(
			@Qualifier("springAiChatMemoryRepository") ChatMemoryRepository chatMemoryRepository) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(chatMemoryRepository)
				.maxMessages(10)
				.build();
	}

}
