package com.coolxer.service.dih.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可独立检索、回查的附件片段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentChunk {
    private String id;
    private String fileId;
    private String fileName;
    private String kind;
    private String locator;
    private String title;
    private String text;
    private Integer ordinal;
    private Integer page;
    private Integer slide;
    private String section;
    private String sheet;
    private Integer startRow;
    private Integer endRow;
    private Integer startLine;
    private Integer endLine;
    private List<String> columns;
}
