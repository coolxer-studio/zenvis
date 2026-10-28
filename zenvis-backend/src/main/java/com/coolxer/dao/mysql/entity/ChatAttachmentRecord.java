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

import java.util.Date;

/**
 * 聊天附件元信息、异步解析状态与派生对象位置。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_CHAT_ATTACHMENT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_attachment_file_id", columnNames = "file_id"),
        indexes = {
                @Index(name = "idx_chat_attachment_user_chat",
                        columnList = "user_id,chat_id,create_time"),
                @Index(name = "idx_chat_attachment_status_heartbeat",
                        columnList = "parse_status,heartbeat_at")
        }
)
public class ChatAttachmentRecord extends BaseEntity {

    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "chat_id", length = 128)
    private String chatId;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "kind", length = 16)
    private String kind;

    @Column(name = "format", length = 32)
    private String format;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "parse_status", nullable = false, length = 24)
    private String parseStatus;

    @Column(name = "parse_progress")
    private Integer parseProgress;

    @Column(name = "parse_message", length = 1000)
    private String parseMessage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "heartbeat_at")
    private Date heartbeatAt;

    @Column(name = "manifest_key", length = 512)
    private String manifestKey;

    @Column(name = "chunks_key", length = 512)
    private String chunksKey;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "sheet_count")
    private Integer sheetCount;

    @Column(name = "slide_count")
    private Integer slideCount;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "text_chars")
    private Long textChars;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "truncated")
    private Boolean truncated = false;

    @Column(name = "warning", length = 1000)
    private String warning;
}
