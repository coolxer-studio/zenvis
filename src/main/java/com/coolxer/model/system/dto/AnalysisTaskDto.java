package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    /**
     * 周期执行 Cron 表达式（Spring 6 段格式，包含秒）。为空表示仅执行一次。
     */
    private String cronExpression;

    /**
     * MCP 工具审批模式，创建和编辑时必须明确设置。
     */
    @NotNull(message = "请选择MCP审批模式")
    private AnalysisTaskApprovalMode approvalMode;

    /**
     * 任务要额外加载的已启用 Skill ID。
     */
    private List<String> skillIds = new ArrayList<>();
}
