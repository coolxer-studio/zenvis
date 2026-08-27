package com.coolxer.service.system;

import cn.hutool.core.lang.tree.Tree;
import com.coolxer.model.system.vo.MenuVo;

import java.util.List;

/**
 * 权限树接口
 */
public interface PermissionTreeService {

    /**
     * 返回菜单权限树状结果
     *
     * @param menuList 权限列表
     * @return 结果
     */
    List<Tree<String>> getPermissionTreeFromMenu(List<MenuVo> menuList);

}
