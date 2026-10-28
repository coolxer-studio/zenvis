package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为附件分块维护独立向量索引。索引失败只降级为关键词检索，不影响解析结果。
 */
@Slf4j
@Service
public class AttachmentVectorIndexService {

    private static final int BATCH_SIZE = 32;

    private final AttachmentDerivedStore derivedStore;
    private final AttachmentProperties properties;
    private final boolean embeddingEnabled;

    @Autowired(required = false)
    @Qualifier("attachmentVectorStore")
    private RedisVectorStore vectorStore;

    public AttachmentVectorIndexService(
            AttachmentDerivedStore derivedStore,
            AttachmentProperties properties,
            @Value("${app.ai.embedding.enabled:false}") boolean embeddingEnabled) {
        this.derivedStore = derivedStore;
        this.properties = properties;
        this.embeddingEnabled = embeddingEnabled;
    }

    public boolean available() {
        return embeddingEnabled && vectorStore != null;
    }

    public void index(ChatAttachmentRecord record) {
        if (!available() || record == null || !StringUtils.hasText(record.getChunksKey())) {
            return;
        }
        Path chunks = null;
        try {
            chunks = derivedStore.downloadChunks(record);
            List<Document> batch = new ArrayList<>(BATCH_SIZE);
            int limit = Math.max(properties.getVector().getMaxChunks(), 0);
            int[] indexed = {0};
            derivedStore.forEachChunk(chunks, chunk -> {
                if (indexed[0] >= limit || chunk == null || !StringUtils.hasText(chunk.getText())) {
                    return;
                }
                batch.add(toDocument(record, chunk));
                indexed[0]++;
                if (batch.size() >= BATCH_SIZE) {
                    vectorStore.add(new ArrayList<>(batch));
                    batch.clear();
                }
            });
            if (!batch.isEmpty()) {
                vectorStore.add(batch);
            }
            log.info("附件向量索引完成: fileId={}, chunks={}", record.getFileId(), indexed[0]);
        } catch (Exception e) {
            log.warn("附件向量索引失败，后续使用关键词检索: fileId={}, error={}",
                    record.getFileId(), e.getMessage());
        } finally {
            derivedStore.deleteQuietly(chunks);
        }
    }

    public void delete(ChatAttachmentRecord record) {
        if (!available() || record == null) {
            return;
        }
        int count = record.getChunkCount() == null ? 0 : record.getChunkCount();
        if (count <= 0) {
            return;
        }
        List<String> ids = new ArrayList<>(count);
        for (int ordinal = 1; ordinal <= count; ordinal++) {
            ids.add(record.getFileId() + "-" + ordinal);
        }
        try {
            vectorStore.delete(ids);
        } catch (Exception e) {
            log.warn("删除附件向量索引失败: fileId={}, error={}", record.getFileId(), e.getMessage());
        }
    }

    private Document toDocument(ChatAttachmentRecord record, AttachmentChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("file_id", record.getFileId());
        metadata.put("file_name", record.getFileName());
        metadata.put("user_id", String.valueOf(record.getUserId()));
        if (StringUtils.hasText(record.getChatId())) {
            metadata.put("chat_id", record.getChatId());
        }
        metadata.put("kind", StringUtils.hasText(chunk.getKind()) ? chunk.getKind() : "text");
        metadata.put("locator", StringUtils.hasText(chunk.getLocator()) ? chunk.getLocator() : "");
        return new Document(chunk.getId(), chunk.getText(), metadata);
    }
}
