package com.coolxer.plugin.asset.model;

public enum AssetRuleStatus {
    INACTIVE("未激活"),
    ACTIVE("激活"),
    EXPIRED("失效"),
    UNAVAILABLE("不可用");

    private final String description;

    AssetRuleStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
