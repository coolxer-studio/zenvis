package com.coolxer.plugin.lab.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ResponseWrap<T> {

    private final int status;
    private final String msg;
    private final T data;

    private ResponseWrap(int status, String msg, T data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ResponseWrap<T> success(T data) {
        return new ResponseWrap<>(0, "请求成功", data);
    }

    public static <T> ResponseWrap<T> success(String msg, T data) {
        return new ResponseWrap<>(0, msg, data);
    }

    public static ResponseWrap<Map<String, Object>> fail(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        return new ResponseWrap<>(status, message, body);
    }

    public int getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
