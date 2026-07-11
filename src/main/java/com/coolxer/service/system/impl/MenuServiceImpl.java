package com.coolxer.service.system.impl;

import com.coolxer.commons.constant.SystemBuiltInConstants;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.RolePermission;
import com.coolxer.dao.mysql.repository.MenuRepository;
import com.coolxer.dao.mysql.repository.RolePermissionRepository;
import com.coolxer.dao.mysql.repository.RoleRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.MenuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 菜单接口实现
 */
@Slf4j
@Service
public class MenuServiceImpl implements MenuService {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ConfigService configService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Override
    public List<MenuVo> findAll() {

        return menuRepository.findAll().stream().map(MenuVo::new).toList();

    }

    @Override
    public PageRowsVo<MenuVo> getPageList(MenuSearchDto menuSearchDto) {
        try {
            Pageable pageable = PageRequest.of(menuSearchDto.getPage() - 1, menuSearchDto.getPerPage());
            Page<Menu> byPage;
            byPage = menuRepository.findByPage(pageable, menuSearchDto.getName(), menuSearchDto.getRoute());
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(menu -> {
                        MenuVo menuVo = new MenuVo(menu);
                        menuVo.setChildren(menuRepository.findByParentIdOrderByOrderNumber(menu.getId()).stream().map(MenuVo::new).toList());
                        return menuVo;
                    }).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public Menu create(MenuDto menuDto) {
        checkCreateOrUpdate(menuDto);
        Menu menu = new Menu();
        menu.updateFromDto(menuDto);
        // 检查赋值
        if (menu.getType() != MenuType.BUILT_APP) {
            menu.setRoute(menu.getType().getRoute());
        }
        Optional<Integer> optionalOrderNumber;
        if (menu.getLevel() == MenuLevel.LEVEL_1) {
            optionalOrderNumber = menuRepository.getMaxOrderNumberById(0);
            menu.setParentId(0);
        } else {
            optionalOrderNumber = menuRepository.getMaxOrderNumberById(menu.getParentId());
        }
        if (optionalOrderNumber != null && optionalOrderNumber.isPresent()) {
            menu.setOrderNumber(optionalOrderNumber.get() + 1);
        }
        menu.setIsEditable(Boolean.TRUE);
        if (menuDto.getType() == MenuType.POLICY_CONFIG && BooleanUtils.isTrue(menuDto.getCreateRootPath())) {
            // 创建根路径
            configService.addRootPath(menu.getParams());
        }
        menu = menuRepository.save(menu);
        grantToSuperAdmin(menu);
        return menu;
    }

    @Override
    public Boolean update(Long id, MenuDto menuDto) {
        checkCreateOrUpdate(menuDto);
        try {
            Optional<Menu> optionalMenu = menuRepository.findById(id);
            if (optionalMenu.isPresent()) {
                Menu menu = optionalMenu.get();
                // 检查是否可以编辑
                if (!menu.getIsEditable()) {
                    return false;
                }
                // 检查赋值
                if (menuDto.getType() != MenuType.BUILT_APP) {
                    menuDto.setRoute(menuDto.getType().getRoute());
                }
                if (menu.getLevel() != menuDto.getLevel()) {
                    Optional<Integer> optionalOrderNumber;
                    if (menuDto.getLevel() == MenuLevel.LEVEL_1) {
                        optionalOrderNumber = menuRepository.getMaxOrderNumberById(0);
                        menuDto.setParentId(0);
                    } else {
                        optionalOrderNumber = menuRepository.getMaxOrderNumberById(menuDto.getParentId());
                    }
                    if (optionalOrderNumber != null && optionalOrderNumber.isPresent()) {
                        menuDto.setOrderNumber(optionalOrderNumber.get() + 1);
                    }
                }
                menu.updateFromDto(menuDto);
                // 检查是否可以编辑
                if (menu.getIsEditable()) {
                    menuRepository.save(menu);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public Boolean updateOrder(MenuOrderRowDto menuOrderRowDto) {
        try {
            // 一级菜单
            for (int i = 0; i < menuOrderRowDto.getRows().size(); i++) {
                MenuDto menuDto1 = menuOrderRowDto.getRows().get(i);
                updateOrder(menuDto1.getId(), i);
                // 二级菜单
                for (int j = 0; j < menuDto1.getChildren().size(); j++) {
                    MenuDto menuDto2 = menuDto1.getChildren().get(j);
                    updateOrder(menuDto2.getId(), j);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("更新对象序列失败, id: {}", e);
            return false;
        }
    }

    private void updateOrder(int id, int order) {
        Optional<Menu> optionalMenu = menuRepository.findById(id);
        if (optionalMenu.isPresent()) {
            Menu menu = optionalMenu.get();
            menu.setOrderNumber(order);
            menuRepository.save(menu);
        }
    }

    @Override
    public void delete(Long id) {
        Optional<Menu> optionalMenu = menuRepository.findById(id);
        if (optionalMenu.isPresent()) {
            Menu menu = optionalMenu.get();
            // 检查是否可以编辑
            if (menu.getIsEditable()) {
                // 重新排序
                List<Menu> menus = menuRepository.findByParentIdOrderByOrderNumber(menu.getParentId());
                menus.forEach(menuTmp -> {
                    if (menuTmp.getOrderNumber() > menu.getOrderNumber()) {
                        menuTmp.setOrderNumber(menuTmp.getOrderNumber() - 1);
                        menuRepository.save(menuTmp);
                    }
                });
                // 删除
                menuRepository.deleteById(id);
            }
        }
    }


    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public MenuVo info(Long id) {
        try {
            Optional<Menu> optionalMenu = menuRepository.findById(id);
            return optionalMenu.map(MenuVo::new).orElse(null);
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    @Override
    public List<Menu> findAllParentMenu() {
        return menuRepository.findByParentIdOrderByOrderNumber(0);
    }

    @Override
    public List<Menu> findBySource(String source) {
        return menuRepository.findBySource(source);
    }

    private static void checkCreateOrUpdate(MenuDto menuDto) {
        if (StringUtils.isEmpty(menuDto.getName()) || menuDto.getType() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
    }

    private void grantToSuperAdmin(Menu menu) {
        List<Role> roles = roleRepository.findByIsSuperAdmin(true);
        if (roles.isEmpty()) {
            return;
        }
        Role role = roles.get(0);
        if (!SystemBuiltInConstants.isSuperAdmin(role)) {
            return;
        }
        RolePermission existing = rolePermissionRepository.findByRoleIdAndPermissionId(role.getId(), menu.getId());
        if (existing == null) {
            rolePermissionRepository.save(new RolePermission(role.getId(), menu.getId()));
        }
    }

}
