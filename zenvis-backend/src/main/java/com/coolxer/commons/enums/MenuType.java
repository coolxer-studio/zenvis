package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 菜单类型
 * 低代码应用、配置文件、内置应用。
 */
@Getter
public enum MenuType {

    BUILT_APP("内置应用", ""),
    EXTERNAL_APP("外联应用", "external-app"),
    LOW_CODE_APP("低代码应用", "low-code-app"),
    LOW_CODE_PAGE("低代码页面", "low-code-page"),
    HTML_PAGE("HTML页面", "html-page"),
    POLICY_CONFIG("策略配置", "policy-config");

    MenuType(String description, String route) {
        this.description = description;
        this.route = route;
    }

    private final String description;
    private final String route;
} 