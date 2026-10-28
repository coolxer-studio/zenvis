package com.coolxer.service.dih.attachment;

import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentContentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void tableQueryFiltersGroupsAndAggregatesRows() throws Exception {
        ChatAttachmentRecord record = tableRecord();
        Path chunks = writeChunks(
                tableChunk(1, "East", "A", 10),
                tableChunk(2, "East", "B", 20),
                tableChunk(3, "West", "C", 30));
        AttachmentDerivedStore store = mock(AttachmentDerivedStore.class);
        when(store.downloadChunks(record)).thenReturn(chunks);
        AttachmentContentService service = new AttachmentContentService(
                store, mock(AttachmentSearchService.class));

        AttachmentContentService.TableQueryResult result = service.tableQuery(
                record,
                null,
                List.of(new AttachmentContentService.FilterSpec("region", "eq", "East")),
                List.of("region", "name", "amount"),
                "region",
                List.of(new AttachmentContentService.AggregateSpec("amount", "sum", "total"),
                        new AttachmentContentService.AggregateSpec(null, "count", "rows")),
                null,
                null,
                10);

        assertThat(result.matchedRows()).isEqualTo(2);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).containsEntry("region", "East")
                .containsEntry("total", new java.math.BigDecimal("30"))
                .containsEntry("rows", 2L);
    }

    @Test
    void readReturnsOrdinalContinuationWhenAChunkExceedsBudget() throws Exception {
        ChatAttachmentRecord record = tableRecord();
        AttachmentChunk first = tableChunk(1, "East", "A", 10);
        first.setText(first.getText() + "\n" + "x".repeat(1_000));
        AttachmentChunk second = tableChunk(2, "West", "B", 20);
        Path chunks = writeChunks(first, second);
        AttachmentDerivedStore store = mock(AttachmentDerivedStore.class);
        when(store.downloadChunks(any())).thenReturn(chunks);
        AttachmentContentService service = new AttachmentContentService(
                store, mock(AttachmentSearchService.class));

        AttachmentContentService.ReadResult result = service.read(record, "sheet:CSV", 256);

        assertThat(result.truncated()).isTrue();
        assertThat(result.nextLocator()).isEqualTo("ordinal:2");
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).getText()).contains("[片段已截断]");
    }

    private ChatAttachmentRecord tableRecord() {
        return new ChatAttachmentRecord()
                .setFileId("file-1")
                .setUserId(1)
                .setFileName("sales.csv")
                .setFormat("csv")
                .setParseStatus(AttachmentStatus.READY);
    }

    private AttachmentChunk tableChunk(int ordinal, String region, String name, int amount) {
        return AttachmentChunk.builder()
                .id("file-1-" + ordinal)
                .fileId("file-1")
                .fileName("sales.csv")
                .kind("table")
                .locator("sheet:CSV!rows:" + (ordinal + 1) + "-" + (ordinal + 1))
                .sheet("CSV")
                .startRow(ordinal + 1)
                .endRow(ordinal + 1)
                .ordinal(ordinal)
                .text("region\tname\tamount\n" + region + "\t" + name + "\t" + amount)
                .build();
    }

    private Path writeChunks(AttachmentChunk... chunks) throws Exception {
        Path path = Files.createTempFile(tempDir, "chunks-", ".jsonl");
        Files.write(path, java.util.Arrays.stream(chunks)
                .map(JacksonUtil::toJson)
                .toList(), StandardCharsets.UTF_8);
        return path;
    }
}
