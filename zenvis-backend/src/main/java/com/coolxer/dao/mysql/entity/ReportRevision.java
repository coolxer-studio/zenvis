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
        name = MysqlFinalTableName.T_AI_REPORT_REVISION,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_revision", columnNames = {"document_id", "revision"}),
        indexes = @Index(
                name = "idx_report_revision_document", columnList = "document_id")
)
public class ReportRevision extends BaseEntity {

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(nullable = false)
    private Long revision;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "LONGTEXT")
    private String outline;

    @Column(name = "source_refs", columnDefinition = "LONGTEXT")
    private String sourceRefs;
}
