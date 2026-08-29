package com.coolxer.commons.exception;

import com.coolxer.commons.enums.ResultCodeEnum;

/**
 * API异常类
 * <p>
 * 用于封装API调用过程中的业务异常信息
 * </p>
 */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = -5431786577589162921L;

    private int code;
    private String description;

    /**
     * 构造函数
     * @param code 错误码
     */
    public ApiException(int code) {
        this.code = code;
    }

    /**
     * 构造函数
     * @param code 错误码
     * @param message 错误信息
     */
    public ApiException(int code, String message) {
        super(message);
        this.code = code;
        this.description = message;
    }

    /**
     * 构造函数
     * @param resultCodeEnum 结果码枚举
     */
    public ApiException(ResultCodeEnum resultCodeEnum) {
        this(resultCodeEnum.getCode(), resultCodeEnum.getDescription());
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}