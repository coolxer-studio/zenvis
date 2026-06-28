package com.coolxer.service.dih.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * InspectionAgent 对话记忆配置
 * 复用项目已有的 MySQL 持久化方案，与 AIChatService 共享 Spring AI JDBC memory 表
 */
@Configuration
public class InspectionAgentMemoryConfig {

	@Bean("inspectionAgentChatMemory")
	public ChatMemory inspectionAgentChatMemory(
			@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(JdbcChatMemoryRepository.builder()
						.jdbcTemplate(new JdbcTemplate(mysqlDataSource))
						.dialect(new MysqlChatMemoryRepositoryDialect())
						.build())
				.maxMessages(10)
				.build();
	}

}
