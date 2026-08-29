package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 接口结果返回码枚举类
 * <p>
 * 定义API接口的返回码和描述信息
 * </p>
 */
@Getter
public enum ResultCodeEnum {

    SUCCESS(0, "请求成功"),

    UNKNOWN_ERROR(-1, "未知错误"),

    INNER_ERROR(101, "请求失败"),

    TASK_NAME_NOT_NULL(102, "任务名称不能为空"),

    TASK_CONFIG_NOT_NULL(103, "任务配置不能为空"),

    TASK_NOT_FOUND(104, "任务不存在"),

    TASK_OPERATION_FAILED(105, "任务操作失败");

    private final int code;
    private final String description;

    /**
     * 构造函数
     * @param code 错误码
     * @param description 描述信息
     */
    ResultCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}