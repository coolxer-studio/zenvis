package com.coolxer.model.validation;

import lombok.Data;

/**
 * 配置隔离校验请求。
 */
@Data
public class ValidationRequest {

    /**
     * 完整 Vector 配置文本。
     */
    private String config;

    /**
     * 可选的配置格式：yaml、json 或 toml。缺省时自动识别。
     */
    private String format;

    /**
     * 是否要求配置声明 Vector 单元测试。
     */
    private boolean testsRequired;
}
