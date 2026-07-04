package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.dao.mysql.entity.Dashboard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;


/**
 * 用户自定义看板
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardVo implements Serializable {

    /**
     * id
     */
    private int id;

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
     * 看板类型描述
     */
    private String typeDescription;

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
     * 来源
     */
    private String source;

    /**
     * 更新时间
     */
    private Date updateTime;

    public DashboardVo(Dashboard dashboard) {
        this.id = dashboard.getId();
        this.name = dashboard.getName();
        this.code = dashboard.getCode();
        this.type = dashboard.getType();
        this.typeDescription = dashboard.getType().getDescription();
        this.url = dashboard.getUrl();
        this.configIndex = dashboard.getConfigIndex();
        this.htmlPath = dashboard.getHtmlPath();
        this.source = dashboard.getSource();
        this.updateTime = dashboard.getUpdateTime();
    }

}
