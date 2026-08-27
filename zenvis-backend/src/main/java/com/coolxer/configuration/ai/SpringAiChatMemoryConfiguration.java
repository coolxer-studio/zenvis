package com.coolxer.configuration.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Spring AI JDBC chat memory uses the MySQL data source, not the primary ClickHouse data source.
 */
@Configuration
public class SpringAiChatMemoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatMemoryConfiguration.class);

    private static final String SCHEMA_LOCATION =
            "org/springframework/ai/chat/memory/repository/jdbc/schema-mysql.sql";

    @Bean
    public InitializingBean springAiChatMemorySchemaInitializer(
            @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
            @Value("${spring.ai.chat.memory.repository.jdbc.initialize-schema:always}") String initializeSchema) {
        return () -> {
            if ("never".equalsIgnoreCase(initializeSchema)) {
                log.info("Skip Spring AI chat memory schema initialization because initialize-schema=never.");
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(SCHEMA_LOCATION));
            populator.execute(mysqlDataSource);
            log.info("Initialized Spring AI chat memory schema from {}.", SCHEMA_LOCATION);
        };
    }

    @Bean("springAiChatMemoryRepository")
    @DependsOn("springAiChatMemorySchemaInitializer")
    public ChatMemoryRepository springAiChatMemoryRepository(
            @Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(new JdbcTemplate(mysqlDataSource))
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
    }
}
