package com.coolxer.model.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一校验诊断项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationDiagnostic {

    private String code;

    private String severity;

    private String source;

    private String path;

    private String message;

    private String repairHint;
}
