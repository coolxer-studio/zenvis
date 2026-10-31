package com.coolxer.model.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 Vector 单元测试结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResult {

    private String name;

    private String status;

    private String message;
}
