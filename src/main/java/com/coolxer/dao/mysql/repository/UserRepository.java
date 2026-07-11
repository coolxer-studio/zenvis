package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 用户管理
 */
public interface UserRepository extends BaseRepository<User, Integer> {

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户对象
     */
    User findByEmail(String email);

    /**
     * 根据id查询用户
     *
     * @param userId 用户id
     * @return 用户对象
     */
    User findById(Integer userId);

    /**
     * 查询超级管理员用户
     *
     * @param isSuperAdmin 是否超级管理员
     * @return 用户列表
     */
    List<User> findByIsSuperAdmin(Boolean isSuperAdmin);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_USERS + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:email IS NULL OR a.email like concat('%',:email,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_USERS + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:email IS NULL OR a.email like concat('%',:email,'%')) ")
    Page<User> findByPage(Pageable pageable, @Param("name") String name, @Param("email") String email);

    /**
     * 分页查询，排除超级管理员
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_USERS + " a WHERE " +
                    "(a.is_super_admin IS NULL OR a.is_super_admin = false) AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:email IS NULL OR a.email like concat('%',:email,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_USERS + " a WHERE " +
                    "(a.is_super_admin IS NULL OR a.is_super_admin = false) AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:email IS NULL OR a.email like concat('%',:email,'%')) ")
    Page<User> findByPageWithoutSuperAdmin(Pageable pageable, @Param("name") String name, @Param("email") String email);

}
