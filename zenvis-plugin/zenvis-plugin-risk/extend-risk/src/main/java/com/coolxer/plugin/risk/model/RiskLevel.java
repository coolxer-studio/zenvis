package com.coolxer.plugin.risk.model;

public enum RiskLevel {
    HIGH("high", "高风险", 1),
    MEDIUM("medium", "中风险", 2),
    LOW("low", "低风险", 3);

    private final String code;
    private final String description;
    private final int sort;

    RiskLevel(String code, String description, int sort) {
        this.code = code;
        this.description = description;
        this.sort = sort;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getSort() {
        return sort;
    }
}
