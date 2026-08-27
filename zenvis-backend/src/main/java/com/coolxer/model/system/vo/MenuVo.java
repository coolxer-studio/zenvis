package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.dao.mysql.entity.Menu;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 菜单传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuVo implements Serializable {

    /**
     * id
     */
    private Integer id;

    /**
     * 菜单名
     */
    private String name;

    /**
     * 类型
     */
    private MenuType type;

    /**
     * 类型描述
     */
    private String typeDescription;

    /**
     * 路由
     */
    private String route;

    /**
     * 参数，不同类型参数值不同
     */
    private String params;

    /**
     * 父级id
     */
    private Integer parentId;

    /**
     * 目录顺序
     */
    private Integer orderNumber;

    /**
     * 目录级别
     */
    private MenuLevel level;

    /**
     * 角标文字
     */
    private String superscript;

    /**
     * 是否可编辑
     */
    private Boolean isEditable;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 子级菜单
     */
    private List<MenuVo> children;

    /**
     * 来源
     */
    private String source;

    public MenuVo(Menu menu) {
        this.id = menu.getId();
        this.name = menu.getName();
        this.type = menu.getType();
        this.typeDescription = menu.getType().getDescription();
        this.route = menu.getRoute();
        this.params = menu.getParams();
        this.parentId = menu.getParentId();
        this.orderNumber = menu.getOrderNumber();
        this.level = menu.getLevel();
        this.superscript = menu.getSuperscript();
        this.isEditable = menu.getIsEditable();
        this.source = menu.getSource();
        this.updateTime = menu.getUpdateTime();
    }

}