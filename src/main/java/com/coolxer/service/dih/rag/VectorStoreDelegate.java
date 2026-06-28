package com.coolxer.service.dih.rag;

import org.springframework.ai.vectorstore.VectorStore;

/**
 * 矢量库代理
 */
public class VectorStoreDelegate {

    /**
     * 内存矢量库
     */
    private VectorStore simpleVectorStore;

    /**
     * redis矢量库
     */
    private VectorStore redisVectorStore;


    public VectorStoreDelegate(VectorStore redisVectorStore, VectorStore simpleVectorStore) {
        this.simpleVectorStore = simpleVectorStore;
        this.redisVectorStore = redisVectorStore;
    }

    public VectorStore getVectorStore(String vectorStoreType) {
        if ("redis".equals(vectorStoreType) && redisVectorStore != null) {
            return redisVectorStore;
        }
        return simpleVectorStore;
    }
}
