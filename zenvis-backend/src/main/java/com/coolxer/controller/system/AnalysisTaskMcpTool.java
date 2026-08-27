package com.coolxer.controller.system;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.system.AnalysisTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP工具服务 - 暴露AI分析任务相关接口为MCP工具
 */
@Service
public class AnalysisTaskMcpTool {

    private final ObjectProvider<AnalysisTaskService> analysisTaskServiceProvider;

    public AnalysisTaskMcpTool(ObjectProvider<AnalysisTaskService> analysisTaskServiceProvider) {
        this.analysisTaskServiceProvider = analysisTaskServiceProvider;
    }

    /**
     * 创建AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_create", description = "创建一个新的AI分析任务，任务创建后在后台按计划时间或优先级排队执行")
    public AnalysisTaskVo create(@ToolParam(description = "AI分析任务参数，包含名称、描述、模型、分析提示词、优先级、可选计划时间、必填审批模式approvalMode(AUTO/MANUAL)和可选的已启用skillIds") AnalysisTaskDto request) {
        return new AnalysisTaskVo(analysisTaskService().create(request, currentUserId()));
    }

    /**
     * 更新AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_update", description = "更新指定AI分析任务；执行中的任务不能更新")
    public Boolean update(@ToolParam(description = "AI分析任务ID") Long id,
                          @ToolParam(description = "AI分析任务参数，包含名称、描述、模型、分析提示词、优先级、可选计划时间、必填审批模式approvalMode(AUTO/MANUAL)和可选skillIds") AnalysisTaskDto request) {
        return analysisTaskService().update(id, request, currentUserId());
    }

    /**
     * 删除AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_delete", description = "删除指定AI分析任务；执行中的任务不能删除")
    public Boolean delete(@ToolParam(description = "AI分析任务ID") Long id) {
        analysisTaskService().delete(id, currentUserId());
        return true;
    }

    /**
     * 批量删除AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_bulk_delete", description = "批量删除AI分析任务；执行中的任务不能删除")
    public Boolean bulkDelete(@ToolParam(description = "AI分析任务ID列表") List<Long> ids) {
        analysisTaskService().deleteByIds(ids, currentUserId());
        return true;
    }

    /**
     * 分页查询AI分析任务
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "analysis_task_list", description = "分页查询AI分析任务，可按任务名称、状态、模型和审批模式过滤")
    public PageRowsVo<AnalysisTaskVo> list(@ToolParam(description = "查询参数，包含page、perPage、name、status、model和approvalMode") AnalysisTaskSearchDto request) {
        return analysisTaskService().getPageList(request, currentUserId());
    }

    /**
     * 获取AI分析任务详情
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "analysis_task_view", description = "获取指定AI分析任务详情")
    public AnalysisTaskVo view(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().info(id, currentUserId());
    }

    /**
     * 将AI分析任务重新入队
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_enqueue", description = "将指定AI分析任务重新入队，并清空上次执行结果、错误信息和执行时间；执行中的任务不能重新入队")
    public AnalysisTaskVo enqueue(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().enqueue(id, currentUserId());
    }

    /**
     * 取消AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_cancel", description = "取消等待执行、执行中或等待审批的AI分析任务，并终止尚未处理的MCP审批")
    public AnalysisTaskVo cancel(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().cancel(id, currentUserId());
    }

    /**
     * 执行队列中的下一个AI分析任务
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "analysis_task_run_once", description = "将队列中下一个到期AI分析任务提交到后台执行；该调用不等待分析完成")
    public AnalysisTaskVo runOnce() {
        return analysisTaskService().executeNextTask(currentUserId());
    }

    /**
     * 获取AI分析任务队列状态
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "analysis_task_queue_status", description = "获取AI分析任务队列状态，包括执行中、等待审批、到期任务、可用执行槽和挂起容量")
    public AnalysisTaskQueueVo queueStatus() {
        return analysisTaskService().queueStatus(currentUserId());
    }

    private AnalysisTaskService analysisTaskService() {
        return analysisTaskServiceProvider.getObject();
    }

    private Integer currentUserId() {
        McpInvocationContext context = McpInvocationContextHolder.current();
        return context == null ? null : context.requesterUserId();
    }
}
