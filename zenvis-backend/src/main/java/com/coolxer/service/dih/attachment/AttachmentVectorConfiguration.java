package com.coolxer.service.dih.attachment;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * 附件片段使用独立 Redis 索引，避免与知识库向量混用。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.embedding", name = "enabled", havingValue = "true")
public class AttachmentVectorConfiguration {

    @Bean
    @Qualifier("attachmentVectorStore")
    public RedisVectorStore attachmentVectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel,
            @Value("${app.ai.dih.attachment.vector.index:zenvis_attachment_vector}")
            String indexName,
            @Value("${app.ai.dih.attachment.vector.prefix:zenvis_attachment_vector:}")
            String prefix) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("file_id"),
                        RedisVectorStore.MetadataField.tag("file_name"),
                        RedisVectorStore.MetadataField.tag("user_id"),
                        RedisVectorStore.MetadataField.tag("chat_id"),
                        RedisVectorStore.MetadataField.tag("kind"),
                        RedisVectorStore.MetadataField.tag("locator"))
                .initializeSchema(true)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }
}
