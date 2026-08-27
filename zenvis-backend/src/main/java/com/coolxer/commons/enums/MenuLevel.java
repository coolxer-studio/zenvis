package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 菜单类型
 * 低代码应用、配置文件、内置应用。
 */
@Getter
public enum MenuLevel {

    LEVEL_1("一级菜单"),
    LEVEL_2("二级菜单");

    MenuLevel(String description) {
        this.description = description;
    }

    private final String description;

} 