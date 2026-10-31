package com.coolxer.service;

import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.ValidationRequest;

/**
 * Vector 配置隔离校验服务。
 */
public interface TaskValidationService {

    /**
     * 在临时目录中使用生产同版本 Vector 校验配置并执行单元测试。
     *
     * @param request 校验请求
     * @return 统一校验结果
     */
    ConfigValidationResult validate(ValidationRequest request);
}
