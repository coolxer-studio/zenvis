package com.coolxer.service;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.vo.TaskVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP任务工具服务
 * <p>
 * 提供任务管理相关的MCP工具方法
 * </p>
 */
@Service
public class McpTaskTools {

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public McpTaskTools(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "获取所有任务列表，返回任务的基本信息和状态")
    public String getTasks() {
        List<TaskVo> tasks = taskService.findAll();
        return toJson(tasks);
    }

    @Tool(description = "根据任务ID获取任务详情信息")
    public String getTask(
            @ToolParam(description = "任务ID") String id) {
        if (id == null || id.isEmpty()) {
            return error("缺少参数: id");
        }
        try {
            Long taskId = Long.parseLong(id);
            TaskVo task = taskService.info(taskId);
            if (task == null) {
                return error("任务不存在: " + id);
            }
            return toJson(task);
        } catch (NumberFormatException e) {
            return error("无效的任务ID: " + id);
        }
    }

    @Tool(description = "创建新的推送任务")
    public String createTask(
            @ToolParam(description = "任务名称") String name,
            @ToolParam(description = "任务描述") String description,
            @ToolParam(description = "任务配置（YAML或JSON格式）") String config) {
        if (name == null || name.isEmpty()) {
            return error("缺少参数: name");
        }
        if (config == null || config.isEmpty()) {
            return error("缺少参数: config");
        }

        TaskDto taskDto = new TaskDto();
        taskDto.setName(name);
        taskDto.setDescription(description);
        taskDto.setConfig(config);
        taskDto.setSource(TaskSourceEnum.MCP.name());

        try {
            TaskVo task = taskService.create(taskDto);
            return toJson(task);
        } catch (Exception e) {
            return error("创建任务失败: " + e.getMessage());
        }
    }

    @Tool(description = "更新指定任务的信息")
    public String updateTask(
            @ToolParam(description = "任务ID") String id,
            @ToolParam(description = "任务名称") String name,
            @ToolParam(description = "任务描述") String description,
            @ToolParam(description = "任务配置（YAML或JSON格式）") String config) {
        if (id == null || id.isEmpty()) {
            return error("缺少参数: id");
        }

        try {
            Long taskId = Long.parseLong(id);
            TaskDto taskDto = new TaskDto();
            if (name != null && !name.isEmpty()) {
                taskDto.setName(name);
            }
            if (description != null) {
                taskDto.setDescription(description);
            }
            if (config != null && !config.isEmpty()) {
                taskDto.setConfig(config);
            }
            taskDto.setSource(TaskSourceEnum.MCP.name());
            Boolean result = taskService.update(taskId, taskDto);
            if (result) {
                return success("更新成功");
            } else {
                return error("更新失败，任务不存在");
            }
        } catch (NumberFormatException e) {
            return error("无效的任务ID: " + id);
        } catch (Exception e) {
            return error("更新任务失败: " + e.getMessage());
        }
    }

    @Tool(description = "删除指定的任务")
    public String deleteTask(
            @ToolParam(description = "任务ID") String id) {
        if (id == null || id.isEmpty()) {
            return error("缺少参数: id");
        }

        try {
            Long taskId = Long.parseLong(id);
            taskService.delete(taskId);
            return success("删除成功");
        } catch (NumberFormatException e) {
            return error("无效的任务ID: " + id);
        } catch (Exception e) {
            return error("删除任务失败: " + e.getMessage());
        }
    }

    @Tool(description = "启动或停止指定任务，切换任务的运行状态")
    public String toggleTask(
            @ToolParam(description = "任务ID") String id) {
        if (id == null || id.isEmpty()) {
            return error("缺少参数: id");
        }

        try {
            Long taskId = Long.parseLong(id);
            Boolean result = taskService.toggle(taskId);
            if (result) {
                return success("操作成功");
            } else {
                return error("操作失败，任务不存在或无法切换状态");
            }
        } catch (NumberFormatException e) {
            return error("无效的任务ID: " + id);
        } catch (Exception e) {
            return error("切换任务状态失败: " + e.getMessage());
        }
    }

    @Tool(description = "获取任务日志")
    public String getTaskLog(
            @ToolParam(description = "任务ID") String id,
            @ToolParam(description = "日志类型（console或system）") String logType) {
        if (id == null || id.isEmpty()) {
            return error("缺少参数: id");
        }
        if (logType == null || logType.isEmpty()) {
            return error("缺少参数: logType");
        }

        try {
            Long taskId = Long.parseLong(id);
            String log = taskService.log(taskId, logType);
            if (log == null) {
                return error("获取日志失败");
            }
            return log;
        } catch (NumberFormatException e) {
            return error("无效的任务ID: " + id);
        } catch (Exception e) {
            return error("获取日志失败: " + e.getMessage());
        }
    }

    @Tool(description = "批量删除多个任务")
    public String batchDeleteTasks(
            @ToolParam(description = "任务ID列表，多个ID用逗号分隔") String ids) {
        if (ids == null || ids.isEmpty()) {
            return error("缺少参数: ids");
        }

        try {
            List<Long> idList = java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            taskService.deleteByIds(idList);
            return success("批量删除成功，共删除 " + idList.size() + " 个任务");
        } catch (NumberFormatException e) {
            return error("无效的任务ID格式");
        } catch (Exception e) {
            return error("批量删除失败: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return error("序列化失败: " + e.getMessage());
        }
    }

    private String success(String message) {
        return "{\"success\":true,\"message\":\"" + message + "\"}";
    }

    private String error(String message) {
        return "{\"success\":false,\"error\":\"" + message + "\"}";
    }
}