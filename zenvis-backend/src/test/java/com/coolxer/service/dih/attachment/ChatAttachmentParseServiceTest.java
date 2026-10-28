package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.ai.AttachmentProperties;
import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.dao.mysql.repository.ChatAttachmentRecordRepository;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAttachmentParseServiceTest {

    @TempDir
    Path tempDir;

    private ChatAttachmentParseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void retriesTransientFailureThenMarksAttachmentReady() throws Exception {
        Fixture fixture = fixture();
        AttachmentParsedContent parsed = parsedContent();
        when(fixture.parser.parse(anyString(), anyString(), anyString(), any(Path.class), any()))
                .thenThrow(new AttachmentParseException("临时读取失败"))
                .thenReturn(parsed);

        process(fixture.service, fixture.record);

        assertThat(fixture.record.getParseStatus()).isEqualTo(AttachmentStatus.READY);
        assertThat(fixture.record.getRetryCount()).isEqualTo(1);
        assertThat(fixture.record.getChunkCount()).isEqualTo(2);
        verify(fixture.parser, times(2))
                .parse(anyString(), anyString(), anyString(), any(Path.class), any());
        verify(fixture.store).uploadChunks(any(), any(Path.class));
        verify(fixture.store).uploadManifest(any(), any(AttachmentManifest.class));
        verify(fixture.vector).index(fixture.record);
    }

    @Test
    void stopsAfterConfiguredRetriesAndRecordsFailure() throws Exception {
        Fixture fixture = fixture();
        fixture.properties.getParse().setMaxRetries(2);
        when(fixture.parser.parse(anyString(), anyString(), anyString(), any(Path.class), any()))
                .thenThrow(new AttachmentParseException("格式损坏"));

        process(fixture.service, fixture.record);

        assertThat(fixture.record.getParseStatus()).isEqualTo(AttachmentStatus.FAILED);
        assertThat(fixture.record.getRetryCount()).isEqualTo(2);
        assertThat(fixture.record.getParseMessage()).contains("格式损坏");
        verify(fixture.parser, times(3))
                .parse(anyString(), anyString(), anyString(), any(Path.class), any());
    }

    @Test
    void parseTimeoutIsReportedAsFailedWithReadableReason() throws Exception {
        Fixture fixture = fixture();
        fixture.properties.getParse().setTimeoutSeconds(1);
        fixture.properties.getParse().setMaxRetries(0);
        when(fixture.parser.parse(anyString(), anyString(), anyString(), any(Path.class), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(5_000);
                    return parsedContent();
                });

        process(fixture.service, fixture.record);

        assertThat(fixture.record.getParseStatus()).isEqualTo(AttachmentStatus.FAILED);
        assertThat(fixture.record.getParseMessage()).contains("解析超过 1 秒上限");
    }

    @Test
    void recoversExpiredTaskAndHonorsRetryLimit() {
        ChatAttachmentRecordRepository repository = mock(ChatAttachmentRecordRepository.class);
        AttachmentContentParser parser = mock(AttachmentContentParser.class);
        AttachmentDerivedStore store = mock(AttachmentDerivedStore.class);
        AttachmentVectorIndexService vector = mock(AttachmentVectorIndexService.class);
        AttachmentProperties properties = new AttachmentProperties();
        properties.getParse().setMaxRetries(2);
        ChatAttachmentParseService recovering = org.mockito.Mockito.spy(
                new ChatAttachmentParseService(repository, parser, store, vector, properties,
                        mock(MinioService.class), new MinioProperties()));
        service = recovering;
        ChatAttachmentRecord pending = record().setParseStatus(AttachmentStatus.PROCESSING);
        ChatAttachmentRecord exhausted = record()
                .setFileId("file-2")
                .setParseStatus(AttachmentStatus.PROCESSING)
                .setRetryCount(2);
        when(repository.findByParseStatusInAndHeartbeatAtBefore(any(), any(Date.class)))
                .thenReturn(List.of(pending, exhausted));
        org.mockito.Mockito.doNothing().when(recovering).enqueue(any());

        recovering.recoverExpired(30);

        assertThat(pending.getRetryCount()).isEqualTo(1);
        verify(repository, times(1)).save(pending);
        verify(recovering, times(1)).enqueue(pending);
        assertThat(exhausted.getParseStatus()).isEqualTo(AttachmentStatus.FAILED);
        verify(recovering, never()).enqueue(exhausted);
    }

    @Test
    void cleanupDeletesUnboundOriginalAndDerivedObjects() {
        ChatAttachmentRecordRepository repository = mock(ChatAttachmentRecordRepository.class);
        AttachmentVectorIndexService vector = mock(AttachmentVectorIndexService.class);
        AttachmentDerivedStore store = mock(AttachmentDerivedStore.class);
        MinioService minio = mock(MinioService.class);
        ChatAttachmentRecord record = record().setChatId(null);
        when(repository.findByChatIdIsNullAndCreateTimeBefore(any(Date.class)))
                .thenReturn(List.of(record));
        ChatAttachmentParseService cleanup = new ChatAttachmentParseService(
                repository, mock(AttachmentContentParser.class), store, vector,
                new AttachmentProperties(), minio, new MinioProperties());
        service = cleanup;

        cleanup.cleanupAbandoned();

        verify(vector).delete(record);
        verify(store).deleteDerived(record);
        verify(minio).deletePrefix("chat/7/file-1", MinioProperties.DEFAULT_CHAT_BUCKET);
        verify(repository).delete(record);
    }

    private void process(ChatAttachmentParseService parseService,
                         ChatAttachmentRecord record) {
        ReflectionTestUtils.invokeMethod(parseService, "process", record.getFileId(), record.getUserId());
    }

    private Fixture fixture() throws Exception {
        ChatAttachmentRecordRepository repository = mock(ChatAttachmentRecordRepository.class);
        AttachmentContentParser parser = mock(AttachmentContentParser.class);
        AttachmentDerivedStore store = mock(AttachmentDerivedStore.class);
        AttachmentVectorIndexService vector = mock(AttachmentVectorIndexService.class);
        MinioService minio = mock(MinioService.class);
        AttachmentProperties properties = new AttachmentProperties();
        properties.getParse().setMaxRetries(2);
        properties.getParse().setTimeoutSeconds(5);
        ChatAttachmentRecord record = record();
        when(repository.findByFileIdAndUserId(record.getFileId(), record.getUserId()))
                .thenReturn(Optional.of(record));
        when(minio.downloadFile(eq(record.getObjectKey()), eq(MinioProperties.DEFAULT_CHAT_BUCKET)))
                .thenAnswer(invocation -> new ByteArrayInputStream(
                        "source-content".getBytes(StandardCharsets.UTF_8)));
        when(store.uploadChunks(any(), any(Path.class))).thenReturn("derived/chunks.jsonl");
        when(store.uploadManifest(any(), any(AttachmentManifest.class))).thenReturn("derived/manifest.json");
        service = new ChatAttachmentParseService(repository, parser, store, vector, properties,
                minio, new MinioProperties());
        return new Fixture(service, parser, store, vector, properties, record);
    }

    private ChatAttachmentRecord record() {
        return new ChatAttachmentRecord()
                .setFileId("file-1")
                .setUserId(7)
                .setChatId("chat-1")
                .setFileName("report.txt")
                .setFileSize(14L)
                .setContentType("text/plain")
                .setKind("text")
                .setFormat("text")
                .setObjectKey("chat/7/file-1/report.txt")
                .setParseStatus(AttachmentStatus.PENDING)
                .setRetryCount(0)
                .setHeartbeatAt(new Date());
    }

    private AttachmentParsedContent parsedContent() throws Exception {
        Path chunks = tempDir.resolve("chunks-" + System.nanoTime() + ".jsonl");
        Files.writeString(chunks, "{}\n{}\n", StandardCharsets.UTF_8);
        return AttachmentParsedContent.builder()
                .status(AttachmentStatus.READY)
                .format("text")
                .textChars(14L)
                .chunkCount(2)
                .truncated(false)
                .chunksPath(chunks)
                .outline(List.of("lines:1-2 report"))
                .build();
    }

    private record Fixture(ChatAttachmentParseService service,
                           AttachmentContentParser parser,
                           AttachmentDerivedStore store,
                           AttachmentVectorIndexService vector,
                           AttachmentProperties properties,
                           ChatAttachmentRecord record) {
    }
}
