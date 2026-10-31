package com.coolxer.model.system.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前运行轮次诊断结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskDiagnosticResultVo {

    private String verdict;

    private String runId;

    private String runStartedAt;

    private String taskStatus;

    private String failureStage;

    private boolean repairable;

    @Builder.Default
    private List<PushTaskDiagnosticVo> diagnostics = new ArrayList<>();

    private String logExcerpt;
}
