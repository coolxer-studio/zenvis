package com.coolxer.service.dih.rag;

import com.coolxer.configuration.ai.AiEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreInitializerServiceTest {

    @Test
    void unavailableCooldownSkipsEmbeddingProbe() {
        VectorStoreInitializerService service = new VectorStoreInitializerService();
        AiEmbeddingProperties embeddingProperties = new AiEmbeddingProperties();
        embeddingProperties.setEnabled(true);
        ReflectionTestUtils.setField(service, "embeddingProperties", embeddingProperties);
        ReflectionTestUtils.setField(
                service, "embeddingUnavailableUntil", System.currentTimeMillis() + 60_000L);

        assertThat(service.isRagAvailable()).isFalse();
    }
}
