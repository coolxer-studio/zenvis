/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coolxer.service.dih.rag;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.configuration.ai.AiEmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * VectorStoreInitializer
 * <p>
 * 使用示例：
 * writeLog(id, "文档加载到RAG......");
 * try {
 * vectorStoreInitializerService.loadDocToRag(plugin.getPackageName().replaceAll("\\.", "_"), pluginPackTool.getDocPath());
 * } catch (Exception e) {
 * log.error("加载到RAG失败......", e);
 * writeLog(id, "加载到RAG失败......，跳过");
 * }
 */

@Service
public class VectorStoreInitializerService {

    private static final long EMBEDDING_UNAVAILABLE_COOLDOWN_MILLIS = 60_000L;

    private final Logger logger = LoggerFactory.getLogger(VectorStoreInitializerService.class);

    @Autowired
    private VectorStoreDelegate vectorStoreDelegate;

    @Autowired(required = false)
    @Qualifier("redisVectorStoreCustom")
    private RedisVectorStore redisVectorStore;

    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Autowired(required = false)
    private JedisPooled jedisPooled;

    @Autowired
    private AiEmbeddingProperties embeddingProperties;

    @Value("${spring.ai.openai.embedding.base-url:${spring.ai.openai.base-url:}}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key:}}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.embedding.embeddings-path:/v1/embeddings}")
    private String embeddingsPath;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    @Value("${app.ai.embedding.health-check-timeout-seconds:5}")
    private long embeddingHealthCheckTimeoutSeconds;

    private volatile long embeddingUnavailableUntil;

