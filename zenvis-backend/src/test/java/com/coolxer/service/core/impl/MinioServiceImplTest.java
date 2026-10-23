package com.coolxer.service.core.impl;

import com.coolxer.configuration.minio.MinioProperties;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MinioServiceImplTest {

    @Test
    void startupCreatesDefaultAndFourBusinessBuckets() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(false);
        MinioServiceImpl service = new MinioServiceImpl(client, new MinioProperties());

        service.init();

        ArgumentCaptor<MakeBucketArgs> buckets = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(client, times(5)).makeBucket(buckets.capture());
        assertThat(buckets.getAllValues())
                .extracting(MakeBucketArgs::bucket)
                .containsExactlyInAnyOrder(
                        "zenvis-attachments", "zenvis-rag", "zenvis-chat",
                        "zenvis-plugins", "zenvis-reports");
    }

    @Test
    void startupRejectsDuplicateBusinessBucketsBeforeTouchingMinio() {
        MinioProperties properties = new MinioProperties();
        properties.getBuckets().setChat("zenvis-rag");
        MinioClient client = mock(MinioClient.class);
        MinioServiceImpl service = new MinioServiceImpl(client, properties);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为空且必须互不相同");
        verifyNoInteractions(client);
    }
}
