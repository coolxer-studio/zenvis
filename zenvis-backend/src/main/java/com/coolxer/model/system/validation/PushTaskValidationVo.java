package com.coolxer.model.system.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据推送配置校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskValidationVo {

    private boolean passed;

    private boolean blocked;

    private String format;

    private String candidateDigest;

    private String metaDigest;

    private String validationDigest;

    private String vectorVersion;

    @Builder.Default
    private List<PushTaskDiagnosticVo> diagnostics = new ArrayList<>();

    @Builder.Default
    private PushTaskTestSummaryVo tests = new PushTaskTestSummaryVo();

    private String normalizedConfig;
}
