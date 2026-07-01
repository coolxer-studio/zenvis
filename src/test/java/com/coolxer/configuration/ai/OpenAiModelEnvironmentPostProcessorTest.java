package com.coolxer.configuration.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiModelEnvironmentPostProcessorTest {

    private final OpenAiModelEnvironmentPostProcessor processor = new OpenAiModelEnvironmentPostProcessor();

    @Test
    void disablesOpenAiModelsWhenBaseUrlIsMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "sk-test");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    }

    @Test
    void keepsChatModelEnabledWhenBaseUrlAndApiKeyAreConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.openai.base-url", "http://model-service:1234")
                .withProperty("spring.ai.openai.api-key", "sk-test");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isNull();
    }
}
