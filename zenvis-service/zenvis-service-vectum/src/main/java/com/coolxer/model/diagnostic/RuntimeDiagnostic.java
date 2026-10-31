package com.coolxer.model.diagnostic;

import com.coolxer.model.validation.ValidationDiagnostic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前运行轮次的结构化诊断结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeDiagnostic {

    private String verdict;

    private String runId;

    private String runStartedAt;

    private String taskStatus;

    private String failureStage;

    private boolean repairable;

    @Builder.Default
    private List<ValidationDiagnostic> diagnostics = new ArrayList<>();

    private String logExcerpt;
}
