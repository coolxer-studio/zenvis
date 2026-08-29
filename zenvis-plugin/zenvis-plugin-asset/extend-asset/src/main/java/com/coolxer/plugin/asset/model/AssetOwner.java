package com.coolxer.plugin.asset.model;

public enum AssetOwner {
    ENTERPRISE("企业"),
    CUSTOMER("终端客户");

    private final String description;

    AssetOwner(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
