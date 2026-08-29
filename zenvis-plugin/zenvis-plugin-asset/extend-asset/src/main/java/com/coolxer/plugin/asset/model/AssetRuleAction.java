package com.coolxer.plugin.asset.model;

public enum AssetRuleAction {
    MERGE("合并"),
    MARK("打标记");

    private final String description;

    AssetRuleAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
