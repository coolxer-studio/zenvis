package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * 插件状态类型
 */
@Getter
public enum PluginStatusType {

    UN_INSTALL("未加载"),
    INSTALLING("安装中"),
    INSTALLED("已加载"),
    INSTALL_FAILED("安装失败"),
    UNINSTALLING("卸载中"),
    UNINSTALL_FAILED("卸载失败");

    PluginStatusType(String description) {
        this.description = description;
    }

    private final String description;

    public boolean isInProgress() {
        return this == INSTALLING || this == UNINSTALLING;
    }

    public boolean canInstall() {
        return this == UN_INSTALL || this == INSTALL_FAILED;
    }

    public boolean canUninstall() {
        return this == INSTALLED || this == INSTALL_FAILED || this == UNINSTALL_FAILED;
    }
}
