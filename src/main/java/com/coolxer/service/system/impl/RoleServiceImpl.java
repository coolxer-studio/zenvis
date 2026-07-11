package com.coolxer.service.system.impl;

import com.coolxer.commons.constant.SystemBuiltInConstants;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.RolePermission;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.entity.UserRole;
import com.coolxer.dao.mysql.repository.MenuRepository;
import com.coolxer.dao.mysql.repository.RolePermissionRepository;
import com.coolxer.dao.mysql.repository.RoleRepository;
import com.coolxer.dao.mysql.repository.UserRoleRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.RoleDto;
import com.coolxer.model.system.dto.RoleSearchDto;
import com.coolxer.model.system.vo.RoleVo;
import com.coolxer.service.system.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 角色管理
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Override
    public List<RoleVo> findAll(User currentUser) {
        return roleRepository.findAll().stream().filter(role -> isVisible(role, currentUser)).map(role -> {
            List<Integer> menuIdList = rolePermissionRepository.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).toList();
            List<String> menuNameList = menuRepository.findByIdIn(menuIdList).stream().map(Menu::getName).toList();
            return new RoleVo(role, menuIdList, menuNameList);
        }).toList();
    }

    @Override
    public PageRowsVo<RoleVo> getPageList(RoleSearchDto roleSearchDto, User currentUser) {
        try {
            Pageable pageable = PageRequest.of(roleSearchDto.getPage() - 1, roleSearchDto.getPerPage());
            Page<Role> byPage;
            if (SystemBuiltInConstants.isSuperAdmin(currentUser)) {
                byPage = roleRepository.findByPage(pageable, roleSearchDto.getName());
            } else {
                byPage = roleRepository.findByPageWithoutSuperAdmin(pageable, roleSearchDto.getName());
            }
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(role -> {
                        List<Integer> menuIdList = rolePermissionRepository.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).toList();
                        List<String> menuNameList = menuRepository.findByIdIn(menuIdList).stream().map(Menu::getName).toList();
                        return new RoleVo(role, menuIdList, menuNameList);
                    }).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public Role create(RoleDto roleDto) {
        checkCreateOrUpdate(roleDto);
        if (SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME.equals(roleDto.getName())) {
            throw new ApiException(ResultCodeEnum.SUPER_ADMIN_ROLE_NOT_ALLOWED);
        }
        Role role = new Role();
        role.updateFromDto(roleDto);
        role = roleRepository.save(role);

        Role finalRole = role;
        List<RolePermission> roleList = Arrays.stream(roleDto.getMenuIds().split(",")).toList()
                .stream()
                .map(p -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(finalRole.getId());
                    rolePermission.setPermissionId(Integer.parseInt(p));
                    return rolePermission;
                }).toList();

        rolePermissionRepository.saveAll(roleList);
        return role;
    }

    @Override
    public Boolean update(Long id, RoleDto roleDto) {
        checkCreateOrUpdate(roleDto);
        try {
            Optional<Role> optionalRole = roleRepository.findById(id);
            if (optionalRole.isPresent()) {
                Role role = optionalRole.get();
                if (SystemBuiltInConstants.isSuperAdmin(role)
                        || SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME.equals(roleDto.getName())) {
                    throw new ApiException(ResultCodeEnum.SUPER_ADMIN_ROLE_NOT_ALLOWED);
                }
                role.updateFromDto(roleDto);
                roleRepository.save(role);

                // 角色权限修改
                List<RolePermission> rolePermissionList = rolePermissionRepository.findByRoleId(role.getId());
                rolePermissionRepository.deleteAll(rolePermissionList);

                rolePermissionList = Arrays.stream(roleDto.getMenuIds().split(",")).toList().stream().map(p -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(role.getId());
                    rolePermission.setPermissionId(Integer.parseInt(p));
                    return rolePermission;
                }).toList();
                rolePermissionRepository.saveAll(rolePermissionList);
                return true;
            }
            return false;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id) {
        if (Objects.isNull(id)) {
            throw new ApiException(ResultCodeEnum.ROLE_ID_MUST_NOT_NULL);
        }
        Optional<Role> optionalRole = roleRepository.findById(id);
        if (optionalRole.isPresent() && SystemBuiltInConstants.isSuperAdmin(optionalRole.get())) {
            throw new ApiException(ResultCodeEnum.SUPER_ADMIN_ROLE_NOT_ALLOWED);
        }
        // 存在绑定的用户，不能删除角色
        List<UserRole> userRoleList = userRoleRepository.findByRoleId(id.intValue());
        if (CollectionUtils.isNotEmpty(userRoleList)) {
            throw new ApiException(ResultCodeEnum.EXISTS_USER_ROLE);
        }

        roleRepository.deleteById(id);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public RoleVo info(Long id, User currentUser) {
        try {
            Optional<Role> optionalRole = roleRepository.findById(id);
            return optionalRole.map(role -> {
                if (!isVisible(role, currentUser)) {
                    return null;
                }
                List<Integer> menuIdList = rolePermissionRepository.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).toList();
                List<String> menuNameList = menuRepository.findByIdIn(menuIdList).stream().map(Menu::getName).toList();
                return new RoleVo(role, menuIdList, menuNameList);
            }).orElse(null);
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    private static void checkCreateOrUpdate(RoleDto roleDto) {
        if (StringUtils.isEmpty(roleDto.getName())) {
            throw new ApiException(ResultCodeEnum.ROLE_NAME_MUST_NOT_NULL);
        }
        if (StringUtils.isEmpty(roleDto.getMenuIds())) {
            throw new ApiException(ResultCodeEnum.PERMISSION_MUST_NOT_NULL);
        }
    }

    private boolean isVisible(Role role, User currentUser) {
        return !SystemBuiltInConstants.isSuperAdmin(role) || SystemBuiltInConstants.isSuperAdmin(currentUser);
    }

}
