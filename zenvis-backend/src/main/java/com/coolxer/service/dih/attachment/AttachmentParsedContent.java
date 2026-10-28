package com.coolxer.service.dih.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentParsedContent {
    private String status;
    private String format;
    private Integer pageCount;
    private Integer sheetCount;
    private Integer slideCount;
    private Long rowCount;
    private Long textChars;
    private Integer chunkCount;
    private Boolean truncated;
    private String warning;
    private Path chunksPath;
    @Builder.Default
    private List<String> outline = new ArrayList<>();
    @Builder.Default
    private List<AttachmentManifest.TableInfo> tables = new ArrayList<>();
    @Builder.Default
    private List<AttachmentChunk> chunks = new ArrayList<>();
}
