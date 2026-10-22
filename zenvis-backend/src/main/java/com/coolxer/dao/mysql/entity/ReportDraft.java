package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 报表章节草稿。每个 Run 的章节写入立即落库，完成时再生成正式修订。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_REPORT_DRAFT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_draft_document",
                columnNames = "document_id"),
        indexes = @Index(
                name = "idx_report_draft_run",
                columnList = "run_id,chat_session_id")
)
public class ReportDraft extends BaseEntity {

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(name = "chat_session_id", nullable = false)
    private Integer chatSessionId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 16)
    private String format = "markdown";

    @Column(name = "base_revision")
    private Long baseRevision;

    @Column(nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "sections_json", columnDefinition = "LONGTEXT")
    private String sections;

    @Column(name = "source_refs", columnDefinition = "LONGTEXT")
    private String sourceRefs;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long lockVersion = 0L;
}
