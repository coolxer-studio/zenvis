package com.coolxer.plugin.asset.model;

import com.coolxer.plugin.asset.api.PageQuery;

public class AssetRuleSearchQuery extends PageQuery {
    private Asset asset;
    private AssetRuleAction action;
    private AssetRuleStatus status;

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public AssetRuleAction getAction() {
        return action;
    }

    public void setAction(AssetRuleAction action) {
        this.action = action;
    }

    public AssetRuleStatus getStatus() {
        return status;
    }

    public void setStatus(AssetRuleStatus status) {
        this.status = status;
    }
}
