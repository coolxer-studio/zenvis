package com.coolxer.service.dih.attachment;

import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 附件关键词 + 向量混合检索。关键词检索始终可用，向量索引不可用时自动降级。
 */
@Slf4j
@Service
public class AttachmentSearchService {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private static final int RRF_K = 60;

    private final AttachmentDerivedStore derivedStore;
    private final boolean embeddingEnabled;

    @Autowired(required = false)
    @Qualifier("attachmentVectorStore")
    private RedisVectorStore vectorStore;

    public AttachmentSearchService(
            AttachmentDerivedStore derivedStore,
            @Value("${app.ai.embedding.enabled:false}") boolean embeddingEnabled) {
        this.derivedStore = derivedStore;
        this.embeddingEnabled = embeddingEnabled;
    }

    public List<SearchHit> search(List<ChatAttachmentRecord> records,
                                  String query,
                                  int topK,
                                  Integer userId) {
        if (records == null || records.isEmpty() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int boundedTopK = Math.max(1, Math.min(topK, 50));
        List<SearchHit> keywordHits = keywordSearch(records, query, Math.max(boundedTopK * 3, 20));
        List<SearchHit> vectorHits = vectorSearch(records, query, Math.max(boundedTopK * 3, 20), userId);
        if (vectorHits.isEmpty()) {
            return keywordHits.stream().limit(boundedTopK).toList();
        }
        return fuse(keywordHits, vectorHits, boundedTopK);
    }

    List<SearchHit> keywordSearch(List<ChatAttachmentRecord> records, String query, int limit) {
        List<String> terms = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        if (terms.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        long[] corpus = {0L, 0L};
        Map<String, Path> downloaded = new LinkedHashMap<>();
        try {
            for (ChatAttachmentRecord record : records) {
                try {
                    downloaded.put(record.getFileId(), derivedStore.downloadChunks(record));
                } catch (Exception e) {
                    log.warn("附件关键词索引下载失败: fileId={}, error={}",
                            record.getFileId(), e.getMessage());
                }
            }
            try {
                for (Path path : downloaded.values()) {
                    derivedStore.forEachChunk(path, chunk -> {
                        List<String> tokens = tokenize(chunk.getText());
                        corpus[0]++;
                        corpus[1] += tokens.size();
                        Set<String> unique = new HashSet<>(tokens);
                        for (String term : terms) {
                            if (unique.contains(term)) {
                                documentFrequency.merge(term, 1, Integer::sum);
                            }
                        }
                    });
                }
            } catch (java.io.IOException e) {
                log.warn("附件关键词索引扫描失败: {}", e.getMessage());
            }
            if (corpus[0] == 0) {
                return List.of();
            }
            double averageLength = Math.max((double) corpus[1] / corpus[0], 1.0);
            long documentCount = corpus[0];
            PriorityQueue<SearchHit> best = new PriorityQueue<>(
                    Comparator.comparingDouble(SearchHit::score));
            for (ChatAttachmentRecord record : records) {
                Path path = downloaded.get(record.getFileId());
                if (path == null) {
                    continue;
                }
                try {
                    derivedStore.forEachChunk(path, chunk -> scoreChunk(
                            record, chunk, terms, documentFrequency, documentCount,
                            averageLength, limit, best));
                } catch (Exception e) {
                    log.warn("附件关键词检索失败: fileId={}, error={}",
                            record.getFileId(), e.getMessage());
                }
            }
            return best.stream()
                    .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                    .toList();
        } finally {
            downloaded.values().forEach(derivedStore::deleteQuietly);
        }
    }

    private void scoreChunk(ChatAttachmentRecord record,
                            AttachmentChunk chunk,
                            List<String> terms,
                            Map<String, Integer> documentFrequency,
                            long documentCount,
                            double averageLength,
                            int limit,
                            PriorityQueue<SearchHit> best) {
        Map<String, Integer> frequencies = termFrequencies(chunk.getText(), terms);
        if (frequencies.isEmpty()) {
            return;
        }
        int length = Math.max(tokenize(chunk.getText()).size(), 1);
        double score = 0;
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            int df = documentFrequency.getOrDefault(entry.getKey(), 0);
            double idf = Math.log(1.0 + (documentCount - df + 0.5) / (df + 0.5));
            double tf = entry.getValue();
            score += idf * (tf * (BM25_K1 + 1))
                    / (tf + BM25_K1 * (1 - BM25_B + BM25_B * length / averageLength));
        }
        if (score <= 0) {
            return;
        }
        SearchHit hit = SearchHit.from(record, chunk, score, score, 0);
        if (best.size() < limit) {
            best.offer(hit);
        } else if (best.peek() != null && score > best.peek().score()) {
            best.poll();
            best.offer(hit);
        }
    }

    List<SearchHit> vectorSearch(List<ChatAttachmentRecord> records,
                                 String query,
                                 int limit,
                                 Integer userId) {
        if (!embeddingEnabled || vectorStore == null || records == null || records.isEmpty()) {
            return List.of();
        }
        try {
            List<Object> fileIds = records.stream()
                    .map(ChatAttachmentRecord::getFileId)
                    .filter(StringUtils::hasText)
                    .map(value -> (Object) value)
                    .toList();
            if (fileIds.isEmpty()) {
                return List.of();
            }
            FilterExpressionBuilder filters = new FilterExpressionBuilder();
            Filter.Expression expression = userId == null
                    ? filters.in("file_id", fileIds).build()
                    : filters.and(
                    filters.in("file_id", fileIds),
                    filters.eq("user_id", String.valueOf(userId))
            ).build();
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .filterExpression(expression)
                    .build());
            if (documents == null) {
                return List.of();
            }
            Map<String, ChatAttachmentRecord> byFileId = new LinkedHashMap<>();
            for (ChatAttachmentRecord record : records) {
                byFileId.put(record.getFileId(), record);
            }
            List<SearchHit> hits = new ArrayList<>();
            for (Document document : documents) {
                if (document == null || !StringUtils.hasText(document.getText())) {
                    continue;
                }
                Map<String, Object> metadata = document.getMetadata() == null
                        ? Map.of() : document.getMetadata();
                String fileId = stringValue(metadata.get("file_id"));
                ChatAttachmentRecord record = byFileId.get(fileId);
                if (record == null) {
                    continue;
                }
                AttachmentChunk chunk = AttachmentChunk.builder()
                        .id(document.getId())
                        .fileId(fileId)
                        .fileName(record.getFileName())
                        .kind(stringValue(metadata.get("kind")))
                        .locator(stringValue(metadata.get("locator")))
                        .text(document.getText())
                        .build();
                double score = document.getScore() == null ? 0 : document.getScore();
                hits.add(SearchHit.from(record, chunk, score, 0, score));
            }
            return hits;
        } catch (Exception e) {
            log.warn("附件向量检索失败，降级为关键词检索: {}", e.getMessage());
            return List.of();
        }
    }

