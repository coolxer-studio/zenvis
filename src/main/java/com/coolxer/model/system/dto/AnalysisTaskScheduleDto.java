package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI分析周期任务配置传输对象。
 */
@Data
public class AnalysisTaskScheduleDto {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    private String model;

    @NotBlank(message = "提示词不能为空")
    private String prompt;

    private Integer priority;

    @NotNull(message = "请选择MCP审批模式")
    private AnalysisTaskApprovalMode approvalMode;

    @NotBlank(message = "Cron表达式不能为空")
    private String cronExpression;

    private Boolean enabled = true;

    private List<String> skillIds = new ArrayList<>();
}
