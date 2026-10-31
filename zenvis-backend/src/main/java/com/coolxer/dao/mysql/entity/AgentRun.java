package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 持久化通用智能体运行快照。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_AGENT_RUN,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_run_id", columnNames = "run_id"),
        indexes = {
                @Index(name = "idx_agent_run_chat", columnList = "chat_id,status"),
                @Index(name = "idx_agent_run_claim", columnList = "status,lease_until,create_time")
        }
)
public class AgentRun extends BaseEntity {

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "chat_id", nullable = false, length = 128)
    private String chatId;

    @Column(name = "chat_session_id")
    private Integer chatSessionId;

    @Column(name = "chat_type", nullable = false, length = 64)
    private String chatType;

    @Column(name = "agent_type", nullable = false, length = 64)
    private String agentType;

    @Column(name = "skill_ids", columnDefinition = "TEXT")
    private String skillIds;

    @Column(length = 255)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status = AgentRunStatus.QUEUED;

    @Column(columnDefinition = "TEXT")
    private String goal;

    @Column(name = "plan_json", columnDefinition = "LONGTEXT")
    private String planJson;

    @Column(name = "plan_revision", nullable = false)
    private Integer planRevision = 0;

    @Column(name = "current_step_id", length = 64)
    private String currentStepId;

    @Column(name = "context_checkpoint", columnDefinition = "LONGTEXT")
    private String contextCheckpoint;

    @Column(name = "request_json", columnDefinition = "LONGTEXT")
    private String requestJson;

    @Column(name = "decision_steps", nullable = false)
    private Integer decisionSteps = 0;

    @Column(name = "tool_calls", nullable = false)
    private Integer toolCalls = 0;

    @Column(name = "effective_millis", nullable = false)
    private Long effectiveMillis = 0L;

    @Column(name = "max_steps", nullable = false)
    private Integer maxSteps = 30;

    @Column(name = "max_duration_seconds", nullable = false)
    private Integer maxDurationSeconds = 1800;

    @Column(name = "max_tool_calls", nullable = false)
    private Integer maxToolCalls = 80;

    @Column(name = "pending_action", columnDefinition = "LONGTEXT")
    private String pendingAction;

    /**
     * 当前唯一待处理的用户交互。pendingAction 仅作为旧数据兼容字段。
     */
    @Column(name = "interaction_json", columnDefinition = "LONGTEXT")
    private String interactionJson;

    @Column(name = "last_event_sequence", nullable = false)
    private Long lastEventSequence = 0L;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "final_answer", columnDefinition = "LONGTEXT")
    private String finalAnswer;

    @Column(name = "paused_reason", length = 255)
    private String pausedReason;

    @Column(name = "lease_id", length = 64)
    private String leaseId;

    @Column(name = "lease_until")
    private Date leaseUntil;

    @Column(name = "heartbeat_time")
    private Date heartbeatTime;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "no_progress_count", nullable = false)
    private Integer noProgressCount = 0;

    @Column(name = "recovery_count", nullable = false)
    private Integer recoveryCount = 0;

    @Column(name = "truncation_count", nullable = false)
    private Integer truncationCount = 0;

    @Column(name = "truncation_step_id", length = 64)
    private String truncationStepId;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "finished_at")
    private Date finishedAt;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long lockVersion = 0L;
}
