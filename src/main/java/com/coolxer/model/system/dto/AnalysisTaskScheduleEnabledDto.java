package com.coolxer.model.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnalysisTaskScheduleEnabledDto {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
