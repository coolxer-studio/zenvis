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
        name = MysqlFinalTableName.T_AI_REPORT_ARTIFACT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_artifact_id", columnNames = "artifact_id"),
        indexes = {
                @Index(name = "idx_report_artifact_session", columnList = "chat_session_id"),
                @Index(name = "idx_report_artifact_document", columnList = "document_id")
        }
)
public class ReportArtifact extends BaseEntity {

    @Column(name = "artifact_id", nullable = false, length = 64)
    private String artifactId;

    @Column(name = "chat_session_id", nullable = false)
    private Integer chatSessionId;

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(nullable = false)
    private Long revision;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(columnDefinition = "LONGTEXT", nullable = false, updatable = false)
    private String content;

    @Column(columnDefinition = "LONGTEXT", updatable = false)
    private String outline;

    @Column(name = "source_refs", columnDefinition = "LONGTEXT", updatable = false)
    private String sourceRefs;
}
