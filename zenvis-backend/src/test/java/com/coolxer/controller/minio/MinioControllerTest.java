package com.coolxer.controller.minio;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MinioControllerTest {

    @Test
    void genericEndpointRejectsBusinessBuckets() {
        MinioService minioService = mock(MinioService.class);
        MinioController controller = new MinioController(minioService, new MinioProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "content".getBytes());

        for (String bucket : new String[]{"zenvis-rag", "zenvis-chat", "zenvis-plugins", "zenvis-reports"}) {
            assertThatThrownBy(() -> controller.upload(file, bucket, "prefix"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("不允许访问业务存储桶");
        }
        verify(minioService, never()).uploadFile(any(), anyString(), anyString());
    }
}
