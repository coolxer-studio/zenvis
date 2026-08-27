package com.coolxer.model.system.vo;

import com.coolxer.dao.mysql.entity.Role;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 角色传输对象
 */
@Data
public class RoleVo implements Serializable {

    /**
     * ID
     */
    private Integer id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色id
     */
    private Integer roleId;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 菜单权限列表
     */
    private List<Integer> menuIds;

    /**
     * 菜单权限名称列表
     */
    private List<String> menuNames;

    /**
     * 是否超级管理员角色
     */
    private Boolean isSuperAdmin;

    public RoleVo(Role role, List<Integer> menuIds, List<String> menuNames) {
        this.id = role.getId();
        this.name = role.getName();
        this.roleId = role.getId();
        this.updateTime = role.getUpdateTime();
        this.menuIds = menuIds;
        this.menuNames = menuNames;
        this.isSuperAdmin = role.getIsSuperAdmin();
    }

}
