package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import com.coolxer.configuration.minio.MinioBucketDomain;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.dao.mysql.repository.ChatAttachmentRecordRepository;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.StorageObjectKey;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 附件异步解析、重试、心跳恢复和未绑定附件清理。
 */
@Slf4j
@Service
public class ChatAttachmentParseService {

    private static final int QUEUE_CAPACITY = 1_000;
    private static final long ABANDONED_AFTER_MILLIS = 24L * 60L * 60L * 1000L;

    private final ChatAttachmentRecordRepository repository;
    private final AttachmentContentParser parser;
    private final AttachmentDerivedStore derivedStore;
    private final AttachmentVectorIndexService vectorIndexService;
    private final AttachmentProperties properties;
    private final MinioService minioService;
    private final MinioProperties minioProperties;
    private final ExecutorService parseExecutorPool;
    private final ThreadPoolExecutor workerPool;

    @Autowired
    public ChatAttachmentParseService(ChatAttachmentRecordRepository repository,
                                      AttachmentContentParser parser,
                                      AttachmentDerivedStore derivedStore,
                                      AttachmentVectorIndexService vectorIndexService,
                                      AttachmentProperties properties,
                                      MinioService minioService,
                                      MinioProperties minioProperties) {
        this.repository = repository;
        this.parser = parser;
        this.derivedStore = derivedStore;
        this.vectorIndexService = vectorIndexService;
        this.properties = properties;
        this.minioService = minioService;
        this.minioProperties = minioProperties;
        int concurrency = Math.max(properties.getParse().getConcurrency(), 1);
        this.parseExecutorPool = java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "attachment-parse-timeout");
            thread.setDaemon(true);
            return thread;
        });
        this.workerPool = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "attachment-parse-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.workerPool.allowCoreThreadTimeOut(true);
    }

    public void enqueue(ChatAttachmentRecord record) {
        if (record == null || !StringUtils.hasText(record.getFileId())) {
            return;
        }
        if (AttachmentStatus.UNSUPPORTED.equals(record.getParseStatus())) {
            return;
        }
        markState(record, AttachmentStatus.PENDING, 0, "等待解析");
        String fileId = record.getFileId();
        Integer userId = record.getUserId();
        try {
            workerPool.execute(() -> process(fileId, userId));
        } catch (Exception e) {
            log.warn("附件解析队列已满: fileId={}", fileId);
            markState(record, AttachmentStatus.FAILED, 0, "解析队列繁忙，请稍后重试");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverExpired(Math.max(properties.getParse().getTimeoutSeconds(), 60));
    }

    @Scheduled(fixedDelayString = "${app.ai.dih.attachment.parse.recovery-delay-ms:60000}")
    public void recoverScheduled() {
        recoverExpired(Math.max(properties.getParse().getTimeoutSeconds(), 60));
    }

    void recoverExpired(int staleSeconds) {
        Date before = new Date(System.currentTimeMillis() - Math.max(staleSeconds, 30) * 1000L);
        List<ChatAttachmentRecord> stale = repository.findByParseStatusInAndHeartbeatAtBefore(
                List.of(AttachmentStatus.PENDING, AttachmentStatus.PROCESSING), before);
        for (ChatAttachmentRecord record : stale) {
            int retries = value(record.getRetryCount());
            if (retries >= properties.getParse().getMaxRetries()) {
                markState(record, AttachmentStatus.FAILED, 100,
                        "解析任务超时且重试次数已用尽，请重新解析或上传");
                continue;
            }
            record.setRetryCount(retries + 1);
            repository.save(record);
            log.info("回收超时附件解析任务: fileId={}, retry={}", record.getFileId(), retries + 1);
            enqueue(record);
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.dih.attachment.cleanup-delay-ms:3600000}")
    public void cleanupAbandoned() {
        Date before = new Date(System.currentTimeMillis() - ABANDONED_AFTER_MILLIS);
        List<ChatAttachmentRecord> abandoned = repository.findByChatIdIsNullAndCreateTimeBefore(before);
        for (ChatAttachmentRecord record : abandoned) {
            try {
                vectorIndexService.delete(record);
                derivedStore.deleteDerived(record);
                minioService.deletePrefix(
                        StorageObjectKey.chatUserFilePrefix(record.getUserId(), record.getFileId()),
                        chatBucket());
                repository.delete(record);
                log.info("清理未绑定附件: fileId={}, userId={}", record.getFileId(), record.getUserId());
            } catch (Exception e) {
                log.warn("清理未绑定附件失败: fileId={}, error={}", record.getFileId(), e.getMessage());
            }
        }
    }

    private void process(String fileId, Integer userId) {
        Optional<ChatAttachmentRecord> optional = repository.findByFileIdAndUserId(fileId, userId);
        if (optional.isEmpty()) {
            return;
        }
        ChatAttachmentRecord record = optional.get();
        int retries = value(record.getRetryCount());
        while (true) {
            try {
                parseOnce(record);
                return;
            } catch (Exception e) {
                String reason = safeMessage(e);
                log.warn("附件解析失败: fileId={}, retry={}, error={}", fileId, retries, reason);
                if (retries >= properties.getParse().getMaxRetries()) {
                    markState(record, AttachmentStatus.FAILED, 100,
                            "解析失败：" + abbreviate(reason, 800));
                    return;
                }
                retries++;
                record.setRetryCount(retries);
                record.setParseStatus(AttachmentStatus.PROCESSING);
                record.setParseProgress(0);
                record.setParseMessage("解析失败，正在重试 " + retries + "/"
                        + properties.getParse().getMaxRetries() + "：" + abbreviate(reason, 400));
                record.setHeartbeatAt(new Date());
                repository.save(record);
            }
        }
    }

    private void parseOnce(ChatAttachmentRecord record) throws Exception {
        markState(record, AttachmentStatus.PROCESSING, 1, "开始解析");
        Path source = Files.createTempFile("zenvis-attachment-source-", ".bin");
        Path chunks = null;
        Future<AttachmentParsedContent> future = null;
        try (InputStream input = minioService.downloadFile(record.getObjectKey(), chatBucket())) {
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
            AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
            future = parseExecutorPool.submit(() -> parser.parse(
                    record.getFileId(),
                    record.getFileName(),
                    record.getContentType(),
                    source,
                    (percent, message) -> reportProgress(record, percent, message, lastHeartbeat)
            ));
            AttachmentParsedContent parsed = future.get(
                    Math.max(properties.getParse().getTimeoutSeconds(), 1), TimeUnit.SECONDS);
            chunks = parsed.getChunksPath();
            int previousChunkCount = value(record.getChunkCount());
            if (previousChunkCount > 0) {
                vectorIndexService.delete(record);
            }
            String chunksKey = derivedStore.uploadChunks(record, chunks);
            record.setChunksKey(chunksKey);
            AttachmentManifest manifest = AttachmentManifest.builder()
                    .fileId(record.getFileId())
                    .fileName(record.getFileName())
                    .contentType(record.getContentType())
                    .kind(record.getKind())
                    .format(parsed.getFormat())
                    .status(parsed.getStatus())
                    .pageCount(parsed.getPageCount())
                    .sheetCount(parsed.getSheetCount())
                    .slideCount(parsed.getSlideCount())
                    .rowCount(parsed.getRowCount())
                    .textChars(parsed.getTextChars())
                    .chunkCount(parsed.getChunkCount())
                    .truncated(parsed.getTruncated())
                    .warning(parsed.getWarning())
                    .outline(parsed.getOutline())
                    .tables(parsed.getTables())
                    .build();
            record.setManifestKey(derivedStore.uploadManifest(record, manifest));
            record.setFormat(parsed.getFormat());
            record.setParseStatus(normalizeStatus(parsed.getStatus()));
            record.setParseProgress(100);
            record.setParseMessage(StringUtils.hasText(parsed.getWarning())
                    ? parsed.getWarning() : "解析完成");
            record.setPageCount(parsed.getPageCount());
            record.setSheetCount(parsed.getSheetCount());
            record.setSlideCount(parsed.getSlideCount());
            record.setRowCount(parsed.getRowCount());
            record.setTextChars(parsed.getTextChars());
            record.setChunkCount(parsed.getChunkCount());
            record.setTruncated(Boolean.TRUE.equals(parsed.getTruncated()));
            record.setWarning(parsed.getWarning());
            record.setHeartbeatAt(new Date());
            repository.save(record);
            if (AttachmentStatus.READY.equals(record.getParseStatus())
                    || AttachmentStatus.PARTIAL.equals(record.getParseStatus())) {
                vectorIndexService.index(record);
            }
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }
            throw new AttachmentParseException("解析超过 "
                    + properties.getParse().getTimeoutSeconds() + " 秒上限", e);
        } finally {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            derivedStore.deleteQuietly(source);
            derivedStore.deleteQuietly(chunks);
        }
    }

    private void reportProgress(ChatAttachmentRecord record,
                                int percent,
                                String message,
                                AtomicLong lastHeartbeat) {
        long now = System.currentTimeMillis();
        if (percent < 100 && now - lastHeartbeat.get() < 1_500L) {
            return;
        }
        lastHeartbeat.set(now);
        int bounded = Math.max(1, Math.min(percent, 99));
        try {
            repository.updateParseState(
                    record.getFileId(),
                    record.getUserId(),
                    AttachmentStatus.PROCESSING,
                    bounded,
                    abbreviate(message, 900),
                    new Date(now));
        } catch (Exception e) {
            log.debug("更新附件解析进度失败: fileId={}, error={}", record.getFileId(), e.getMessage());
        }
    }

    private void markState(ChatAttachmentRecord record, String status, int progress, String message) {
        record.setParseStatus(status);
        record.setParseProgress(progress);
        record.setParseMessage(abbreviate(message, 900));
        record.setHeartbeatAt(new Date());
        repository.save(record);
    }

    private String normalizeStatus(String status) {
        String normalized = AttachmentStatus.normalize(status, AttachmentStatus.READY);
        return switch (normalized) {
            case AttachmentStatus.READY, AttachmentStatus.PARTIAL,
                 AttachmentStatus.UNSUPPORTED, AttachmentStatus.FAILED -> normalized;
            default -> AttachmentStatus.READY;
        };
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String safeMessage(Throwable error) {
        Throwable current = error;
        String message = error == null ? null : error.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
        }
        return StringUtils.hasText(message)
                ? message : (current == null ? "未知错误" : current.getClass().getSimpleName());
    }

    private String chatBucket() {
        return minioProperties == null
                ? MinioProperties.DEFAULT_CHAT_BUCKET
                : minioProperties.bucket(MinioBucketDomain.CHAT);
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdownNow();
        parseExecutorPool.shutdownNow();
    }
}
