package com.coolxer.service;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.ValidationRequest;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.model.vo.TaskVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vectum MCP 任务工具。
 */
@Service
public class McpTaskTools {

    private final TaskService taskService;
    private final TaskValidationService taskValidationService;
    private final ObjectMapper objectMapper;

    public McpTaskTools(TaskService taskService,
                        TaskValidationService taskValidationService,
                        ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.taskValidationService = taskValidationService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "分页查询任务摘要，可按来源、备注、名称和状态筛选")
    public String listTasks(
            @ToolParam(description = "任务来源，可选 SYSTEM、MCP、API、WEB") String source,
            @ToolParam(description = "来源备注，精确匹配") String mark,
            @ToolParam(description = "任务名称包含的文本，不区分大小写") String nameContains,
            @ToolParam(description = "任务状态，例如 running、stopped、error") String status,
            @ToolParam(description = "偏移量，默认 0") Integer offset,
            @ToolParam(description = "每页数量，默认 20，最大 100") Integer limit) {
        TaskQueryDto query = new TaskQueryDto();
        query.setSource(source);
        query.setMark(mark);
        query.setNameContains(nameContains);
        query.setStatus(status);
        query.setOffset(offset);
        query.setLimit(limit);
        TaskPageVo page = taskService.query(query);
        return toJson(page);
    }

    @Tool(description = "根据任务 ID 获取完整任务详情，包括配置和当前运行轮次")
    public String getTask(@ToolParam(description = "任务 ID") String id) {
        return toJson(requireTask(parseId(id)));
    }

    @Tool(description = "创建任务，格式必须显式指定为 yaml、toml 或 json")
    public String createTask(
            @ToolParam(description = "任务名称") String name,
            @ToolParam(description = "任务描述") String description,
            @ToolParam(description = "完整 Vector 配置") String config,
            @ToolParam(description = "配置格式：yaml、toml 或 json") String format) {
        requireText(name, "任务名称不能为空");
        requireText(config, "任务配置不能为空");
        requireText(format, "配置格式不能为空");
        TaskDto taskDto = new TaskDto();
        taskDto.setName(name);
        taskDto.setDescription(description);
        taskDto.setConfig(config);
        taskDto.setFormat(format);
        taskDto.setSource(TaskSourceEnum.MCP.name());
        return toJson(taskService.create(taskDto));
    }

    @Tool(description = "更新任务名称、描述、配置和格式，保留原来源与备注")
    public String updateTask(
            @ToolParam(description = "任务 ID") String id,
            @ToolParam(description = "任务名称") String name,
            @ToolParam(description = "任务描述") String description,
            @ToolParam(description = "完整 Vector 配置") String config,
            @ToolParam(description = "配置格式：yaml、toml 或 json") String format) {
        Long taskId = parseId(id);
        requireTask(taskId);
        requireText(name, "任务名称不能为空");
        requireText(config, "任务配置不能为空");
        requireText(format, "配置格式不能为空");
        TaskDto taskDto = new TaskDto();
        taskDto.setName(name);
        taskDto.setDescription(description);
        taskDto.setConfig(config);
        taskDto.setFormat(format);
        taskService.update(taskId, taskDto);
        return toJson(requireTask(taskId));
    }

    @Tool(description = "按唯一任务 ID 删除任务")
    public String deleteTask(@ToolParam(description = "任务 ID") String id) {
        Long taskId = parseId(id);
        requireTask(taskId);
        taskService.delete(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "delete");
        result.put("taskId", taskId);
        result.put("deleted", true);
        return toJson(result);
    }

    @Tool(description = "启动任务；已运行时保持现状")
    public String startTask(@ToolParam(description = "任务 ID") String id) {
        return toJson(taskService.start(parseId(id)));
    }

    @Tool(description = "停止任务；已停止时保持现状")
    public String stopTask(@ToolParam(description = "任务 ID") String id) {
        return toJson(taskService.stop(parseId(id)));
    }

    @Tool(description = "重启任务并生成新的运行轮次")
    public String restartTask(@ToolParam(description = "任务 ID") String id) {
        return toJson(taskService.restart(parseId(id)));
    }

    @Tool(description = "读取任务日志，日志类型支持 console 或 system")
    public String getTaskLog(
            @ToolParam(description = "任务 ID") String id,
            @ToolParam(description = "日志类型：console 或 system") String logType) {
        Long taskId = parseId(id);
        TaskVo task = requireTask(taskId);
        String normalized = normalizeLogType(logType);
        String content = taskService.log(taskId, normalized);
        if (content == null) {
            throw new IllegalStateException("获取任务日志失败: taskId=" + taskId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("taskStatus", task.getStatus());
        result.put("currentRunId", task.getCurrentRunId());
        result.put("runStartedAt", task.getRunStartedAt());
        result.put("logType", normalized);
        result.put("content", content);
        return toJson(result);
    }

    @Tool(description = "诊断任务当前 runId 的进程状态和本轮日志")
    public String diagnoseTask(@ToolParam(description = "任务 ID") String id) {
        Long taskId = parseId(id);
        requireTask(taskId);
        return toJson(taskService.diagnostics(taskId));
    }

    @Tool(description = "在隔离环境执行 Vector 原生配置校验；格式必须显式指定")
    public String validateConfig(
            @ToolParam(description = "完整 Vector 配置") String config,
            @ToolParam(description = "配置格式：yaml、toml 或 json") String format,
            @ToolParam(description = "是否要求配置包含测试；缺省 false") Boolean testsRequired) {
        requireText(config, "配置不能为空");
        requireText(format, "配置格式不能为空");
        ValidationRequest request = new ValidationRequest();
        request.setConfig(config);
        request.setFormat(format);
        request.setTestsRequired(Boolean.TRUE.equals(testsRequired));
        ConfigValidationResult result = taskValidationService.validate(request);
        return toJson(result);
    }

    private TaskVo requireTask(Long id) {
        TaskVo task = taskService.info(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return task;
    }

    private Long parseId(String id) {
        requireText(id, "缺少参数: id");
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的任务ID: " + id, e);
        }
    }

    private String normalizeLogType(String logType) {
        if (!StringUtils.hasText(logType)) {
            throw new IllegalArgumentException("缺少参数: logType");
        }
        String normalized = logType.trim().toLowerCase();
        if (!"console".equals(normalized) && !"system".equals(normalized)) {
            throw new IllegalArgumentException("日志类型仅支持 console 或 system");
        }
        return normalized;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化任务结果失败", e);
        }
    }
}
