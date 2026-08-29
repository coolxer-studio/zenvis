package com.coolxer.plugin.asset.model;

public enum AssetRiskLevel {
    NONE("无风险"),
    LOW("低风险"),
    MEDIUM("中风险"),
    HIGH("高风险"),
    EXTREME("极高风险");

    private final String description;

    AssetRiskLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
