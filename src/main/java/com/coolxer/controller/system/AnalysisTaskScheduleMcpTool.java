package com.coolxer.controller.system;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskScheduleVo;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.system.AnalysisTaskScheduleService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

@Service
public class AnalysisTaskScheduleMcpTool {

    private final ObjectProvider<AnalysisTaskScheduleService> serviceProvider;

    public AnalysisTaskScheduleMcpTool(ObjectProvider<AnalysisTaskScheduleService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_schedule_create", description = "创建AI分析周期配置；到达Cron时间后生成独立的一次性AI分析任务")
    public AnalysisTaskScheduleVo create(
            @ToolParam(description = "周期任务模板，包含任务参数、6段cronExpression、enabled和skillIds")
            AnalysisTaskScheduleDto request) {
        return new AnalysisTaskScheduleVo(service().create(request));
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_schedule_update", description = "更新AI分析周期配置，仅影响未来生成的任务")
    public Boolean update(@ToolParam(description = "周期配置ID") Long id,
                          @ToolParam(description = "完整周期任务模板") AnalysisTaskScheduleDto request) {
        return service().update(id, request);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_schedule_set_enabled", description = "启用或停用AI分析周期配置；不影响已经生成的任务")
    public AnalysisTaskScheduleVo setEnabled(@ToolParam(description = "周期配置ID") Long id,
                                             @ToolParam(description = "是否启用") Boolean enabled) {
        return service().setEnabled(id, Boolean.TRUE.equals(enabled));
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_schedule_delete", description = "删除AI分析周期配置；不影响已经生成的任务")
    public Boolean delete(@ToolParam(description = "周期配置ID") Long id) {
        service().delete(id);
        return true;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "analysis_task_schedule_list", description = "分页查询AI分析周期配置")
    public PageRowsVo<AnalysisTaskScheduleVo> list(
            @ToolParam(description = "分页和名称、启用状态过滤参数") AnalysisTaskScheduleSearchDto request) {
        return service().getPageList(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "analysis_task_schedule_view", description = "查看AI分析周期配置详情")
    public AnalysisTaskScheduleVo view(@ToolParam(description = "周期配置ID") Long id) {
        return service().info(id);
    }

    private AnalysisTaskScheduleService service() {
        return serviceProvider.getObject();
    }
}
