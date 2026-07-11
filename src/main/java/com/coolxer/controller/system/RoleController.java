package com.coolxer.controller.system;


import cn.hutool.core.lang.tree.Tree;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.RoleDto;
import com.coolxer.model.system.dto.RoleSearchDto;
import com.coolxer.model.system.vo.RoleVo;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PermissionTreeService;
import com.coolxer.service.system.RoleService;
import com.coolxer.utils.CommonUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/role/")
public class RoleController extends BaseController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private PermissionTreeService permissionTreeService;


    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody RoleDto roleDto) {

        try {
            if (roleService.create(roleDto) != null) {
                return ResponseWrap.success("创建成功");
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            roleService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            roleService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @Valid @RequestBody RoleDto roleDto) {
        try {
            if (roleService.update(id, roleDto)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{ids}/bulk-update"})
    public ResponseWrap<?> bulkUpdate(@PathVariable("ids") Long[] ids, @Valid @RequestBody RoleDto roleDto) {
        try {
            for (long id : ids) {
                roleService.update(id, roleDto);
            }
            return ResponseWrap.success("修改成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(RoleSearchDto roleSearchDto) {
        try {
            PageRowsVo<RoleVo> pageDataVo = roleService.getPageList(roleSearchDto, getSessionUser());
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<RoleVo> query(@PathVariable("id") Long id) {
        try {
            RoleVo roleVo = roleService.info(id, getSessionUser());
            if (roleVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(roleVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 角色列表
     */
    @GetMapping(value = "/type/list")
    @Operation(summary = "全部角色列表", description = "全部角色列表")
    public ResponseWrap<?> listAllRoles() {
        try {
            Map<String, List<Map<String, String>>> result = new HashMap<>();
            User currentUser = getSessionUser();
            result.put("options", CommonUtil.createOptions(
                    roleService.findAll(currentUser),
                    item -> item.getName(),
                    item -> String.valueOf(item.getId())
            ));
            return ResponseWrap.success(result);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    /**
     * 全权限列表
     */
    @GetMapping(value = "/permission/tree")
    @Operation(summary = "全权限列表", description = "全权限列表")
    public ResponseWrap<List<Tree<String>>> allPermissionTree() {
        return ResponseWrap.success(permissionTreeService.getPermissionTreeFromMenu(menuService.findAll()));


    }


}
