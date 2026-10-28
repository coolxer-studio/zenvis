package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.minio.MinioBucketDomain;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.JacksonUtil;
import com.coolxer.utils.StorageObjectKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * MinIO 中的附件派生对象。业务代码只把这里当作持久来源，本地文件均为可删除临时文件。
 */
@Slf4j
@Service
public class AttachmentDerivedStore {

    private final MinioService minioService;
    private final MinioProperties minioProperties;

    public AttachmentDerivedStore(MinioService minioService, MinioProperties minioProperties) {
        this.minioService = minioService;
        this.minioProperties = minioProperties;
    }

    public String manifestKey(ChatAttachmentRecord record) {
        return StringUtils.hasText(record.getManifestKey())
                ? record.getManifestKey()
                : StorageObjectKey.chatDerivedManifest(record.getUserId(), record.getFileId());
    }

    public String chunksKey(ChatAttachmentRecord record) {
        return StringUtils.hasText(record.getChunksKey())
                ? record.getChunksKey()
                : StorageObjectKey.chatDerivedChunks(record.getUserId(), record.getFileId());
    }

    public AttachmentManifest readManifest(ChatAttachmentRecord record) {
        if (record == null) {
            return null;
        }
        try (InputStream input = minioService.downloadFile(manifestKey(record), chatBucket())) {
            String content = new String(input.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8);
            return JacksonUtil.toObject(content, AttachmentManifest.class);
        } catch (Exception e) {
            log.warn("读取附件清单失败: fileId={}, error={}", record.getFileId(), e.getMessage());
            return null;
        }
    }

    public Path downloadChunks(ChatAttachmentRecord record) throws IOException {
        Path path = Files.createTempFile("zenvis-attachment-read-", ".jsonl");
        try (InputStream input = minioService.downloadFile(chunksKey(record), chatBucket())) {
            Files.copy(input, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return path;
        } catch (Exception e) {
            Files.deleteIfExists(path);
            throw new IOException("下载附件分块失败", e);
        }
    }

    public void forEachChunk(ChatAttachmentRecord record, Consumer<AttachmentChunk> consumer) {
        Path path = null;
        try {
            path = downloadChunks(record);
            forEachChunk(path, consumer);
        } catch (Exception e) {
            log.warn("流式读取附件分块失败: fileId={}, error={}", record.getFileId(), e.getMessage());
        } finally {
            deleteQuietly(path);
        }
    }

    public void forEachChunk(Path path, Consumer<AttachmentChunk> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                AttachmentChunk chunk = JacksonUtil.toObject(line, AttachmentChunk.class);
                if (chunk != null) {
                    consumer.accept(chunk);
                }
            }
        }
    }

    public List<AttachmentChunk> readChunks(ChatAttachmentRecord record, int limit) {
        List<AttachmentChunk> chunks = new ArrayList<>();
        if (record == null || limit <= 0) {
            return chunks;
        }
        Path path = null;
        try {
            path = downloadChunks(record);
            forEachChunk(path, chunk -> {
                if (chunks.size() < limit) {
                    chunks.add(chunk);
                }
            });
        } catch (Exception e) {
            log.warn("读取附件分块失败: fileId={}, error={}", record.getFileId(), e.getMessage());
        } finally {
            deleteQuietly(path);
        }
        return chunks;
    }

    public String uploadManifest(ChatAttachmentRecord record, AttachmentManifest manifest) throws IOException {
        String content = JacksonUtil.toJson(manifest);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String key = manifestKey(record);
        try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
            minioService.uploadStream(key, input, bytes.length, "application/json", chatBucket());
        }
        return key;
    }

    public String uploadChunks(ChatAttachmentRecord record, Path chunksPath) throws IOException {
        if (chunksPath == null || !Files.isRegularFile(chunksPath)) {
            return null;
        }
        String key = chunksKey(record);
        try (InputStream input = Files.newInputStream(chunksPath)) {
            minioService.uploadStream(key, input, Files.size(chunksPath),
                    "application/x-ndjson", chatBucket());
        }
        return key;
    }

    public void deleteDerived(ChatAttachmentRecord record) {
        if (record == null || record.getUserId() == null || !StringUtils.hasText(record.getFileId())) {
            return;
        }
        try {
            minioService.deletePrefix(
                    StorageObjectKey.chatDerivedPrefix(record.getUserId(), record.getFileId()),
                    chatBucket());
        } catch (Exception e) {
            log.warn("删除附件派生数据失败: fileId={}, error={}", record.getFileId(), e.getMessage());
        }
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String chatBucket() {
        return minioProperties == null
                ? MinioProperties.DEFAULT_CHAT_BUCKET
                : minioProperties.bucket(MinioBucketDomain.CHAT);
    }
}
