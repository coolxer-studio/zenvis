package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI分析周期任务配置。配置只负责按 Cron 创建一次性 AnalysisTask。
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_AI_ANALYSIS_TASK_SCHEDULE,
        indexes = @Index(name = "idx_analysis_task_schedule_due",
                columnList = "enabled,next_fire_time"))
public class AnalysisTaskSchedule extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String model;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private Integer priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 16)
    private AnalysisTaskApprovalMode approvalMode = AnalysisTaskApprovalMode.MANUAL;

    @Column(name = "cron_expression", nullable = false, length = 128)
    private String cronExpression;

    @Column(nullable = false)
    private Boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = MysqlFinalTableName.T_AI_ANALYSIS_TASK_SCHEDULE_SKILL,
            joinColumns = @JoinColumn(name = "schedule_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_analysis_task_schedule_skill",
                    columnNames = {"schedule_id", "skill_id"}
            ))
    @Column(name = "skill_id", nullable = false, length = 128)
    private Set<String> skillIds = new LinkedHashSet<>();

    @Column(name = "next_fire_time")
    private Date nextFireTime;

    @Column(name = "last_fire_time")
    private Date lastFireTime;

    @Column(name = "generated_count", nullable = false)
    private Integer generatedCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long lockVersion = 0L;

    public void updateFromDto(AnalysisTaskScheduleDto dto) {
        this.name = dto.getName();
        this.description = dto.getDescription();
        this.model = dto.getModel();
        this.prompt = dto.getPrompt();
        this.priority = dto.getPriority() == null ? 0 : dto.getPriority();
        this.approvalMode = dto.getApprovalMode();
        this.cronExpression = dto.getCronExpression();
        this.enabled = dto.getEnabled() == null || dto.getEnabled();
        this.skillIds = dto.getSkillIds() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(dto.getSkillIds());
    }
}
