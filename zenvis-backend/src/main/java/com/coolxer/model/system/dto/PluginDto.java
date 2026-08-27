package com.coolxer.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 插件传输对象
 */
@Data
public class PluginDto {

    /**
     * id
     */
    private int id;

    /**
     * 插件名
     */
    @NotBlank(message = "插件名称不能为空")
    private String name;

    /**
     * 图标
     */
    private String icon;

    /**
     * 包名
     */
    @NotBlank(message = "包名不能为空")
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
     * 插件包路径
     */
    private String pluginPath;

}
