package com.coolxer.model.system.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vector 单元测试用例结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskTestCaseVo {

    private String name;

    private String status;

    private String message;
}
