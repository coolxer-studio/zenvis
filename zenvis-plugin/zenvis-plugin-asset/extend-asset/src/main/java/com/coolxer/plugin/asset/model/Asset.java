package com.coolxer.plugin.asset.model;

public enum Asset {
    HOST("服务器设备"),
    MOBILE("移动端设备"),
    PC("PC端设备"),
    IOT("IOT设备"),
    PROBE("探针资产"),
    APP("应用资产"),
    SERVICE("服务资产"),
    API("API资产"),
    LOG("日志"),
    FILE("文件");

    private final String description;

    Asset(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
