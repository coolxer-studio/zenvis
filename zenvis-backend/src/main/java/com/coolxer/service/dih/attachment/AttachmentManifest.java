package com.coolxer.service.dih.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 附件解析产物清单，写入 MinIO 以便多实例共享。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentManifest {
    private String fileId;
    private String fileName;
    private String contentType;
    private String kind;
    private String format;
    private String status;
    private Integer pageCount;
    private Integer sheetCount;
    private Integer slideCount;
    private Long rowCount;
    private Long textChars;
    private Integer chunkCount;
    private Boolean truncated;
    private String warning;
    @Builder.Default
    private List<String> outline = new ArrayList<>();
    @Builder.Default
    private List<TableInfo> tables = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        private String sheet;
        private String name;
        private List<String> columns;
        private List<String> fieldTypes;
        private List<List<String>> sampleRows;
        private Integer startRow;
        private Integer endRow;
        private Long rowCount;
    }
}
