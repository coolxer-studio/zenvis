package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.MenuDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 菜单表
 */

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_SYS_MENU)
public class Menu extends BaseEntity {

    /**
     * 菜单名
     */
    @Column
    private String name;

    /**
     * 类型
     */
    @Column
    private MenuType type;

    /**
     * 路由
     */
    @Column
    private String route;

    /**
     * 参数，不同类型参数值不同
     */
    @Column
    private String params;

    /**
     * 父级id
     */
    @Column(name = "parent_id")
    private Integer parentId;

    /**
     * 目录顺序
     */
    @Column(name = "order_number")
    private Integer orderNumber;

    /**
     * 目录级别
     */
    @Column
    private MenuLevel level;

    /**
     * 角标文字
     */
    @Column
    private String superscript;

    /**
     * 是否可编辑
     */
    @Column
    private Boolean isEditable;

    /**
     * 来源
     */
    @Column
    private String source = "default";


    public void updateFromDto(MenuDto menuDto) {
        if (menuDto.getName() != null) {
            this.name = menuDto.getName();
        }
        if (menuDto.getType() != null) {
            this.type = menuDto.getType();
        }
        if (menuDto.getRoute() != null) {
            this.route = menuDto.getRoute();
        }
        if (menuDto.getParams() != null) {
            this.params = menuDto.getParams();
        }
        if (menuDto.getParentId() != null) {
            this.parentId = menuDto.getParentId();
        }
        if (menuDto.getOrderNumber() != null) {
            this.orderNumber = menuDto.getOrderNumber();
        }
        if (menuDto.getLevel() != null) {
            this.level = menuDto.getLevel();
        }
        if (menuDto.getSuperscript() != null) {
            this.superscript = menuDto.getSuperscript();
        }
        if (menuDto.getSource() != null) {
            this.source = menuDto.getSource();
        }
    }
}