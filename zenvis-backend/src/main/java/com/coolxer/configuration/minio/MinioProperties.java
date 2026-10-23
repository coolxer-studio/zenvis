package com.coolxer.configuration.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    public static final String DEFAULT_BUCKET = "zenvis-attachments";
    public static final String DEFAULT_RAG_BUCKET = "zenvis-rag";
    public static final String DEFAULT_CHAT_BUCKET = "zenvis-chat";
    public static final String DEFAULT_PLUGINS_BUCKET = "zenvis-plugins";
    public static final String DEFAULT_REPORTS_BUCKET = "zenvis-reports";

    /**
     * MinIO 服务地址，如 http://localhost:9000
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 秘密密钥
     */
    private String secretKey;

    /**
     * 默认存储桶名称
     */
    private String bucketName = DEFAULT_BUCKET;

    /**
     * 业务域独立存储桶。
     */
    private Buckets buckets = new Buckets();

    /**
     * 返回业务域 Bucket，调用方不得再以空值回退到默认 Bucket。
     */
    public String bucket(MinioBucketDomain domain) {
        Buckets configured = buckets == null ? new Buckets() : buckets;
        return switch (domain) {
            case RAG -> configured.getRag();
            case CHAT -> configured.getChat();
            case PLUGINS -> configured.getPlugins();
            case REPORTS -> configured.getReports();
        };
    }

    /**
     * 所有必须存在的业务桶。
     */
    public Set<String> requiredBusinessBuckets() {
        Set<String> names = new LinkedHashSet<>();
        for (MinioBucketDomain domain : MinioBucketDomain.values()) {
            names.add(bucket(domain));
        }
        return names;
    }

    @Data
    public static class Buckets {
        private String rag = DEFAULT_RAG_BUCKET;
        private String chat = DEFAULT_CHAT_BUCKET;
        private String plugins = DEFAULT_PLUGINS_BUCKET;
        private String reports = DEFAULT_REPORTS_BUCKET;
    }
}
