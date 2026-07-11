package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 角色数据库操作类
 */
public interface RoleRepository extends BaseRepository<Role, Integer> {

    /**
     * 查询角色
     *
     * @param id 角色id列
     * @return 角色
     */
    Role findById(Integer id);

    /**
     * 根据名称查询角色
     *
     * @param name 角色名称
     * @return 角色
     */
    List<Role> findByName(String name);

    /**
     * 查询超级管理员角色
     *
     * @param isSuperAdmin 是否超级管理员角色
     * @return 角色列表
     */
    List<Role> findByIsSuperAdmin(Boolean isSuperAdmin);

    /**
     * 查询角色名
     *
     * @param ids 角色id列表
     * @return 角色列表
     */
    List<Role> findByIdIn(List<Integer> ids);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_ROLE + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_ROLE + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) ")
    Page<Role> findByPage(Pageable pageable, @Param("name") String name);

    /**
     * 分页查询，排除超级管理员角色
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_ROLE + " a WHERE " +
                    "(a.is_super_admin IS NULL OR a.is_super_admin = false) AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_ROLE + " a WHERE " +
                    "(a.is_super_admin IS NULL OR a.is_super_admin = false) AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) ")
    Page<Role> findByPageWithoutSuperAdmin(Pageable pageable, @Param("name") String name);
}
