package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * RAG检索审计日志
 * <p>记录每次检索的请求参数与命中结果，用于知识库检索效果审计与排障。</p>
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_RAG_RETRIEVAL_LOG)
public class RagRetrievalLog extends BaseEntity {

    /**
     * 检索请求类型
     */
    public static final String REQUEST_TYPE_MANUAL = "MANUAL";
    public static final String REQUEST_TYPE_CHAT = "CHAT";

    /**
     * 检索状态
     */
    public static final String STATUS_OK = "ok";
    public static final String STATUS_NO_MATCH = "no_match";
    public static final String STATUS_EMPTY_QUERY = "empty_query";
    public static final String STATUS_EMBEDDING_DISABLED = "embedding_disabled";
    public static final String STATUS_RETRIEVAL_ERROR = "retrieval_error";

    /**
     * 请求类型：MANUAL=管理端手动检索，CHAT=问答触发的检索
     */
    @Column(name = "request_type", nullable = false, length = 32)
    private String requestType;

    /**
     * 检索请求文本
     */
    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    /**
     * TopK
     */
    @Column(name = "top_k")
    private Integer topK;

    /**
     * 来源筛选
     */
    @Column(name = "source")
    private String source;

    /**
     * 关联知识库ID（可选，知识库内定向检索时记录）
     */
    @Column(name = "knowledge_base_id")
    private Integer knowledgeBaseId;

    /**
     * 命中文档数量
     */
    @Column(name = "document_count")
    private Integer documentCount = 0;

    /**
     * 检索状态：ok/no_match/empty_query/embedding_disabled/retrieval_error
     */
    @Column(name = "status", length = 32)
    private String status;

    /**
     * 耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 命中结果摘要（JSON数组，包含文档ID、来源、知识库、文件名、内容摘要）
     */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    /**
     * 错误信息
     */
    @Column(name = "error_message")
    private String errorMessage;
}
