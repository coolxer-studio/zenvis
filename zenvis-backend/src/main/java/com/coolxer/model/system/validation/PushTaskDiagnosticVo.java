package com.coolxer.model.system.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一配置或运行诊断项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskDiagnosticVo {

    private String code;

    private String severity;

    private String source;

    private String path;

    private String message;

    private String repairHint;
}
