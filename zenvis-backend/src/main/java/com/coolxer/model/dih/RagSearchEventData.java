package com.coolxer.model.dih;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DIH 对话中的一次知识库检索快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class RagSearchEventData {

    public static final String STREAM_MARKER_PREFIX = "\u001ezenvis-rag-search:";

    private String searchId;
    private String query;
    private String mode;
    private String scopeType;
    private List<Integer> knowledgeBaseIds;
    private Integer topK;
    private String status;
    private String reason;
    private Integer hitCount;
    private Long durationMillis;
    private String startedAt;
    private String completedAt;
    private List<RagSearchResult> results;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class RagSearchResult {
        private String documentId;
        private Integer knowledgeBaseId;
        private String knowledgeBaseName;
        private String datasetName;
        private String fileName;
        private String source;
        private String text;
    }
}
