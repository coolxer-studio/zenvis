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

/**
 * Agent Run 只追加事件。sequence 在单个 Run 内单调递增。
 */
@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(
        name = MysqlFinalTableName.T_AI_AGENT_RUN_EVENT,
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_run_event_sequence",
                columnNames = {"run_id", "event_sequence"}),
        indexes = @Index(
                name = "idx_agent_run_event_replay",
                columnList = "run_id,event_sequence")
)
public class AgentRunEvent extends BaseEntity {

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "event_sequence", nullable = false)
    private Long sequence;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "step_id", length = 64)
    private String stepId;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payload;
}
