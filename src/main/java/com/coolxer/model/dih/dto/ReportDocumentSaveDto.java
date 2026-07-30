package com.coolxer.model.dih.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReportDocumentSaveDto {
    private String documentId;
    private Long baseRevision;
    private String title;
    private String format;
    private String content;
    private List<Map<String, Object>> outline;
    private List<Map<String, Object>> sourceRefs;
}
