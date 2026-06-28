package com.coolxer.service.dih.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisVectorConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RedisVectorConfiguration.class);

    @Value("${spring.ai.vectorstore.redis.host}")
    private String host;
    @Value("${spring.ai.vectorstore.redis.port}")
    private int port;
    @Value("${spring.ai.vectorstore.redis.password}")
    private String password;
    @Value("${spring.ai.vectorstore.redis.prefix}")
    private String prefix;
    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Bean
    public JedisPooled jedisPooled() {
        logger.info("Redis host: {}, port: {}", host, port);
        return new JedisPooled(host, port, null, password);
    }

    @Bean
    @Qualifier("redisVectorStoreCustom")
    public RedisVectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        logger.info("create redis vector store");
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)                // Optional: defaults to "spring-ai-index"
                .prefix(prefix)                  // Optional: defaults to "embedding:"
                .metadataFields(                         // Optional: define metadata fields for filtering
                        RedisVectorStore.MetadataField.tag("source"))
                .initializeSchema(true)                   // Optional: defaults to false
                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
                .build();
    }


    /**
     * 为Agent单独创建一个向量数据库，索引单独拆开
     *
     * @param jedisPooled    与 #vectorStore 共用一个jedis 链接
     * @param embeddingModel embedding model
     * @return
     */
    @Bean
    @Qualifier("redisVectorStoreForAgent")
    public RedisVectorStore vectorStoreForAgent(JedisPooled jedisPooled,
                                                EmbeddingModel embeddingModel) {

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("agent_vector_index")
                .prefix(prefix)
                .metadataFields(
                        // 后续可以使用source 区分不同的数据来源
                        RedisVectorStore.MetadataField.tag("source"),

                        RedisVectorStore.MetadataField.tag("schema"),
                        RedisVectorStore.MetadataField.tag("tableName"),
                        RedisVectorStore.MetadataField.tag("vectorType"),

                        RedisVectorStore.MetadataField.text("name"),
                        RedisVectorStore.MetadataField.text("description"),
                        RedisVectorStore.MetadataField.text("type"),
                        RedisVectorStore.MetadataField.text("foreignKey"),
                        RedisVectorStore.MetadataField.text("primaryKey"),

                        RedisVectorStore.MetadataField.tag("primary"),
                        RedisVectorStore.MetadataField.tag("notnull"))
                .initializeSchema(true)                   // Optional: defaults to false
                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
                .build();
    }

}
