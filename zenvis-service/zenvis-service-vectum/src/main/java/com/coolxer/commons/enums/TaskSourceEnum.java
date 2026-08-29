package com.coolxer.commons.enums;

public enum TaskSourceEnum {
    SYSTEM("系统内置"),
    MCP("MCP"),
    API("接口添加"),
    WEB("Web页面添加");

    private final String description;

    TaskSourceEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static TaskSourceEnum fromString(String value) {
        if (value == null) {
            return SYSTEM;
        }
        for (TaskSourceEnum source : values()) {
            if (source.name().equalsIgnoreCase(value) || source.description.equals(value)) {
                return source;
            }
        }
        return SYSTEM;
    }
}