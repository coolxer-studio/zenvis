package com.coolxer.controller.system;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.system.AnalysisTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

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
    @Tool(name = "analysis_task_create", description = "创建一个新的AI分析任务，任务创建后默认为等待执行状态")
    public AnalysisTaskVo create(@ToolParam(description = "AI分析任务参数，包含名称、描述、模型、分析提示词、优先级和计划执行时间") AnalysisTaskDto request) {
        return new AnalysisTaskVo(analysisTaskService().create(request));
    }

    /**
     * 更新AI分析任务
     */
    @Tool(name = "analysis_task_update", description = "更新指定AI分析任务；执行中的任务不能更新")
    public Boolean update(@ToolParam(description = "AI分析任务ID") Long id,
                          @ToolParam(description = "AI分析任务参数，包含名称、描述、模型、分析提示词、优先级和计划执行时间") AnalysisTaskDto request) {
        return analysisTaskService().update(id, request);
    }

    /**
     * 删除AI分析任务
     */
    @Tool(name = "analysis_task_delete", description = "删除指定AI分析任务；执行中的任务不能删除")
    public Boolean delete(@ToolParam(description = "AI分析任务ID") Long id) {
        analysisTaskService().delete(id);
        return true;
    }

    /**
     * 批量删除AI分析任务
     */
    @Tool(name = "analysis_task_bulk_delete", description = "批量删除AI分析任务；执行中的任务不能删除")
    public Boolean bulkDelete(@ToolParam(description = "AI分析任务ID列表") List<Long> ids) {
        analysisTaskService().deleteByIds(ids);
        return true;
    }

    /**
     * 分页查询AI分析任务
     */
    @Tool(name = "analysis_task_list", description = "分页查询AI分析任务，可按任务名称、状态和模型过滤")
    public PageRowsVo<AnalysisTaskVo> list(@ToolParam(description = "查询参数，包含page、perPage、name、status和model") AnalysisTaskSearchDto request) {
        return analysisTaskService().getPageList(request);
    }

    /**
     * 获取AI分析任务详情
     */
    @Tool(name = "analysis_task_view", description = "获取指定AI分析任务详情")
    public AnalysisTaskVo view(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().info(id);
    }

    /**
     * 将AI分析任务重新入队
     */
    @Tool(name = "analysis_task_enqueue", description = "将指定AI分析任务重新入队，并清空上次执行结果、错误信息和执行时间；执行中的任务不能重新入队")
    public AnalysisTaskVo enqueue(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().enqueue(id);
    }

    /**
     * 取消AI分析任务
     */
    @Tool(name = "analysis_task_cancel", description = "取消指定AI分析任务；执行中的任务不能取消")
    public AnalysisTaskVo cancel(@ToolParam(description = "AI分析任务ID") Long id) {
        return analysisTaskService().cancel(id);
    }

    /**
     * 执行队列中的下一个AI分析任务
     */
    @Tool(name = "analysis_task_run_once", description = "立即触发执行队列中的下一个到期AI分析任务；如果没有可执行任务或已有任务执行中则返回空")
    public AnalysisTaskVo runOnce() {
        return analysisTaskService().executeNextTask();
    }

    /**
     * 获取AI分析任务队列状态
     */
    @Tool(name = "analysis_task_queue_status", description = "获取AI分析任务队列状态，包括当前执行任务、下一个等待任务、等待数、到期可执行数和执行中数量")
    public AnalysisTaskQueueVo queueStatus() {
        return analysisTaskService().queueStatus();
    }

    private AnalysisTaskService analysisTaskService() {
        return analysisTaskServiceProvider.getObject();
    }
}
