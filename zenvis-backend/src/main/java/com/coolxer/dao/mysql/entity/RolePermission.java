package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 角色权限表
 */

@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYS_ROLE_PERMISSION)
public class RolePermission extends BaseEntity {

    /**
     * 角色id
     */
    @Column(name = "role_id")
    private int roleId;

    /**
     * 权限id
     */
    @Column(name = "permission_id")
    private int permissionId;


    public RolePermission(int roleId, int permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }
}
