package com.coolxer.service.dih.attachment;

import com.coolxer.configuration.minio.MinioProperties;
import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.service.core.MinioService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AttachmentSearchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void tokenizesCjkBigramsAndAsciiWords() {
        assertThat(AttachmentSearchService.tokenize("季度 revenue 2026"))
                .containsExactly("季度", "revenue", "2026");
        assertThat(AttachmentSearchService.tokenize("营业收入"))
                .containsExactly("营业", "业收", "收入");
    }

    @Test
    void downloadsEachAttachmentOnceForKeywordSearchAndKeepsLocators() throws Exception {
        ChatAttachmentRecord first = record("file-1", "quarterly.pdf");
        ChatAttachmentRecord second = record("file-2", "notes.md");
        Path firstChunks = tempDir.resolve("first.jsonl");
        Files.writeString(firstChunks, String.join("\n",
                JacksonUtil.toJson(chunk("file-1", "page:1", "季度营业收入同比增长 18%")),
                JacksonUtil.toJson(chunk("file-1", "page:2", "成本保持稳定"))),
                StandardCharsets.UTF_8);
        Path secondChunks = tempDir.resolve("second.jsonl");
        Files.writeString(secondChunks,
                JacksonUtil.toJson(chunk("file-2", "lines:1-3", "季度复盘记录")),
                StandardCharsets.UTF_8);
        AttachmentDerivedStore store = spy(new AttachmentDerivedStore(
                mock(MinioService.class), mock(MinioProperties.class)));
        doReturn(firstChunks).when(store).downloadChunks(first);
        doReturn(secondChunks).when(store).downloadChunks(second);
        AttachmentSearchService service = new AttachmentSearchService(store, false);

        List<AttachmentSearchService.SearchHit> hits =
                service.keywordSearch(List.of(first, second), "季度收入", 10);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).locator()).isEqualTo("page:1");
        assertThat(hits.get(0).fileId()).isEqualTo("file-1");
        verify(store, times(1)).downloadChunks(first);
        verify(store, times(1)).downloadChunks(second);
    }

    @Test
    void searchFallsBackToKeywordsWhenEmbeddingIsDisabled() throws Exception {
        ChatAttachmentRecord record = record("file-1", "report.txt");
        Path chunks = tempDir.resolve("fallback.jsonl");
        Files.writeString(chunks,
                JacksonUtil.toJson(chunk("file-1", "lines:1-4", "异常流量在夜间出现")),
                StandardCharsets.UTF_8);
        AttachmentDerivedStore store = spy(new AttachmentDerivedStore(
                mock(MinioService.class), mock(MinioProperties.class)));
        doReturn(chunks).when(store).downloadChunks(record);
        AttachmentSearchService service = new AttachmentSearchService(store, false);

        List<AttachmentSearchService.SearchHit> hits =
                service.search(List.of(record), "夜间异常", 8, 7);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).locator()).isEqualTo("lines:1-4");
        assertThat(hits.get(0).vectorScore()).isZero();
    }

    private ChatAttachmentRecord record(String fileId, String fileName) {
        return new ChatAttachmentRecord()
                .setFileId(fileId)
                .setUserId(7)
                .setFileName(fileName)
                .setFormat("text")
                .setParseStatus(AttachmentStatus.READY);
    }

    private AttachmentChunk chunk(String fileId, String locator, String text) {
        return AttachmentChunk.builder()
                .id(fileId + "-" + locator)
                .fileId(fileId)
                .fileName(fileId + ".txt")
                .kind("text")
                .locator(locator)
                .ordinal(1)
                .text(text)
                .build();
    }
}
