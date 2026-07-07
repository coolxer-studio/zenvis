package com.coolxer.service.dih.rag;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.configuration.ai.AiEmbeddingProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class RagDocumentManagementService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentManagementService.class);

    private static final int MAX_LIST_SIZE = 10_000;

    private static final int MAX_TOP_K = 100;

    private static final ObjectMapper OBJECT_MAPPER = JacksonConfig.OBJECT_MAPPER.copy();

    @Autowired(required = false)
    @Qualifier("redisVectorStoreCustom")
    private RedisVectorStore redisVectorStore;

    @Autowired(required = false)
    private JedisPooled jedisPooled;

    @Autowired
    private AiEmbeddingProperties embeddingProperties;

    @Value("${spring.ai.vectorstore.redis.index:}")
    private String indexName;

    public List<RagDocument> listDocuments(String keyword, String source) {
        return loadAllDocuments().stream()
                .filter(document -> matchSource(document, source))
                .filter(document -> matchKeyword(document, keyword))
                .toList();
    }

    public RagDocument getDocumentById(String documentId) {
        if (!StringUtils.isNotBlank(documentId)) {
            return null;
        }
        return loadAllDocuments().stream()
                .filter(document -> documentId.equals(document.id()))
                .findFirst()
                .orElse(null);
    }

    public List<RagDocument> searchDocuments(String query, int topK, String source) {
        if (!isVectorStoreAvailable("searchDocuments") || !StringUtils.isNotBlank(query)) {
            return List.of();
        }
        int boundedTopK = Math.min(Math.max(topK, 1), MAX_TOP_K);
        return doSearchDocuments(query, boundedTopK, source);
    }

    public boolean deleteDocumentById(String documentId) {
        if (!StringUtils.isNotBlank(documentId)) {
            return false;
        }
        return deleteDocumentsByIds(List.of(documentId));
    }

    public boolean deleteDocumentsByIds(List<String> documentIds) {
        if (!isVectorStoreAvailable("deleteDocumentsByIds")) {
            return false;
        }
        List<String> ids = documentIds == null ? List.of() : documentIds.stream()
                .filter(StringUtils::isNotBlank)
                .toList();
        if (ids.isEmpty()) {
            return true;
        }
        try {
            redisVectorStore.delete(ids);
            return true;
        } catch (Exception e) {
            log.error("Delete RAG documents failed, ids={}", ids, e);
            return false;
        }
    }

    protected List<RagDocument> loadAllDocuments() {
        if (!isVectorStoreAvailable("listDocuments")) {
            return List.of();
        }
        List<RagDocument> redisSearchDocuments = loadDocumentsFromRedisSearch();
        if (!redisSearchDocuments.isEmpty()) {
            return redisSearchDocuments;
        }
        return loadDocumentsFromVectorSearchFallback();
    }

    protected List<RagDocument> doSearchDocuments(String query, int topK, String source) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK);
            if (StringUtils.isNotBlank(source)) {
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                Filter.Expression expression = filterBuilder.eq("source", source).build();
                builder.filterExpression(expression);
            }
            return redisVectorStore.similaritySearch(builder.build()).stream()
                    .map(this::fromSpringDocument)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("Search RAG documents failed, query={}, source={}", query, source, e);
            return List.of();
        }
    }

    private List<RagDocument> loadDocumentsFromRedisSearch() {
        if (jedisPooled == null || !StringUtils.isNotBlank(indexName)) {
            return List.of();
        }
        try {
            SearchResult searchResult = jedisPooled.ftSearch(indexName, new Query("*").limit(0, MAX_LIST_SIZE));
            return searchResult.getDocuments().stream()
                    .map(this::fromRedisDocument)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("Load RAG documents through Redis Search failed, fallback to vector search. index={}", indexName, e);
            return List.of();
        }
    }

    private List<RagDocument> loadDocumentsFromVectorSearchFallback() {
        try {
            return redisVectorStore.similaritySearch(SearchRequest.builder()
                            .query("*")
                            .topK(MAX_LIST_SIZE)
                            .build())
                    .stream()
                    .map(this::fromSpringDocument)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("Load RAG documents through vector search failed", e);
            return List.of();
        }
    }

    protected boolean isVectorStoreAvailable(String operation) {
        if (embeddingProperties == null || !embeddingProperties.isEnabled()) {
            log.info("Skip RAG document operation {} because app.ai.embedding.enabled=false.", operation);
            return false;
        }
        if (redisVectorStore == null) {
            log.warn("Skip RAG document operation {} because Redis vector store is not available.", operation);
            return false;
        }
        return true;
    }

    private RagDocument fromSpringDocument(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        return new RagDocument(document.getId(), document.getText(), metadata, stringValue(metadata.get("source")));
    }

    private RagDocument fromRedisDocument(redis.clients.jedis.search.Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.getProperties()) {
            String key = entry.getKey();
            if ("embedding".equals(key)) {
                continue;
            }
            Object value = normalizeValue(entry.getValue());
            if ("metadata".equals(key)) {
                metadata.putAll(parseMetadata(value));
            }
            else if (!"content".equals(key) && !"text".equals(key)) {
                metadata.put(key, value);
            }
        }
        String text = firstNonBlank(document.getString("content"), document.getString("text"));
        return new RagDocument(document.getId(), text, metadata, stringValue(metadata.get("source")));
    }

    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> metadata.put(String.valueOf(key), normalizeValue(mapValue)));
            return metadata;
        }
        String text = stringValue(value);
        if (!StringUtils.isNotBlank(text)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean matchSource(RagDocument document, String source) {
        return !StringUtils.isNotBlank(source) || source.equals(document.source());
    }

    private boolean matchKeyword(RagDocument document, String keyword) {
        if (!StringUtils.isNotBlank(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(document.id(), normalizedKeyword)
                || containsIgnoreCase(document.text(), normalizedKeyword)
                || containsIgnoreCase(document.source(), normalizedKeyword)
                || containsIgnoreCase(String.valueOf(document.metadata()), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record RagDocument(String id, String text, Map<String, Object> metadata, String source) {
        public RagDocument {
            metadata = metadata == null ? Map.of() : metadata;
        }
    }
}
