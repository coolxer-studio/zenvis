package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 模型基础服务
 */
@Slf4j
@Service
public class AIBaseService {

    private static final String AUTO_MODEL = "auto";

    private static final String LEGACY_AUTO_MODEL = "x-sage-v1";

    private static final String MODEL = "model";

    private static final String DESC = "desc";

    private static final Duration MODEL_LIST_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration MODEL_LIST_CACHE_TTL = Duration.ofSeconds(60);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(MODEL_LIST_TIMEOUT)
            .build();

    private final String openAiBaseUrl;

    private final String openAiApiKey;

    private final String defaultChatModel;

    private volatile ModelCatalog cachedCatalog;

    public AIBaseService(
            @Value("${spring.ai.openai.base-url:}") String openAiBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String defaultChatModel
    ) {
        this.openAiBaseUrl = openAiBaseUrl;
        this.openAiApiKey = openAiApiKey;
        this.defaultChatModel = defaultChatModel;
    }

    public List<Map<String, String>> getModels() {
        ModelCatalog catalog = loadModelCatalog();
        List<Map<String, String>> models = new ArrayList<>();
        models.add(modelMap(AUTO_MODEL, "系统自动选择合适的模型"));
        catalog.modelIds().stream()
                .filter(StringUtils::hasText)
                .filter(modelId -> !AUTO_MODEL.equals(modelId))
                .sorted(Comparator.naturalOrder())
                .forEach(modelId -> models.add(modelMap(modelId, modelDescription(modelId))));
        return models;
    }

    public boolean isModelSupported(String model) {
        if (!StringUtils.hasText(model) || AUTO_MODEL.equals(model) || LEGACY_AUTO_MODEL.equals(model)) {
            return true;
        }
        ModelCatalog catalog = loadModelCatalog();
        return !catalog.remoteAvailable() || catalog.modelIds().contains(model);
    }

    public String resolveChatModel(String requestedModel, boolean deepThinking, boolean hasImageAttachment) {
        if (StringUtils.hasText(requestedModel)
                && !AUTO_MODEL.equals(requestedModel)
                && !LEGACY_AUTO_MODEL.equals(requestedModel)) {
            return requestedModel;
        }
        ModelCatalog catalog = loadModelCatalog();
        return selectAutoModel(catalog.modelIds(), deepThinking, hasImageAttachment);
    }

    private ModelCatalog loadModelCatalog() {
        ModelCatalog snapshot = cachedCatalog;
        long now = System.nanoTime();
        if (snapshot != null && now < snapshot.expiresAtNanos()) {
            return snapshot;
        }
        ModelCatalog refreshed = fetchModelCatalog(now);
        cachedCatalog = refreshed;
        return refreshed;
    }

    private ModelCatalog fetchModelCatalog(long now) {
        Set<String> modelIds = new LinkedHashSet<>();
        if (StringUtils.hasText(defaultChatModel)) {
            modelIds.add(defaultChatModel);
        }

        if (!StringUtils.hasText(openAiBaseUrl) || !StringUtils.hasText(openAiApiKey)) {
            return new ModelCatalog(modelIds, false, expiresAt(now));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAiModelsUrl()))
                    .timeout(MODEL_LIST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Fetch OpenAI model list failed, status={}", response.statusCode());
                return new ModelCatalog(modelIds, false, expiresAt(now));
            }
            modelIds.addAll(parseModelIds(response.body()));
            return new ModelCatalog(modelIds, true, expiresAt(now));
        } catch (Exception e) {
            log.warn("Fetch OpenAI model list failed: {}", e.getMessage());
            return new ModelCatalog(modelIds, false, expiresAt(now));
        }
    }

    List<String> parseModelIds(String responseBody) throws IOException {
        JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(responseBody);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<String> modelIds = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("id").asText("");
            if (isChatModelCandidate(id)) {
                modelIds.add(id);
            }
        }
        return modelIds;
    }

    String selectAutoModel(Set<String> modelIds, boolean deepThinking, boolean hasImageAttachment) {
        if (modelIds.isEmpty()) {
            return null;
        }
        if (hasImageAttachment) {
            String visionModel = findFirst(modelIds, this::isVisionModel);
            if (visionModel != null) {
                return visionModel;
            }
        }
        if (deepThinking) {
            String reasoningModel = findFirst(modelIds, this::isReasoningModel);
            if (reasoningModel != null) {
                return reasoningModel;
            }
        }
        if (StringUtils.hasText(defaultChatModel) && modelIds.contains(defaultChatModel)) {
            return defaultChatModel;
        }
        return modelIds.iterator().next();
    }

    private String findFirst(Set<String> modelIds, java.util.function.Predicate<String> predicate) {
        return modelIds.stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    private boolean isChatModelCandidate(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return false;
        }
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return !normalized.contains("embedding")
                && !normalized.contains("embed")
                && !normalized.contains("rerank")
                && !normalized.contains("whisper")
                && !normalized.contains("tts")
                && !normalized.contains("audio")
                && !normalized.contains("moderation");
    }

    private boolean isVisionModel(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("vision")
                || normalized.contains("-vl")
                || normalized.contains("/vl")
                || normalized.contains("qwen-vl")
                || normalized.contains("gpt-4o")
                || normalized.contains("gpt-4.1")
                || normalized.contains("gpt-4-turbo");
    }

    private boolean isReasoningModel(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("qwen3")
                || normalized.contains("deepseek-r1")
                || normalized.contains("deepseek-reasoner")
                || normalized.contains("reasoning")
                || normalized.matches(".*\\bo[134](?:[-/].*)?$");
    }

    private Map<String, String> modelMap(String model, String desc) {
        Map<String, String> modelMap = new LinkedHashMap<>();
        modelMap.put(MODEL, model);
        modelMap.put(DESC, desc);
        return modelMap;
    }

    private String modelDescription(String modelId) {
        if (StringUtils.hasText(defaultChatModel) && defaultChatModel.equals(modelId)) {
            return "配置的默认聊天模型";
        }
        if (isVisionModel(modelId)) {
            return "OpenAI 兼容服务返回的多模态模型";
        }
        if (isReasoningModel(modelId)) {
            return "OpenAI 兼容服务返回的推理模型";
        }
        return "OpenAI 兼容服务返回的可用模型";
    }

    private String openAiModelsUrl() {
        String normalizedBaseUrl = openAiBaseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/models";
        }
        return normalizedBaseUrl + "/v1/models";
    }

    private long expiresAt(long now) {
        return now + MODEL_LIST_CACHE_TTL.toNanos();
    }

    private record ModelCatalog(Set<String> modelIds, boolean remoteAvailable, long expiresAtNanos) {
    }
}
