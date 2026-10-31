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
 * Run 运行期间的主动消息、统一交互响应和附件。
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

    @Column(name = "part_id", length = 64)
    private String partId;

    @Column(name = "workflow_id", length = 64)
    private String workflowId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "interaction_id", length = 64)
    private String interactionId;

    @Column(name = "interaction_kind", length = 32)
    private String interactionKind;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachments_json", columnDefinition = "LONGTEXT")
    private String attachments;

    /**
     * 结构化交互响应，用于幂等比较和审计；普通聊天输入可为空。
     */
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentRunInputStatus status = AgentRunInputStatus.PENDING;

    @Column(name = "applied_at")
    private Date appliedAt;
}
