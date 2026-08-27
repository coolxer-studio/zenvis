package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.dao.mysql.entity.Plugin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 插件传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PluginVo implements Serializable {

    /**
     * id
     */
    private int id;

    /**
     * 插件名
     */
    private String name;

    /**
     * 图标
     */
    private String icon;

    /**
     * 包名
     */
    private String packageName;

    /**
     * 版本
     */
    private String version;

    /**
     * 插件简介
     */
    private String description;

    /**
     * 作者
     */
    private String author;

    /**
     * 状态
     */
    private PluginStatusType status;

    /**
     * 状态描述
     */
    private String statusDescription;


    /**
     * 插件包路径
     */
    private String pluginPath;

    /**
     * 升级候选包路径。
     */
    private String pendingUpgradePath;

    /**
     * 升级候选版本。
     */
    private String pendingUpgradeVersion;

    /**
     * 升级操作标识。
     */
    private String upgradeOperationId;

    /**
     * 最近一次插件操作摘要
     */
    private String operationMessage;

    /**
     * 最近一次插件操作错误
     */
    private String operationError;

    /**
     * 最近一次插件操作开始时间
     */
    private Date operationStartedAt;

    /**
     * 最近一次插件操作结束时间
     */
    private Date operationEndedAt;

    /**
     * 更新时间
     */
    private Date updateTime;

    public PluginVo(Plugin plugin) {
        this.id = plugin.getId();
        this.name = plugin.getName();
        this.icon = plugin.getIcon();
        this.packageName = plugin.getPackageName();
        this.version = plugin.getVersion();
        this.description = plugin.getDescription();
        this.author = plugin.getAuthor();
        this.status = plugin.getStatus();
        this.statusDescription = plugin.getStatus() == null ? "" : plugin.getStatus().getDescription();
        this.pluginPath = plugin.getPluginPath();
        this.pendingUpgradePath = plugin.getPendingUpgradePath();
        this.pendingUpgradeVersion = plugin.getPendingUpgradeVersion();
        this.upgradeOperationId = plugin.getUpgradeOperationId();
        this.operationMessage = plugin.getOperationMessage();
        this.operationError = plugin.getOperationError();
        this.operationStartedAt = plugin.getOperationStartedAt();
        this.operationEndedAt = plugin.getOperationEndedAt();
        this.updateTime = plugin.getUpdateTime();
    }

}
