package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.UserRole;

import java.util.List;

/**
 * 用户角色管理
 */
public interface UserRoleRepository extends BaseRepository<UserRole, Integer> {

    /**
     * 用户角色
     *
     * @param userId 用户id
     * @return 结果
     */
    UserRole findByUserId(Integer userId);

    /**
     * 用户列表展示角色信息
     *
     * @param userIds 用户列表
     * @return 结果
     */
    List<UserRole> findByUserIdIn(List<Integer> userIds);

    /**
     * 根据用户id删除用户
     *
     * @param userId 用户id
     */
    void deleteByUserId(Integer userId);

    /**
     * 角色查询用户
     *
     * @param roleId 角色id
     * @return 结果
     */
    List<UserRole> findByRoleId(Integer roleId);

}
