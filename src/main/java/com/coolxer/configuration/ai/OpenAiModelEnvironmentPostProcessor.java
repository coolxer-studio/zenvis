package com.coolxer.configuration.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAiModelEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "openAiModelFallbackDefaults";

    private static final String PLACEHOLDER_API_KEY = "sk-local-placeholder";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        boolean embeddingEnabled = Boolean.parseBoolean(environment.getProperty("app.ai.embedding.enabled", "false"));

        if (isMissingOpenAiKey(environment)) {
            disableModelIfUnset(environment, defaults, "spring.ai.model.chat");
            disableModelIfUnset(environment, defaults, "spring.ai.model.embedding");
            disableModelIfUnset(environment, defaults, "spring.ai.model.image");
            disableModelIfUnset(environment, defaults, "spring.ai.model.audio.speech");
            disableModelIfUnset(environment, defaults, "spring.ai.model.audio.transcription");
            disableModelIfUnset(environment, defaults, "spring.ai.model.moderation");
        }

        if (!embeddingEnabled) {
            disableModelIfUnset(environment, defaults, "spring.ai.model.embedding");
        }

        if (!embeddingEnabled && !StringUtils.hasText(environment.getProperty("spring.ai.vectorstore.type"))) {
            defaults.put("spring.ai.vectorstore.type", "none");
        }

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static void disableModelIfUnset(ConfigurableEnvironment environment, Map<String, Object> defaults,
                                            String propertyName) {
        if (!StringUtils.hasText(environment.getProperty(propertyName))) {
            defaults.put(propertyName, "none");
        }
    }

    private static boolean isMissingOpenAiKey(ConfigurableEnvironment environment) {
        String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
        return !StringUtils.hasText(apiKey) || PLACEHOLDER_API_KEY.equals(apiKey);
    }
}
