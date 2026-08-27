package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 消息类型枚举
 */
@Getter
public enum MessageType {
    /**
     * 文本类型
     */
    TEXT("text", "文本消息"),
    
    /**
     * 图表类型
     */
    CHART("chart", "图表消息"),
    
    /**
     * 代码类型
     */
    CODE("code", "代码消息"),
    
    /**
     * 表格类型
     */
    TABLE("table", "表格消息"),
    
    /**
     * 图片类型
     */
    IMAGE("image", "图片消息");
    
    private final String code;
    private final String description;
    
    MessageType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * 根据code获取枚举
     */
    public static MessageType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MessageType type : MessageType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