    private List<SearchHit> fuse(List<SearchHit> keywordHits,
                                 List<SearchHit> vectorHits,
                                 int limit) {
        Map<String, SearchHit> merged = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        for (int index = 0; index < keywordHits.size(); index++) {
            SearchHit hit = keywordHits.get(index);
            merged.put(hit.chunkId(), hit);
            scores.merge(hit.chunkId(), 1.0 / (RRF_K + index + 1), Double::sum);
        }
        for (int index = 0; index < vectorHits.size(); index++) {
            SearchHit hit = vectorHits.get(index);
            merged.putIfAbsent(hit.chunkId(), hit);
            scores.merge(hit.chunkId(), 1.0 / (RRF_K + index + 1), Double::sum);
        }
        return merged.values().stream()
                .map(hit -> hit.withFusedScore(scores.getOrDefault(hit.chunkId(), 0d)))
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private Map<String, Integer> termFrequencies(String text, List<String> terms) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return Map.of();
        }
        Set<String> wanted = new HashSet<>(terms);
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            if (wanted.contains(token)) {
                frequencies.merge(token, 1, Integer::sum);
            }
        }
        return frequencies;
    }

    static List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (isAsciiWord(current)) {
                flushCjk(cjk, tokens);
                ascii.append(current);
            } else if (isCjk(current)) {
                flushAscii(ascii, tokens);
                cjk.append(current);
            } else {
                flushAscii(ascii, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushAscii(ascii, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushAscii(StringBuilder ascii, List<String> tokens) {
        if (ascii.length() >= 2) {
            tokens.add(ascii.toString());
        }
        ascii.setLength(0);
    }

    private static void flushCjk(StringBuilder cjk, List<String> tokens) {
        if (cjk.length() == 1) {
            tokens.add(cjk.toString());
        } else if (cjk.length() > 1) {
            for (int index = 0; index < cjk.length() - 1; index++) {
                tokens.add(cjk.substring(index, index + 2));
            }
        }
        cjk.setLength(0);
    }

    private static boolean isAsciiWord(char value) {
        return (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')
                || value == '_';
    }

    private static boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record SearchHit(String fileId,
                            String fileName,
                            String chunkId,
                            String locator,
                            String title,
                            String kind,
                            String text,
                            double score,
                            double keywordScore,
                            double vectorScore) {

        static SearchHit from(ChatAttachmentRecord record,
                              AttachmentChunk chunk,
                              double score,
                              double keywordScore,
                              double vectorScore) {
            return new SearchHit(
                    record.getFileId(),
                    record.getFileName(),
                    chunk.getId(),
                    chunk.getLocator(),
                    chunk.getTitle(),
                    chunk.getKind(),
                    chunk.getText(),
                    score,
                    keywordScore,
                    vectorScore);
        }

        SearchHit withFusedScore(double value) {
            return new SearchHit(fileId, fileName, chunkId, locator, title, kind, text,
                    value, keywordScore, vectorScore);
        }
    }
}
