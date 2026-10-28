package com.coolxer.service.dih.attachment;

import com.coolxer.utils.JacksonUtil;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 以 JSONL 流式写入片段，避免大文件把全部片段留在堆内。
 */
public class AttachmentChunkWriter implements Closeable {

    private final int maxChunks;
    private final BufferedWriter writer;
    private final AtomicInteger count = new AtomicInteger();
    private boolean truncated;

    public AttachmentChunkWriter(Path path, int maxChunks) throws IOException {
        this.maxChunks = Math.max(maxChunks, 1);
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    }

    public boolean add(AttachmentChunk chunk) {
        if (count.get() >= maxChunks) {
            truncated = true;
            return false;
        }
        try {
            int ordinal = count.incrementAndGet();
            chunk.setOrdinal(ordinal);
            // 固定 ID 使向量索引能够在重解析、删除和清理时精确回收。
            chunk.setId(chunk.getFileId() + "-" + ordinal);
            writer.write(JacksonUtil.toJson(chunk));
            writer.newLine();
            return true;
        } catch (IOException e) {
            throw new AttachmentParseException("写入附件片段失败", e);
        }
    }

    public int count() {
        return count.get();
    }

    public boolean truncated() {
        return truncated;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
