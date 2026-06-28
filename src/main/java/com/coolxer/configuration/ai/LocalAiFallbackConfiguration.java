package com.coolxer.configuration.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.IntStream;

@Configuration
public class LocalAiFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalAiFallbackConfiguration.class);

    private static final String PLACEHOLDER_API_KEY = "sk-local-placeholder";

    private static final String LOCAL_AI_DISABLED_MESSAGE =
            "本地未配置有效的 OPENAI_API_KEY，AI 对话暂不可用。请配置真实 OpenAI API Key 后重启服务。";

    @Bean
    @Primary
    @Conditional(LocalAiFallbackConfiguration.MissingOpenAiKeyCondition.class)
    public ChatModel localFallbackChatModel() {
        log.warn("Using local fallback ChatModel because spring.ai.openai.api-key is empty or placeholder.");
        return new LocalFallbackChatModel();
    }

    @Bean
    @Primary
    @Conditional(LocalAiFallbackConfiguration.EmbeddingDisabledOrMissingOpenAiKeyCondition.class)
    public EmbeddingModel localFallbackEmbeddingModel(
            @Value("${spring.ai.openai.embedding.options.dimensions:1536}") Integer dimensions
    ) {
        int embeddingDimensions = dimensions == null || dimensions <= 0 ? 1536 : dimensions;
        log.warn("Using local fallback EmbeddingModel with {} dimensions.", embeddingDimensions);
        return new LocalFallbackEmbeddingModel(embeddingDimensions);
    }

    static class MissingOpenAiKeyCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isMissingOpenAiKey(context.getEnvironment());
        }
    }

    static class EmbeddingDisabledOrMissingOpenAiKeyCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean embeddingEnabled = Boolean.parseBoolean(
                    environment.getProperty("app.ai.embedding.enabled", "false")
            );
            return !embeddingEnabled || isMissingOpenAiKey(environment);
        }
    }

    private static boolean isMissingOpenAiKey(Environment environment) {
        String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
        return !StringUtils.hasText(apiKey) || PLACEHOLDER_API_KEY.equals(apiKey);
    }

    private static class LocalFallbackChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(LOCAL_AI_DISABLED_MESSAGE))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().build();
        }
    }

    private static class LocalFallbackEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        private LocalFallbackEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            int size = request.getInstructions() == null ? 0 : request.getInstructions().size();
            List<Embedding> embeddings = IntStream.range(0, size)
                    .mapToObj(index -> new Embedding(new float[dimensions], index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return new float[dimensions];
        }
    }
}
