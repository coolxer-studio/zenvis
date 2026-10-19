package com.coolxer.plugin.infra.monitor.model;

public record ApiResponse<T>(int status, String msg, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "请求成功", data);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(1, message, null);
    }
}
