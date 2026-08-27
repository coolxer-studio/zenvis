package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.MenuVo;

import java.util.List;

/**
 * 菜单接口
 */
public interface MenuService {

    /**
     * 查询全部列表
     *
     * @return 结果
     */
    List<MenuVo> findAll();

    /**
     * 创建菜单
     *
     * @param menuDto 传输实体
     */
    Menu create(MenuDto menuDto);

    /**
     * 修改菜单
     *
     * @param id      菜单id
     * @param menuDto 用户传输实体
     */
    Boolean update(Long id, MenuDto menuDto);

    /**
     * 更新菜单排序
     *
     * @param menuOrderRowDto 排序参数
     * @return 结果
     */
    Boolean updateOrder(MenuOrderRowDto menuOrderRowDto);

    /**
     * 删除菜单
     *
     * @param id 菜单id
     */
    void delete(Long id);

    /**
     * 批量删除
     */
    void deleteByIds(List<Long> ids);

    /**
     * 菜单详情
     *
     * @param id 菜单id
     * @return 结果
     */
    MenuVo info(Long id);

    /**
     * 获取菜单列表
     *
     * @param menuSearchDto 搜索参数
     * @return 菜单列表
     */
    PageRowsVo<MenuVo> getPageList(MenuSearchDto menuSearchDto);

    /**
     * 获取所有父级菜单
     *
     * @return 菜单列表
     */
    List<Menu> findAllParentMenu();

    /**
     * 根据来源查询菜单
     *
     * @param source
     * @return
     */
    List<Menu> findBySource(String source);
}
