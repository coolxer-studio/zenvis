package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_REPORT_DOCUMENT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_document_id", columnNames = "document_id"),
        indexes = @Index(
                name = "idx_report_document_session", columnList = "chat_session_id")
)
public class ReportDocument extends BaseEntity {

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(name = "chat_session_id", nullable = false)
    private Integer chatSessionId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "current_revision", nullable = false)
    private Long currentRevision;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "LONGTEXT")
    private String outline;

    @Column(name = "source_refs", columnDefinition = "LONGTEXT")
    private String sourceRefs;
}
