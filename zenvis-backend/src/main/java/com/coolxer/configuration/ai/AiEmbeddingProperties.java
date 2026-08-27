package com.coolxer.configuration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.embedding")
public class AiEmbeddingProperties {

    /**
     * Embedding/RAG is opt-in because local chat-only model services may not expose embeddings.
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
