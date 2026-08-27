package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.system.dto.RoleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


/**
 * 角色表
 */

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYS_ROLE)
public class Role extends BaseEntity {

    /**
     * 角色名称
     */
    @Column
    private String name;

    /**
     * 是否超级管理员角色
     */
    @Column(name = "is_super_admin", columnDefinition = "boolean default false")
    private Boolean isSuperAdmin = false;


    public void updateFromDto(RoleDto roleDto) {
        this.name = roleDto.getName();
    }
}
