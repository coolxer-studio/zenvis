package com.coolxer.model.dih.dto;

import lombok.Data;

@Data
public class ReportArchiveDto {
    private String documentId;
    private Long baseRevision;
    private String name;
}
