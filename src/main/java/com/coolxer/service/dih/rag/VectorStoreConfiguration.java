package com.coolxer.service.dih.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfiguration {

    /**
     * 提供基于内存的向量存储（SimpleVectorStore）
     * <p>
     * 依赖 EmbeddingModel（由 Spring AI 自动注入）
     *
     * @param embeddingModel
     * @return
     */
    @Bean
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public VectorStoreDelegate vectorStoreDelegate(
            @Qualifier("redisVectorStoreCustom") RedisVectorStore redisVectorStore,
            @Qualifier("simpleVectorStore") VectorStore simpleVectorStore
    ) {

        return new VectorStoreDelegate(redisVectorStore, simpleVectorStore);
    }

}
