package com.coolxer.service.dih;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

class AIBaseServiceTest {

    @Test
    void returnsAutoAndDefaultModelWhenOpenAiConfigIsMissing() {
        AIBaseService service = new AIBaseService("", "", "qwen/qwen3.6-35b-a3b");

        assertThat(service.getModels())
                .extracting(model -> model.get("model"))
                .containsExactly("auto", "qwen/qwen3.6-35b-a3b");
        assertThat(service.isModelSupported("custom-model")).isTrue();
    }

    @Test
    void parsesChatModelIdsFromOpenAiModelsResponse() throws Exception {
        AIBaseService service = new AIBaseService("", "", "");

        assertThat(service.parseModelIds("""
                {
                  "object": "list",
                  "data": [
                    {"id": "qwen/qwen3.6-35b-a3b", "object": "model"},
                    {"id": "text-embedding-v4", "object": "model"},
                    {"id": "gpt-4o", "object": "model"}
                  ]
                }
                """))
                .containsExactly("qwen/qwen3.6-35b-a3b", "gpt-4o");
    }

    @Test
    void autoModelPrefersVisionOrReasoningModelByRequestShape() {
        AIBaseService service = new AIBaseService("", "", "plain-chat-model");
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        modelIds.add("plain-chat-model");
        modelIds.add("qwen/qwen2.5-vl-32b");
        modelIds.add("qwen/qwen3.6-35b-a3b");

        assertThat(service.selectAutoModel(modelIds, false, true))
                .isEqualTo("qwen/qwen2.5-vl-32b");
        assertThat(service.selectAutoModel(modelIds, true, false))
                .isEqualTo("qwen/qwen3.6-35b-a3b");
        assertThat(service.selectAutoModel(modelIds, false, false))
                .isEqualTo("plain-chat-model");
    }
}
