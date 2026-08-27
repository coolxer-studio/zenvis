package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.Plugin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 插件权限管理
 */
public interface PluginRepository extends BaseRepository<Plugin, Integer> {

    /**
     * 根据id查询插件
     *
     * @param id 插件id
     * @return 插件对象
     */
    Optional<Plugin> findById(Integer id);

    /**
     * 根据包名查询插件
     *
     * @param packageName 包名
     * @return 插件对象
     */
    Optional<Plugin> findByPackageName(String packageName);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_PLUGIN + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:packageName IS NULL OR a.package_name like concat('%',:packageName,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_PLUGIN + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:packageName IS NULL OR a.package_name like concat('%',:packageName,'%')) ")
    Page<Plugin> findByPage(Pageable pageable, @Param("name") String name, @Param("packageName") String packageName);

}