package com.coolxer.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 * AI分析任务传输对象
 */
@Data
public class AnalysisTaskDto {

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 模型名称，为空或 auto 时使用系统默认模型
     */
    private String model;

    /**
     * 分析提示词
     */
    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /**
     * 队列优先级，数值越大越先执行
     */
    private Integer priority;

    /**
     * 计划执行时间，为空表示立即进入队列
     */
    private Date scheduledTime;
}
