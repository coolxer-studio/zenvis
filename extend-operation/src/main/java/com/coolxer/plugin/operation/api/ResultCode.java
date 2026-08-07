package com.coolxer.plugin.operation.api;

public enum ResultCode {
    SUCCESS(0, "请求成功"),
    UNKNOWN_ERROR(-1, "未知错误"),
    INNER_ERROR(1, "请求失败");

    private final int code;
    private final String description;
    ResultCode(int code, String description) { this.code = code; this.description = description; }
    public int getCode() { return code; }
    public String getDescription() { return description; }
}
