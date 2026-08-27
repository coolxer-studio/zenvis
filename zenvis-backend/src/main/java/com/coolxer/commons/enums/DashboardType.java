package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 看板类型
 * 菜单、链接
 */
@Getter
public enum DashboardType {

    BUILT("内置看板"),
    LOW_CODE_PAGE("低代码页面看板"),
    HTML_PAGE("HTML页面看板"),
    LINK("外链看板");

    DashboardType(String description) {
        this.description = description;
    }

    private final String description;
} 