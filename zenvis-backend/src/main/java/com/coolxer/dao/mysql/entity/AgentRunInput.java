package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Run 运行期间由用户追加的 steer 输入。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_AGENT_RUN_INPUT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_run_input_client_message",
                columnNames = {"run_id", "client_message_id"}),
        indexes = @Index(
                name = "idx_agent_run_input_pending",
                columnList = "run_id,status,create_time")
)
public class AgentRunInput extends BaseEntity {

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "client_message_id", nullable = false, length = 128)
    private String clientMessageId;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachments_json", columnDefinition = "LONGTEXT")
    private String attachments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentRunInputStatus status = AgentRunInputStatus.PENDING;

    @Column(name = "applied_at")
    private Date appliedAt;
}
