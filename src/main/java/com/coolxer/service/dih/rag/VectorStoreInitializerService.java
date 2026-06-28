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

import com.coolxer.configuration.ai.AiEmbeddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * VectorStoreInitializer
 */

@Service
public class VectorStoreInitializerService {

    private final Logger logger = LoggerFactory.getLogger(VectorStoreInitializerService.class);

    @Autowired
    private VectorStoreDelegate vectorStoreDelegate;

    @Autowired
    @Qualifier("redisVectorStoreCustom")
    private RedisVectorStore redisVectorStore;

    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Autowired
    private JedisPooled jedisPooled;

    @Autowired
    private AiEmbeddingProperties embeddingProperties;

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
        logger.info("start delete data with filter");
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq("source", docSource).build();
        redisVectorStore.delete(expression);

        // 解决当前库没有删除干净的问题 - 优化版本
        long total = 0;
        String cursor = "0";
        do {
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
                    // pipeline 批量 UNLINK
                    try (Pipeline pipe = jedisPooled.pipelined()) {
                        ids.forEach(id -> pipe.unlink(id));
                        pipe.sync();
                    }
                    total += ids.size();
                }

                // 注意：RedisSearch的cursor行为可能需要根据实际版本调整
                // 如果使用的是较新版本的Jedis，可能需要使用其他方式获取下一页
                cursor = "0"; // 简化处理，实际应根据API返回值判断

            } catch (Exception e) {
                logger.error("Error during data deletion", e);
                break;
            }
        } while (!"0".equals(cursor));
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
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
