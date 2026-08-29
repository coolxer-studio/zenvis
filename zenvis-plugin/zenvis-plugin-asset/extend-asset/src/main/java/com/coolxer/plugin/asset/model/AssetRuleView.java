package com.coolxer.plugin.asset.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AssetRuleView {
    private Long id;
    private String name;
    private String description;
    private Asset asset;
    private String assetDesc;
    private Object conditions;
    private AssetRuleAction action;
    private String actionDesc;
    private AssetRuleStatus status;
    private String statusDesc;
    private String result;
    private String createTime;
    private String updateTime;

    public static AssetRuleView from(AssetRuleRecord record, ObjectMapper objectMapper) {
        AssetRuleView view = new AssetRuleView();
        view.id = record.id();
        view.name = record.name();
        view.description = record.description();
        view.asset = record.asset() == null ? null : Asset.valueOf(record.asset());
        view.assetDesc = view.asset == null ? null : view.asset.getDescription();
        view.conditions = parseConditions(record.conditions(), objectMapper);
        view.action = record.action() == null ? null : AssetRuleAction.valueOf(record.action());
        view.actionDesc = view.action == null ? null : view.action.getDescription();
        view.status = record.status() == null ? null : AssetRuleStatus.valueOf(record.status());
        view.statusDesc = view.status == null ? null : view.status.getDescription();
        view.result = record.result();
        view.createTime = record.createTime() == null ? null : record.createTime().toString();
        view.updateTime = record.updateTime() == null ? null : record.updateTime().toString();
        return view;
    }

    private static Object parseConditions(String conditions, ObjectMapper objectMapper) {
        if (conditions == null) {
            return null;
        }
        try {
            return objectMapper.readValue(conditions, Object.class);
        } catch (JsonProcessingException ignored) {
            return conditions;
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Asset getAsset() { return asset; }
    public String getAssetDesc() { return assetDesc; }
    public Object getConditions() { return conditions; }
    public AssetRuleAction getAction() { return action; }
    public String getActionDesc() { return actionDesc; }
    public AssetRuleStatus getStatus() { return status; }
    public String getStatusDesc() { return statusDesc; }
    public String getResult() { return result; }
    public String getCreateTime() { return createTime; }
    public String getUpdateTime() { return updateTime; }
}
