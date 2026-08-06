package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI分析任务
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_AI_ANALYSIS_TASK)
public class AnalysisTask extends BaseEntity {

    /**
     * 任务名称
     */
    @Column
    private String name;

    /**
     * 任务描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 模型名称，为空时使用系统默认模型
     */
    @Column
    private String model;

    /**
     * 分析提示词
     */
    @Column(columnDefinition = "TEXT")
    private String prompt;

    /**
     * AI返回结果
     */
    @Column(columnDefinition = "LONGTEXT")
    private String result;

    /**
     * 异常信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 任务状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(32)")
    private AnalysisTaskStatus status = AnalysisTaskStatus.PENDING;

    /**
     * 队列优先级，数值越大越先执行
     */
    @Column
    private Integer priority = 0;

    /**
     * MCP 工具审批模式。历史数据为空时按 MANUAL 处理。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", length = 16)
    private AnalysisTaskApprovalMode approvalMode = AnalysisTaskApprovalMode.MANUAL;

    /**
     * 当前一次执行的唯一标识。
     */
    @Column(name = "execution_id", length = 64)
    private String executionId;

    /**
     * 最近一次已结束执行的唯一标识，供周期任务查询上一轮审计记录。
     */
    @Column(name = "last_execution_id", length = 64)
    private String lastExecutionId;

    /**
     * 最近一次已结束执行的状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_status", length = 32)
    private AnalysisTaskStatus lastRunStatus;

    /**
     * 任务明确选择的 Skill。
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = MysqlFinalTableName.T_AI_ANALYSIS_TASK_SKILL,
            joinColumns = @JoinColumn(name = "task_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_analysis_task_skill",
                    columnNames = {"task_id", "skill_id"}
            ))
    @Column(name = "skill_id", nullable = false, length = 128)
    private Set<String> skillIds = new LinkedHashSet<>();

    /**
     * 计划执行时间，为空表示立即进入队列
     */
    @Column(name = "scheduled_time")
    private Date scheduledTime;

    /**
     * Spring Cron 表达式（6 段，包含秒）。为空表示仅执行一次。
     */
    @Column(name = "cron_expression", length = 128)
    private String cronExpression;

    /**
     * 开始执行时间
     */
    @Column(name = "start_time")
    private Date startTime;

    /**
     * 结束执行时间
     */
    @Column(name = "finish_time")
    private Date finishTime;

    /**
     * 执行次数
     */
    @Column(name = "run_count")
    private Integer runCount = 0;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long lockVersion = 0L;

    public void updateFromDto(AnalysisTaskDto analysisTaskDto) {
        this.name = analysisTaskDto.getName();
        this.description = analysisTaskDto.getDescription();
        this.model = analysisTaskDto.getModel();
        this.prompt = analysisTaskDto.getPrompt();
        if (analysisTaskDto.getPriority() != null) {
            this.priority = analysisTaskDto.getPriority();
        }
        this.scheduledTime = analysisTaskDto.getScheduledTime();
        this.cronExpression = analysisTaskDto.getCronExpression();
        this.approvalMode = analysisTaskDto.getApprovalMode();
        this.skillIds = analysisTaskDto.getSkillIds() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(analysisTaskDto.getSkillIds());
    }
}
