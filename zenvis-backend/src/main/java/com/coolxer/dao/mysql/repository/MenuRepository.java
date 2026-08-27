package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.Menu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 菜单权限管理
 */
public interface MenuRepository extends BaseRepository<Menu, Integer> {

    /**
     * 查询菜单
     *
     * @param ids 菜单id列表
     * @return 菜单列表
     */
    List<Menu> findByIdIn(List<Integer> ids);

    /**
     * 根据父级id查询菜单
     *
     * @param parentId 父级id
     * @return 菜单列表
     */
    List<Menu> findByParentIdOrderByOrderNumber(Integer parentId);

    /**
     * 根据来源查找
     *
     * @param source
     * @return
     */
    List<Menu> findBySource(String source);

    /**
     * 根据id查询菜单
     *
     * @param id 菜单id
     * @return 菜单对象
     */
    Optional<Menu> findById(Integer id);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_SYS_MENU + " a WHERE a.parent_id = 0 AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%') OR EXISTS (" +
                    "SELECT 1 FROM " + MysqlFinalTableName.T_SYS_MENU + " b WHERE b.parent_id = a.id AND b.name like concat('%',:name,'%')" +
                    ")) AND " +
                    "(:route IS NULL OR a.route like concat('%',:route,'%') OR EXISTS (" +
                    "SELECT 1 FROM " + MysqlFinalTableName.T_SYS_MENU + " b WHERE b.parent_id = a.id AND b.route like concat('%',:route,'%')" +
                    ")) " +
                    "ORDER BY a.order_number ASC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_SYS_MENU + " a WHERE a.parent_id = 0 AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%') OR EXISTS (" +
                    "SELECT 1 FROM " + MysqlFinalTableName.T_SYS_MENU + " b WHERE b.parent_id = a.id AND b.name like concat('%',:name,'%')" +
                    ")) AND " +
                    "(:route IS NULL OR a.route like concat('%',:route,'%') OR EXISTS (" +
                    "SELECT 1 FROM " + MysqlFinalTableName.T_SYS_MENU + " b WHERE b.parent_id = a.id AND b.route like concat('%',:route,'%')" +
                    ")) ")
    Page<Menu> findByPage(Pageable pageable, @Param("name") String name, @Param("route") String route);

    /**
     * 根据父级菜单ID获取最大排序号
     *
     * @param id
     * @return
     */
    @Query(nativeQuery = true, value = "SELECT MAX(a.order_number) FROM " + MysqlFinalTableName.T_SYS_MENU + " a WHERE a.parent_id = :id")
    Optional<Integer> getMaxOrderNumberById(@Param("id") Integer id);

}
