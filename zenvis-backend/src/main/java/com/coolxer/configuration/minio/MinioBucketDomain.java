package com.coolxer.configuration.minio;

/**
 * 业务存储域。每个域固定使用独立 Bucket，避免通用附件接口访问业务对象。
 */
public enum MinioBucketDomain {
    RAG,
    CHAT,
    PLUGINS,
    REPORTS
}
