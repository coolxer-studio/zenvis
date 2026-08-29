package com.coolxer.plugin.asset.model;

public enum AssetSource {
    MANUAL("手动录入"),
    AGENT("agent监测"),
    PROBE("主动探测"),
    THIRD_PARTY("三方平台对接"),
    OTHER("其他");

    private final String description;

    AssetSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
