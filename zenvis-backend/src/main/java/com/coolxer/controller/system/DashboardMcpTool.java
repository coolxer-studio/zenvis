package com.coolxer.controller.system;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.service.system.DashboardService;
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
 * MCP工具服务 - 暴露看板管理相关接口为MCP工具
 */
@Service
public class DashboardMcpTool {

    private final DashboardService dashboardService;

    public DashboardMcpTool(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 创建看板
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "dashboard_create", description = "创建一个新的看板")
    public DashboardVo create(@ToolParam(description = "看板参数，包含name、code、type、url、configIndex、htmlPath和isDefault等字段；HTML看板路径必须为相对路径") DashboardDto request) {
        return new DashboardVo(dashboardService.create(request));
    }

    /**
     * 更新看板
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "dashboard_update", description = "更新指定看板")
    public Boolean update(@ToolParam(description = "看板ID") Long id,
                          @ToolParam(description = "看板参数，包含name、code、type、url、configIndex、htmlPath和isDefault等字段；设置新默认看板会替换原默认看板") DashboardDto request) {
        return dashboardService.update(id, request);
    }

    /**
     * 批量更新看板
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "dashboard_bulk_update", description = "批量更新多个看板")
    public Boolean bulkUpdate(@ToolParam(description = "看板ID列表") List<Long> ids,
                              @ToolParam(description = "看板参数，包含name、code、type、url、configIndex、htmlPath和isDefault等字段；不能同时设置多个默认看板") DashboardDto request) {
        return dashboardService.bulkUpdate(ids, request);
    }

    /**
     * 删除看板
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "dashboard_delete", description = "删除指定看板")
    public Boolean delete(@ToolParam(description = "看板ID") Long id) {
        dashboardService.delete(id);
        return true;
    }

    /**
     * 批量删除看板
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "dashboard_bulk_delete", description = "批量删除看板")
    public Boolean bulkDelete(@ToolParam(description = "看板ID列表") List<Long> ids) {
        dashboardService.deleteByIds(ids);
        return true;
    }

    /**
     * 分页查询看板
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "dashboard_list", description = "分页查询看板，可按看板名称和URL过滤")
    public PageRowsVo<DashboardVo> list(@ToolParam(description = "查询参数，包含page、perPage、name和url") DashboardSearchDto request) {
        return dashboardService.getPageList(request);
    }

    /**
     * 获取全部看板
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "dashboard_list_all", description = "获取全部看板列表")
    public List<DashboardVo> listAll() {
        return dashboardService.findAll();
    }

    /**
     * 获取看板详情
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "dashboard_view", description = "获取指定看板详情")
    public DashboardVo view(@ToolParam(description = "看板ID") Long id) {
        return dashboardService.info(id);
    }

    /**
     * 获取看板类型选项
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "dashboard_type_options", description = "获取看板类型选项，返回options数组，label为类型描述，value为枚举值")
    public Map<String, List<Map<String, String>>> typeOptions() {
        return Map.of("options", CommonUtil.createOptions(
                Arrays.asList(DashboardType.values()),
                DashboardType::getDescription,
                DashboardType::name
        ));
    }
}
