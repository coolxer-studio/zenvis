package com.coolxer.plugin.controller;

/**
 * 接口结果返回码枚举类
 *
 * @author hunter
 */
public enum ResultCodeEnum {

    /**
     * 成功
     */
    SUCCESS(0, "请求成功"),

    /**
     * 未知错误
     */
    UNKNOWN_ERROR(-1, "未知错误"),

    /**
     * 服务器内部错误
     */
    INNER_ERROR(1, "请求失败"),

    /**
     * 您没有权限进行此操作
     */
    NO_AUTHORITY(100, "您没有权限进行此操作！");


    private final int code;
    private final String description;

    ResultCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }


}
