package com.coolxer.model.dih.vo;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG知识库视图对象
 */
@Data
@NoArgsConstructor
public class RagKnowledgeBaseVo implements Serializable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private int id;
    private String name;
    private String source;
    private List<String> tags;
    private String tagsText;
    private String description;
    private String status;
    private String statusDescription;
    private int documentCount;
    private List<String> datasetIds;
    private List<String> datasetNames;
    private String operationMessage;
    private String operationError;
    private Date operationStartedAt;
    private Date operationEndedAt;
    private Date updateTime;

    public RagKnowledgeBaseVo(RagKnowledgeBase knowledgeBase) {
        this.id = knowledgeBase.getId();
        this.name = knowledgeBase.getName();
        this.source = knowledgeBase.getSource();
        this.description = knowledgeBase.getDescription();
        this.status = knowledgeBase.getStatus() == null ? null : knowledgeBase.getStatus().name();
        this.statusDescription = knowledgeBase.getStatus() == null ? "" : knowledgeBase.getStatus().getDescription();
        this.documentCount = knowledgeBase.getDocumentCount() == null ? 0 : knowledgeBase.getDocumentCount();
        this.operationMessage = knowledgeBase.getOperationMessage();
        this.operationError = knowledgeBase.getOperationError();
        this.operationStartedAt = knowledgeBase.getOperationStartedAt();
        this.operationEndedAt = knowledgeBase.getOperationEndedAt();
        this.updateTime = knowledgeBase.getUpdateTime();
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(knowledgeBase.getTags(), new TypeReference<List<String>>() {
            });
            // 兼容历史数据：单个元素内可能逗号拼接了多个标签，拆分为独立标签
            this.tags = parsed == null ? List.of() : parsed.stream()
                    .flatMap(t -> Arrays.stream(t.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            this.tagsText = String.join(", ", this.tags);
        } catch (Exception e) {
            this.tags = List.of();
            this.tagsText = "";
        }
    }
}
