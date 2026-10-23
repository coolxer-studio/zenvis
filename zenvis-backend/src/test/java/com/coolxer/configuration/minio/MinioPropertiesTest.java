package com.coolxer.configuration.minio;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MinioPropertiesTest {

    @Test
    void selectsConfiguredBucketForEachBusinessDomain() {
        MinioProperties properties = new MinioProperties();
        assertThat(properties.bucket(MinioBucketDomain.RAG)).isEqualTo("zenvis-rag");
        assertThat(properties.bucket(MinioBucketDomain.CHAT)).isEqualTo("zenvis-chat");
        assertThat(properties.bucket(MinioBucketDomain.PLUGINS)).isEqualTo("zenvis-plugins");
        assertThat(properties.bucket(MinioBucketDomain.REPORTS)).isEqualTo("zenvis-reports");

        properties.getBuckets().setRag("custom-rag");
        properties.getBuckets().setChat("custom-chat");
        properties.getBuckets().setPlugins("custom-plugins");
        properties.getBuckets().setReports("custom-reports");

        assertThat(properties.bucket(MinioBucketDomain.RAG)).isEqualTo("custom-rag");
        assertThat(properties.bucket(MinioBucketDomain.CHAT)).isEqualTo("custom-chat");
        assertThat(properties.bucket(MinioBucketDomain.PLUGINS)).isEqualTo("custom-plugins");
        assertThat(properties.bucket(MinioBucketDomain.REPORTS)).isEqualTo("custom-reports");
        assertThat(properties.requiredBusinessBuckets())
                .isEqualTo(Set.of("custom-rag", "custom-chat", "custom-plugins", "custom-reports"));
        assertThat(properties.getBucketName()).isEqualTo(MinioProperties.DEFAULT_BUCKET);
    }
}
