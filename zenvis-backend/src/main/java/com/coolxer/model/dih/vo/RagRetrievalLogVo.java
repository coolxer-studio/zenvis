package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.RagRetrievalLog;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * RAG检索审计日志VO
 */
@Data
@NoArgsConstructor
public class RagRetrievalLogVo {

    private Integer id;
    private String requestType;
    private String queryText;
    private Integer topK;
    private String source;
    private Integer knowledgeBaseId;
    private Integer documentCount;
    private String status;
    private Long durationMs;
    private String resultJson;
    private String errorMessage;
    private Date createTime;

    public RagRetrievalLogVo(RagRetrievalLog log) {
        if (log == null) {
            return;
        }
        this.id = log.getId();
        this.requestType = log.getRequestType();
        this.queryText = log.getQueryText();
        this.topK = log.getTopK();
        this.source = log.getSource();
        this.knowledgeBaseId = log.getKnowledgeBaseId();
        this.documentCount = log.getDocumentCount();
        this.status = log.getStatus();
        this.durationMs = log.getDurationMs();
        this.resultJson = log.getResultJson();
        this.errorMessage = log.getErrorMessage();
        this.createTime = log.getCreateTime();
    }
}
