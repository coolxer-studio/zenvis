package com.coolxer.model.dih.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报表对话动作。完整文档和选区片段使用不同的持久化协议。
 */
@Data
public class ReportActionDto {

    public static final String FULL_GENERATE = "full_generate";
    public static final String FULL_REWRITE = "full_rewrite";
    public static final String SELECTION_REWRITE = "selection_rewrite";

    private String type;
    private String documentId;
    private Long baseRevision;
    private String selectionId;
    private String selectionHash;
    private List<Map<String, Object>> sourceRefs;

    public boolean isSelectionRewrite() {
        return SELECTION_REWRITE.equals(type);
    }

    public boolean isFullDocumentAction() {
        return FULL_GENERATE.equals(type) || FULL_REWRITE.equals(type);
    }
}
