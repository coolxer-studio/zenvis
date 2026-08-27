package com.coolxer.model.system.vo;

import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户传输对象
 */
@Data
@AllArgsConstructor
public class UserVo implements Serializable {

    /**
     * id
     */
    private int id;

    /**
     * 登录邮箱
     */
    private String email;

    /**
     * 用户名
     */
    private String name;

    /**
     * 角色id
     */
    private int roleId;

    /**
     * 角色名
     */
    private String roleName;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否超级管理员
     */
    private Boolean isSuperAdmin;

    public UserVo(int id, String email, String name, int roleId, String roleName, Date updateTime) {
        this(id, email, name, roleId, roleName, updateTime, false);
    }

    public UserVo(User user, Role role) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.updateTime = user.getUpdateTime();
        this.roleId = role.getId();
        this.roleName = role.getName();
        this.isSuperAdmin = user.getIsSuperAdmin();
    }

}
