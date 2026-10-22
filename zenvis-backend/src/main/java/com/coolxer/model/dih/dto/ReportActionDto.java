package com.coolxer.model.dih.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("documentId")
    private String documentId;
    @JsonAlias("baseRevision")
    private Long baseRevision;
    @JsonAlias("selectionId")
    private String selectionId;
    @JsonAlias("selectionHash")
    private String selectionHash;
    @JsonAlias("sourceRefs")
    private List<Map<String, Object>> sourceRefs;

    public boolean isSelectionRewrite() {
        return SELECTION_REWRITE.equals(type);
    }

    public boolean isFullDocumentAction() {
        return FULL_GENERATE.equals(type) || FULL_REWRITE.equals(type);
    }
}
