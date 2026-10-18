package com.coolxer.service.dih.rag;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.configuration.ai.AiEmbeddingProperties;
import com.coolxer.dao.mysql.entity.RagRetrievalLog;
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

    @Autowired
    private RagRetrievalLogService ragRetrievalLogService;

    @Value("${spring.ai.vectorstore.redis.index:}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix:embedding:}")
    private String prefix;

    public List<RagDocument> listDocuments(String keyword, String source) {
        return listDocuments(keyword, source, null);
    }

    public List<RagDocument> listDocuments(String keyword, String source, String knowledgeBaseId) {
        return loadAllDocuments().stream()
                .filter(document -> matchSource(document, source))
                .filter(document -> matchKnowledgeBase(document, knowledgeBaseId))
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
        return searchDocuments(query, topK, source, null);
    }

    public List<RagDocument> searchDocuments(String query, int topK, String source, String knowledgeBaseId) {
        long startedAt = System.nanoTime();
        int boundedTopK = Math.min(Math.max(topK, 1), MAX_TOP_K);
        String status = RagRetrievalLog.STATUS_OK;
        String errorMessage = null;
        List<RagDocument> documents = List.of();
        try {
            if (!isVectorStoreAvailable("searchDocuments") || !StringUtils.isNotBlank(query)) {
                if (!StringUtils.isNotBlank(query)) {
                    status = RagRetrievalLog.STATUS_EMPTY_QUERY;
                }
                return List.of();
            }
            documents = doSearchDocuments(query, boundedTopK, source, knowledgeBaseId);
            if (documents.isEmpty()) {
                status = RagRetrievalLog.STATUS_NO_MATCH;
            }
            return documents;
        } catch (Exception e) {
            status = RagRetrievalLog.STATUS_RETRIEVAL_ERROR;
            errorMessage = e.getMessage();
            return List.of();
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            recordManualRetrieval(query, boundedTopK, source, knowledgeBaseId, documents, status, durationMs, errorMessage);
        }
    }

    private void recordManualRetrieval(String query, int topK, String source, String knowledgeBaseId,
                                       List<RagDocument> documents, String status, long durationMs, String errorMessage) {
        try {
            Integer kbId = null;
            if (StringUtils.isNotBlank(knowledgeBaseId)) {
                try { kbId = Integer.valueOf(knowledgeBaseId); } catch (NumberFormatException ignored) { }
            }
            if (kbId == null && documents != null && !documents.isEmpty()) {
                // 全局检索未指定知识库时，按命中数最多的知识库归属，便于按知识库查看审计记录
                kbId = RagRetrievalLogService.dominantKnowledgeBaseId(
                        documents.stream().map(RagDocument::metadata).toList());
            }
            List<Object> summaries = documents.stream()
                    .map(doc -> {
                        Map<String, Object> md = doc.metadata();
                        return RagRetrievalLogService.summary(
                                doc.id(),
                                doc.source(),
                                stringValue(md.get("knowledge_base_name")),
                                stringValue(md.get("dataset_name")),
                                stringValue(md.get("file_name")),
                                doc.text());
                    })
                    .map(s -> (Object) s)
                    .toList();
            RagRetrievalLog entity = new RagRetrievalLog()
                    .setRequestType(RagRetrievalLog.REQUEST_TYPE_MANUAL)
                    .setQueryText(query == null ? "" : query)
                    .setTopK(topK)
                    .setSource(source)
                    .setKnowledgeBaseId(kbId)
                    .setDocumentCount(documents.size())
                    .setStatus(status)
                    .setDurationMs(durationMs)
                    .setResultJson(ragRetrievalLogService.toResultJson(summaries))
                    .setErrorMessage(errorMessage);
            ragRetrievalLogService.saveLog(entity);
        } catch (Exception e) {
            log.warn("记录管理端检索日志失败", e);
        }
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

    protected List<RagDocument> doSearchDocuments(String query, int topK, String source, String knowledgeBaseId) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK);
            boolean hasSource = StringUtils.isNotBlank(source);
            boolean hasKb = StringUtils.isNotBlank(knowledgeBaseId);
            if (hasSource || hasKb) {
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                Filter.Expression expression;
                if (hasSource && hasKb) {
                    expression = filterBuilder.and(
                            filterBuilder.eq("source", source),
                            filterBuilder.eq("knowledge_base_id", knowledgeBaseId)
                    ).build();
                } else if (hasSource) {
                    expression = filterBuilder.eq("source", source).build();
                } else {
                    expression = filterBuilder.eq("knowledge_base_id", knowledgeBaseId).build();
                }
                builder.filterExpression(expression);
            }
            return redisVectorStore.similaritySearch(builder.build()).stream()
                    .map(this::fromSpringDocument)
                    .filter(Objects::nonNull)
                    .map(this::enrichMetadataFromRedis)
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

    /**
     * similaritySearch 返回的 Document 只包含索引 schema 中声明的 metadata 字段，
     * 从 Redis JSON 读取完整文档补全 metadata（如知识库名称、标签、数据集、文件名）。
     */
    private RagDocument enrichMetadataFromRedis(RagDocument document) {
        if (document == null || jedisPooled == null || !StringUtils.isNotBlank(document.id())) {
            return document;
        }
        try {
            Object json = jedisPooled.jsonGet(prefix + document.id());
            if (json == null) {
                return document;
            }
            Map<String, Object> jsonDocument = parseObject(json);
            if (jsonDocument.isEmpty()) {
                return document;
            }
            RagDocument full = fromRedisJsonDocument(document.id(), jsonDocument);
            if (full == null) {
                return document;
            }
            // 以完整 JSON 的 metadata 为基础，保留 similaritySearch 返回的额外字段（如 distance）
            Map<String, Object> mergedMetadata = new LinkedHashMap<>(full.metadata());
            document.metadata().forEach(mergedMetadata::putIfAbsent);
            return new RagDocument(document.id(), document.text(), mergedMetadata, document.source());
        } catch (Exception e) {
            log.warn("Enrich RAG document metadata failed, id={}", document.id(), e);
            return document;
        }
    }

    RagDocument fromRedisDocument(redis.clients.jedis.search.Document document) {
        if (document == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : document.getProperties()) {
            if ("$".equals(entry.getKey())) {
                Map<String, Object> jsonDocument = parseObject(entry.getValue());
                if (!jsonDocument.isEmpty()) {
                    return fromRedisJsonDocument(document.getId(), jsonDocument);
                }
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        String content = "";
        String text = "";
        for (Map.Entry<String, Object> entry : document.getProperties()) {
            String key = entry.getKey();
            if ("embedding".equals(key)) {
                continue;
            }
            Object value = normalizeValue(entry.getValue());
            if ("metadata".equals(key)) {
                metadata.putAll(parseObject(value));
            }
            else if ("content".equals(key)) {
                content = stringValue(value);
            }
            else if ("text".equals(key)) {
                text = stringValue(value);
            }
            else if (!"content".equals(key) && !"text".equals(key)) {
                metadata.put(key, value);
            }
        }
        return new RagDocument(
                document.getId(),
                firstNonBlank(content, text),
                metadata,
                stringValue(metadata.get("source"))
        );
    }

    private RagDocument fromRedisJsonDocument(String id, Map<String, Object> jsonDocument) {
        Map<String, Object> values = new LinkedHashMap<>(jsonDocument);
        values.remove("embedding");
        String content = stringValue(values.remove("content"));
        String text = stringValue(values.remove("text"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.putAll(parseObject(values.remove("metadata")));
        metadata.putAll(values);
        return new RagDocument(
                id,
                firstNonBlank(content, text),
                metadata,
                stringValue(metadata.get("source"))
        );
    }

    private Map<String, Object> parseObject(Object value) {
        if (value == null) {
            return Map.of();
        }
        value = normalizeValue(value);
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

    private boolean matchKnowledgeBase(RagDocument document, String knowledgeBaseId) {
        if (!StringUtils.isNotBlank(knowledgeBaseId)) {
            return true;
        }
        if (document.metadata() == null) {
            return false;
        }
        Object kbId = document.metadata().get("knowledge_base_id");
        return kbId != null && knowledgeBaseId.equals(String.valueOf(kbId));
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
