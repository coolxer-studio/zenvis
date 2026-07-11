package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.RolePermission;

import java.util.List;

/**
 * 角色权限
 */
public interface RolePermissionRepository extends BaseRepository<RolePermission, Integer> {

    /**
     * 角色对应权限
     *
     * @param roleId 角色id
     * @return 角色权限列表
     */
    List<RolePermission> findByRoleId(Integer roleId);

    /**
     * 查询指定角色和权限关系
     *
     * @param roleId       角色id
     * @param permissionId 权限id
     * @return 角色权限
     */
    RolePermission findByRoleIdAndPermissionId(Integer roleId, Integer permissionId);
}
