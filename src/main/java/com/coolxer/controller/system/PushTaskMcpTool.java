package com.coolxer.controller.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP工具服务 - 暴露数据推送任务相关接口为MCP工具
 */
@Service
public class PushTaskMcpTool {

    private final PushTaskService pushTaskService;

    public PushTaskMcpTool(PushTaskService pushTaskService) {
        this.pushTaskService = pushTaskService;
    }

    @Tool(name = "push_task_create_and_start", description = "创建并启动数据推送任务，用于持续分析任务的数据匹配和推送")
    public Boolean createAndStart(@ToolParam(description = "数据推送任务参数，包含任务名、描述、配置、来源和备注") PushTaskDto request) {
        return pushTaskService.createAndStart(request);
    }

    @Tool(name = "push_task_list_by_source_mark", description = "按来源备注查询系统创建的数据推送任务，用于检测持续分析任务是否冲突")
    public List<PushTaskVo> listBySourceMark(@ToolParam(description = "数据推送任务备注标识") String sourceMark) {
        return pushTaskService.findBySourceMark(sourceMark);
    }

    @Tool(name = "push_task_delete_by_source_mark", description = "按来源备注删除系统创建的数据推送任务")
    public Boolean deleteBySourceMark(@ToolParam(description = "数据推送任务备注标识") String sourceMark) {
        return pushTaskService.deleteBySourceMark(sourceMark);
    }

    @Tool(name = "push_task_detect_format", description = "检测数据推送配置文本格式，返回yaml、toml或json")
    public String detectFormat(@ToolParam(description = "数据推送配置文本") String content) {
        return pushTaskService.detectFormat(content);
    }
}
