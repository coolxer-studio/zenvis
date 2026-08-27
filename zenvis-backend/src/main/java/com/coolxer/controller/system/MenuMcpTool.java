package com.coolxer.controller.system;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.utils.CommonUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP工具服务 - 暴露菜单管理相关接口为MCP工具
 */
@Service
public class MenuMcpTool {

    private final MenuService menuService;

    public MenuMcpTool(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 创建菜单
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_create", description = "创建一个新的菜单；非内置应用类型会自动使用菜单类型对应的路由")
    public MenuVo create(@ToolParam(description = "菜单参数，包含name、type、level、parentId、route、params、superscript和source等字段") MenuDto request) {
        return new MenuVo(menuService.create(request));
    }

    /**
     * 更新菜单
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_update", description = "更新指定菜单；内置不可编辑菜单不会被更新")
    public Boolean update(@ToolParam(description = "菜单ID") Long id,
                          @ToolParam(description = "菜单参数，包含name、type、level、parentId、route、params、superscript和source等字段") MenuDto request) {
        return menuService.update(id, request);
    }

    /**
     * 批量更新菜单
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_bulk_update", description = "批量更新多个菜单；内置不可编辑菜单不会被更新")
    public Boolean bulkUpdate(@ToolParam(description = "菜单ID列表") List<Long> ids,
                              @ToolParam(description = "菜单参数，包含name、type、level、parentId、route、params、superscript和source等字段") MenuDto request) {
        for (Long id : ids) {
            menuService.update(id, request);
        }
        return true;
    }

    /**
     * 删除菜单
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_delete", description = "删除指定菜单；内置不可编辑菜单不会被删除")
    public Boolean delete(@ToolParam(description = "菜单ID") Long id) {
        menuService.delete(id);
        return true;
    }

    /**
     * 批量删除菜单
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_bulk_delete", description = "批量删除菜单；内置不可编辑菜单不会被删除")
    public Boolean bulkDelete(@ToolParam(description = "菜单ID列表") List<Long> ids) {
        menuService.deleteByIds(ids);
        return true;
    }

    /**
     * 更新菜单排序
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "menu_update_order", description = "更新一级和二级菜单排序；rows为排序后的菜单树")
    public Boolean updateOrder(@ToolParam(description = "菜单排序参数，rows为排序后的一级菜单列表，每个菜单可包含children二级菜单列表") MenuOrderRowDto request) {
        return menuService.updateOrder(request);
    }

    /**
     * 分页查询菜单
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_list", description = "分页查询菜单树，可按菜单名称和路由过滤，仅返回一级菜单并附带二级子菜单")
    public PageRowsVo<MenuVo> list(@ToolParam(description = "查询参数，包含page、perPage、name和route") MenuSearchDto request) {
        return menuService.getPageList(request);
    }

    /**
     * 获取全部菜单
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_list_all", description = "获取全部菜单扁平列表")
    public List<MenuVo> listAll() {
        return menuService.findAll();
    }

    /**
     * 获取菜单详情
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_view", description = "获取指定菜单详情")
    public MenuVo view(@ToolParam(description = "菜单ID") Long id) {
        return menuService.info(id);
    }

    /**
     * 获取一级菜单选项
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_parent_options", description = "获取一级菜单下拉选项，返回options数组，label为菜单名称，value为菜单ID")
    public Map<String, List<Map<String, String>>> parentOptions() {
        return Map.of("options", CommonUtil.createOptions(
                menuService.findAllParentMenu(),
                Menu::getName,
                item -> String.valueOf(item.getId())
        ));
    }

    /**
     * 获取菜单类型选项
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_type_options", description = "获取菜单类型选项，返回options数组，label为类型描述，value为枚举值")
    public Map<String, List<Map<String, String>>> typeOptions() {
        return Map.of("options", CommonUtil.createOptions(
                Arrays.asList(MenuType.values()),
                MenuType::getDescription,
                MenuType::name
        ));
    }

    /**
     * 获取菜单级别选项
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "menu_level_options", description = "获取菜单级别选项，返回options数组，label为级别描述，value为枚举值")
    public Map<String, List<Map<String, String>>> levelOptions() {
        return Map.of("options", CommonUtil.createOptions(
                Arrays.asList(MenuLevel.values()),
                MenuLevel::getDescription,
                MenuLevel::name
        ));
    }
}
