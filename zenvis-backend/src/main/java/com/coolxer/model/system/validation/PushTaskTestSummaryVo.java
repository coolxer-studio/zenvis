package com.coolxer.model.system.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector 单元测试汇总。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskTestSummaryVo {

    private boolean present;

    private int total;

    private int passed;

    private int failed;

    @Builder.Default
    private List<PushTaskTestCaseVo> cases = new ArrayList<>();
}
