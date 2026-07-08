package com.coolxer.lubinsun.entity;

import com.coolxer.dao.mysql.entity.BaseEntity;
import com.coolxer.lubinsun.model.LubinsunTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "t_lubinsun_skill_run_task", indexes = {
        @Index(name = "idx_lubinsun_task_run_id", columnList = "run_id"),
        @Index(name = "idx_lubinsun_task_status", columnList = "status"),
        @Index(name = "idx_lubinsun_task_skill", columnList = "skill")
})
public class LubinsunSkillRunTask extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "skill", nullable = false)
    private String skill;

    @Column(name = "agent")
    private String agent;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "platform_title")
    private String platformTitle;

    @Column(name = "task_type")
    private String taskType;

    @Column(name = "target_ip")
    private String ip;

    @Column(name = "raw_log", columnDefinition = "LONGTEXT")
    private String rawLog;

    @Column(name = "input_json", columnDefinition = "LONGTEXT")
    private String inputJson;

    @Column(name = "metadata_json", columnDefinition = "LONGTEXT")
    private String metadataJson;

    @Column(name = "event_logs_json", columnDefinition = "LONGTEXT")
    private String eventLogsJson;

    @Column(name = "alarm_logs_json", columnDefinition = "LONGTEXT")
    private String alarmLogsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LubinsunTaskStatus status = LubinsunTaskStatus.DRAFT;

    @Column(name = "platform_status")
    private String platformStatus;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "platform_task_id")
    private String platformTaskId;

    @Column(name = "response_title")
    private String responseTitle;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "key_points_json", columnDefinition = "LONGTEXT")
    private String keyPointsJson;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "public_result_json", columnDefinition = "LONGTEXT")
    private String publicResultJson;

    @Column(name = "pending_permissions_json", columnDefinition = "LONGTEXT")
    private String pendingPermissionsJson;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "create_response_json", columnDefinition = "LONGTEXT")
    private String createResponseJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "last_seq")
    private Long lastSeq = 0L;

    @Column(name = "executed_at")
    private Date executedAt;

    @Column(name = "last_polled_at")
    private Date lastPolledAt;

    @Column(name = "finished_at")
    private Date finishedAt;
}
