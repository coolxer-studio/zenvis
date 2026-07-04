package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.DashboardDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


/**
 * 用户自定义看板
 */
@Data
@Entity
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYSTEM_DASHBOARD)
public class Dashboard extends BaseEntity {

    /**
     * 看板名称
     */
    @Column
    private String name;

    /**
     * 编码
     */
    @Column
    private String code;

    /**
     * 看板类型 route 路由 link 外链
     */
    @Column
    private DashboardType type;

    /**
     * 路由地址
     */
    @Column
    private String url;

    /**
     * 低代码配置索引
     */
    @Column
    private String configIndex;

    /**
     * HTML文件路径
     */
    @Column
    private String htmlPath;

    /**
     * 来源，用于区分插件导入资源。
     */
    @Column
    private String source = "default";

    public void updateFromDto(DashboardDto dashboardDto) {
        this.name = dashboardDto.getName();
        this.code = dashboardDto.getCode();
        this.type = dashboardDto.getType();
        this.url = dashboardDto.getUrl();
        this.configIndex = dashboardDto.getConfigIndex();
        this.htmlPath = dashboardDto.getHtmlPath();
        if (dashboardDto.getSource() != null) {
            this.source = dashboardDto.getSource();
        }
    }
}
