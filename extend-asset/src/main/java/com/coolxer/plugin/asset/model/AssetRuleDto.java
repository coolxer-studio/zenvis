package com.coolxer.plugin.asset.model;

import jakarta.validation.constraints.NotBlank;

public class AssetRuleDto {
    private Long id;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    private String description;
    private Asset asset;
    private Object conditions;
    private AssetRuleAction action;
    private AssetRuleStatus status;
    private String result;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public Object getConditions() {
        return conditions;
    }

    public void setConditions(Object conditions) {
        this.conditions = conditions;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
