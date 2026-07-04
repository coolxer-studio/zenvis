package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.Dashboard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户自定义看板
 */
public interface DashboardRepository extends BaseRepository<Dashboard, Integer> {

    /**
     * 根据id查询菜单
     *
     * @param id 菜单id
     * @return 菜单对象
     */
    Optional<Dashboard> findById(Integer id);

    Optional<Dashboard> findByCode(String code);

    List<Dashboard> findBySource(String source);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYSTEM_DASHBOARD + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:url IS NULL OR a.url like concat('%',:url,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYSTEM_DASHBOARD + " a WHERE " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:url IS NULL OR a.url like concat('%',:url,'%')) ")
    Page<Dashboard> findByPage(Pageable pageable, @Param("name") String name, @Param("url") String url);


}
