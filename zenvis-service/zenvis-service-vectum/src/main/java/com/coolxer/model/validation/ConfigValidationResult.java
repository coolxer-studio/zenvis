package com.coolxer.model.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector 原生配置校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigValidationResult {

    private boolean passed;

    private boolean blocked;

    private String format;

    private String candidateDigest;

    private String vectorVersion;

    @Builder.Default
    private List<ValidationDiagnostic> diagnostics = new ArrayList<>();

    @Builder.Default
    private TestSummary tests = new TestSummary();

    private String normalizedConfig;
}
