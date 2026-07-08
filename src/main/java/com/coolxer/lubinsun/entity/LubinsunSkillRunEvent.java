package com.coolxer.lubinsun.entity;

import com.coolxer.dao.mysql.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "t_lubinsun_skill_run_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lubinsun_task_seq", columnNames = {"task_id", "seq"})
        },
        indexes = {
                @Index(name = "idx_lubinsun_event_task_id", columnList = "task_id"),
                @Index(name = "idx_lubinsun_event_run_id", columnList = "run_id")
        })
public class LubinsunSkillRunEvent extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "event_type")
    private String type;

    @Column(name = "data_json", columnDefinition = "LONGTEXT")
    private String dataJson;

    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(name = "event_created_at")
    private Date eventCreatedAt;
}
