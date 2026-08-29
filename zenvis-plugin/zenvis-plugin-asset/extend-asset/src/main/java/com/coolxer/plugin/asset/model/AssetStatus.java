package com.coolxer.plugin.asset.model;

public enum AssetStatus {
    ONLINE("在网"),
    DISABLED("停用"),
    OFFLINE("下线"),
    DELETED("删除");

    private final String description;

    AssetStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
