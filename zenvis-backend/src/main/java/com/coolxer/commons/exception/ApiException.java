package com.coolxer.commons.exception;

import com.coolxer.commons.enums.ResultCodeEnum;

/**
 * 异常处理基类
 */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = -5431786577589162921L;

    private int code;
    private String description;

    public ApiException(int code) {
        this.code = code;
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
        this.description = message;
    }

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
