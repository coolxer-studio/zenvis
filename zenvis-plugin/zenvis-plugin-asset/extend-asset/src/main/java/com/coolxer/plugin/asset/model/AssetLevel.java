package com.coolxer.plugin.asset.model;

public enum AssetLevel {
    AUXILIARY("辅助资产"),
    GENERAL("一般资产"),
    MINOR("次要资产"),
    IMPORTANT("重要资产"),
    CRITICAL("关键资产");

    private final String description;

    AssetLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
