package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.DashboardType;
import lombok.Data;

import java.io.Serializable;


/**
 * 用户自定义看板
 */
@Data
public class DashboardDto implements Serializable {


    /**
     * id
     */
    private Integer id;

    /**
     * 看板名称
     */
    private String name;

    /**
     * 编码
     */
    private String code;

    /**
     * 看板类型 route:路由 link:外链
     */
    private DashboardType type;

    /**
     * URL地址
     */
    private String url;

    /**
     * 低代码配置索引
     */
    private String configIndex;

    /**
     * HTML文件路径
     */
    private String htmlPath;

    /**
     * 是否为默认看板；更新时为空表示保留原值
     */
    private Boolean isDefault;

    /**
     * 来源，用于区分插件导入资源
     */
    private String source;

}
