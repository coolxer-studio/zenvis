package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

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
    @Column
    private AnalysisTaskStatus status = AnalysisTaskStatus.PENDING;

    /**
     * 队列优先级，数值越大越先执行
     */
    @Column
    private Integer priority = 0;

    /**
     * 计划执行时间，为空表示立即进入队列
     */
    @Column(name = "scheduled_time")
    private Date scheduledTime;

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

    public void updateFromDto(AnalysisTaskDto analysisTaskDto) {
        if (analysisTaskDto.getName() != null) {
            this.name = analysisTaskDto.getName();
        }
        if (analysisTaskDto.getDescription() != null) {
            this.description = analysisTaskDto.getDescription();
        }
        if (analysisTaskDto.getModel() != null) {
            this.model = analysisTaskDto.getModel();
        }
        if (analysisTaskDto.getPrompt() != null) {
            this.prompt = analysisTaskDto.getPrompt();
        }
        if (analysisTaskDto.getPriority() != null) {
            this.priority = analysisTaskDto.getPriority();
        }
        if (analysisTaskDto.getScheduledTime() != null) {
            this.scheduledTime = analysisTaskDto.getScheduledTime();
        }
    }
}
