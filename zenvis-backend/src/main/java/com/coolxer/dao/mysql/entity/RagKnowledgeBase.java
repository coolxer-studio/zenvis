package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Check;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * RAG知识库表
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_RAG_KNOWLEDGE_BASE)
@Check(name = "t_rag_knowledge_base_status_chk", constraints = RagKnowledgeBase.STATUS_CHECK_CONSTRAINT)
public class RagKnowledgeBase extends BaseEntity {

    public static final String STATUS_CHECK_CONSTRAINT = """
            status IN (
                'UNBUILD',
                'BUILDING',
                'BUILT',
                'BUILD_FAILED'
            )
            """;

    /**
     * 知识库名称
     */
    @Column(nullable = false)
    private String name;

    /**
     * 来源
     */
    @Column
    private String source;

    /**
     * 标签(JSON数组字符串)
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /**
     * 描述
     */
    @Column
    private String description;

    /**
     * 构建状态
     */
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(32)")
    @Enumerated(EnumType.STRING)
    private RagKnowledgeBaseStatus status = RagKnowledgeBaseStatus.UNBUILD;

    /**
     * 已向量化的文档分块数量
     */
    @Column(name = "document_count")
    private Integer documentCount = 0;

    /**
     * 最近一次构建操作摘要
     */
    @Column(name = "operation_message")
    private String operationMessage;

    /**
     * 最近一次构建操作错误
     */
    @Column(name = "operation_error", columnDefinition = "TEXT")
    private String operationError;

    /**
     * 最近一次构建开始时间
     */
    @Column(name = "operation_started_at")
    private Date operationStartedAt;

    /**
     * 最近一次构建结束时间
     */
    @Column(name = "operation_ended_at")
    private Date operationEndedAt;

    /**
     * 托管该知识库的插件包名
     */
    @Column(name = "plugin_package")
    private String pluginPackage;

    /**
     * 插件内稳定的知识库编码
     */
    @Column(name = "plugin_code")
    private String pluginCode;

    /**
     * 最近一次成功构建对应的输入指纹
     */
    @Column(name = "build_fingerprint")
    private String buildFingerprint;
}