    /**
     * 插件操作前使用轻量、单次 HTTP 请求探测 Embedding 服务，避免进入 Spring AI
     * 的长时间指数退避重试后一直占用插件安装/升级线程。
     */
    public boolean isRagAvailable() {
        if (!embeddingProperties.isEnabled()) {
            logger.info("RAG is disabled by app.ai.embedding.enabled=false.");
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < embeddingUnavailableUntil) {
            logger.info("Skip Embedding health check during unavailable cooldown.");
            return false;
        }
        if (!StringUtils.hasText(embeddingBaseUrl) || !StringUtils.hasText(embeddingModel)) {
            markEmbeddingUnavailable(now);
            logger.warn("Embedding health check skipped because base URL or model is not configured.");
            return false;
        }

        try {
            Duration timeout = Duration.ofSeconds(Math.max(1L, embeddingHealthCheckTimeoutSeconds));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", embeddingModel);
            body.put("input", List.of("zenvis-rag-health-check"));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(resolveEmbeddingUri())
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JacksonConfig.OBJECT_MAPPER.writeValueAsString(body)));
            if (StringUtils.hasText(embeddingApiKey)) {
                requestBuilder.header("Authorization", "Bearer " + embeddingApiKey);
            }
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(timeout)
                    .build();
            HttpResponse<Void> response = client.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                embeddingUnavailableUntil = 0L;
                return true;
            }
            markEmbeddingUnavailable(now);
            logger.warn("Embedding health check failed with HTTP {}. RAG will be skipped temporarily.",
                    response.statusCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markEmbeddingUnavailable(now);
            logger.warn("Embedding health check was interrupted. RAG will be skipped temporarily.");
            return false;
        } catch (Exception e) {
            markEmbeddingUnavailable(now);
            logger.warn("Embedding health check failed. RAG will be skipped temporarily.", e);
            return false;
        }
    }

    private URI resolveEmbeddingUri() {
        String baseUrl = embeddingBaseUrl.endsWith("/")
                ? embeddingBaseUrl.substring(0, embeddingBaseUrl.length() - 1)
                : embeddingBaseUrl;
        String path = embeddingsPath.startsWith("/") ? embeddingsPath : "/" + embeddingsPath;
        return URI.create(baseUrl + path);
    }

    private void markEmbeddingUnavailable(long now) {
        embeddingUnavailableUntil = now + EMBEDDING_UNAVAILABLE_COOLDOWN_MILLIS;
    }

    public void loadDocToRag(String docSource, Path docPath) {
        if (!embeddingProperties.isEnabled()) {
            logger.info("Skip loading docs into RAG because app.ai.embedding.enabled=false. source={}", docSource);
            return;
        }
        VectorStore vectorStore = vectorStoreDelegate.getVectorStore("redis");
        List<MarkdownDocumentReader> markdownDocumentReaderList = loadMarkdownDocuments(docPath);
        int size = 0;
        if (markdownDocumentReaderList.isEmpty()) {
            logger.warn("No markdown documents found in the directory.");
            return;
        }
        logger.info("Start to load markdown documents into vector store......");
        for (MarkdownDocumentReader markdownDocumentReader : markdownDocumentReaderList) {
            List<Document> documents = new TokenTextSplitter(2000, 1024, 10, 10000, true).transform(markdownDocumentReader.get());
            size += documents.size();
            // 拆分 documents 列表为最大 25 个元素的子列表
            for (int i = 0; i < documents.size(); i += 25) {
                int end = Math.min(i + 25, documents.size());
                List<Document> subList = documents.subList(i, end);
                for (Document doc : subList
                ) {
                    doc.getMetadata().put("source", docSource);
                }
                vectorStore.add(subList);
            }
        }
        logger.info("Load markdown documents into vector store successfully. Load {} documents.", size);
    }

    public void unloadDocFromRag(String docSource) {
        if (!embeddingProperties.isEnabled() || redisVectorStore == null || jedisPooled == null) {
            logger.info("Skip unloading docs from RAG because Redis vector store is disabled. source={}", docSource);
            return;
        }
        logger.info("start delete data with filter");
        long total = 0;
        while (true) {
            try {
                Query query = new Query("@source:{%s}".formatted(docSource))
                        .limit(0, 1000)
                        .setNoContent(); // 只获取ID，不返回内容，提高性能

                SearchResult sr = jedisPooled.ftSearch(indexName, query);

                if (sr.getTotalResults() == 0) {
                    break;
                }

                List<String> ids = sr.getDocuments().stream()
                        .map(document -> document.getId())
                        .filter(Objects::nonNull) // 过滤空ID
                        .toList();

                if (!ids.isEmpty()) {
                    try (Pipeline pipe = jedisPooled.pipelined()) {
                        ids.forEach(id -> pipe.unlink(id));
                        pipe.sync();
                    }
                    total += ids.size();
                } else {
                    break;
                }
            } catch (Exception e) {
                logger.error("Error during data deletion", e);
                throw new IllegalStateException("Failed to unload RAG documents for source=" + docSource, e);
            }
        }
        logger.info("Unload documents from vector store successfully. Unloaded {} documents.", total);
    }

    private List<MarkdownDocumentReader> loadMarkdownDocuments(Path docPath) {
        try {
            // 检查当前运行目录是否存在markdown文件
            if (Files.exists(docPath) && Files.isDirectory(docPath)) {
                try (Stream<Path> paths = Files.walk(docPath)) {
                    List<Path> markdownFiles = paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".md"))
                            .collect(Collectors.toList());

                    if (!markdownFiles.isEmpty()) {
                        logger.info("Loading {} markdown files from current directory", markdownFiles.size());
                        return markdownFiles.stream()
                                .map(path -> {
                                    try {
                                        URI fileUri = path.toAbsolutePath().toUri();
                                        return new MarkdownDocumentReader(fileUri.toString());
                                    } catch (Exception e) {
                                        logger.warn("Failed to create MarkdownDocumentReader for file: {}", path, e);
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                    }
                }
                logger.warn("No markdown files found in current directory {}", docPath);
            } else {
                logger.warn("docPath not Found {}", docPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to load markdown documents from {}", docPath, e);
        }
        return Collections.emptyList();
    }
}
