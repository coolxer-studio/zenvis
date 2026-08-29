package com.coolxer.plugin.asset.model;

public enum AssetType {
    BUSINESS("业务类"),
    SUPPORT("支撑类");

    private final String description;

    AssetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
