package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.PluginDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 插件表
 */

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_SYS_PLUGIN)
public class Plugin extends BaseEntity {

    /**
     * 插件名
     */
    @Column
    private String name;

    /**
     * 图标(base64存储)
     */
    @Column(columnDefinition = "TEXT")
    private String icon;

    /**
     * 包名
     */
    @Column(name = "package_name")
    private String packageName;

    /**
     * 版本
     */
    @Column
    private String version;

    /**
     * 插件简介
     */
    @Column
    private String description;

    /**
     * 作者
     */
    @Column
    private String author;

    /**
     * 状态
     */
    @Column
    @Enumerated(EnumType.STRING)
    private PluginStatusType status;

    /**
     * 插件包路径
     */
    @Column(name = "plugin_path")
    private String pluginPath;

    /**
     * 最近一次插件操作摘要
     */
    @Column(name = "operation_message")
    private String operationMessage;

    /**
     * 最近一次插件操作错误
     */
    @Column(name = "operation_error", columnDefinition = "TEXT")
    private String operationError;

    /**
     * 最近一次插件操作开始时间
     */
    @Column(name = "operation_started_at")
    private Date operationStartedAt;

    /**
     * 最近一次插件操作结束时间
     */
    @Column(name = "operation_ended_at")
    private Date operationEndedAt;

    public void updateFromDto(PluginDto pluginDto) {
        if (pluginDto.getName() != null) {
            this.name = pluginDto.getName();
        }
        if (pluginDto.getIcon() != null) {
            this.icon = pluginDto.getIcon();
        }
        if (pluginDto.getPackageName() != null) {
            this.packageName = pluginDto.getPackageName();
        }
        if (pluginDto.getVersion() != null) {
            this.version = pluginDto.getVersion();
        }
        if (pluginDto.getDescription() != null) {
            this.description = pluginDto.getDescription();
        }
        if (pluginDto.getAuthor() != null) {
            this.author = pluginDto.getAuthor();
        }
        if (pluginDto.getPluginPath() != null) {
            this.pluginPath = pluginDto.getPluginPath();
        }

    }

}
