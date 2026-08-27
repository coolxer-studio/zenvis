package com.coolxer.controller.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP工具服务 - 暴露数据推送任务相关接口为MCP工具
 */
@Service
public class PushTaskMcpTool {

    private static final int MAX_LOG_LINES = 100;
    private static final int MAX_LOG_CHARS = 6_000;
    private final PushTaskService pushTaskService;

    public PushTaskMcpTool(PushTaskService pushTaskService) {
        this.pushTaskService = pushTaskService;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_create_and_start",
            description = "创建并启动受管数据推送任务；启动后必须查询任务状态和 system 日志")
    public Boolean createAndStart(
            @ToolParam(description = "完整任务参数，必须包含任务名、描述、配置、source=SYSTEM 和 mark")
            PushTaskDto request) {
        validateManagedRequest(request, request == null ? null : request.getMark());
        return pushTaskService.createAndStart(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_list_by_source_mark", description = "按来源备注查询系统创建的数据推送任务，用于检测持续分析任务是否冲突")
    public List<PushTaskVo> listBySourceMark(@ToolParam(description = "数据推送任务备注标识") String sourceMark) {
        return pushTaskService.findBySourceMark(sourceMark);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_delete_by_source_mark", description = "按来源备注删除系统创建的数据推送任务")
    public Boolean deleteBySourceMark(@ToolParam(description = "数据推送任务备注标识") String sourceMark) {
        return pushTaskService.deleteBySourceMark(sourceMark);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_get_log", description = "读取受管数据推送任务的最新运行日志，用于诊断启动或运行失败")
    public Map<String, Object> getLog(
            @ToolParam(description = "数据推送任务 ID") Integer taskId,
            @ToolParam(description = "创建任务时使用的来源备注标识") String sourceMark,
            @ToolParam(description = "日志类型，仅支持 console 或 system") String logType) {
        PushTaskVo task = requireOwnedTask(taskId, sourceMark);
        String normalizedLogType = Objects.toString(logType, "").trim().toLowerCase();
        if (!"console".equals(normalizedLogType) && !"system".equals(normalizedLogType)) {
            throw new IllegalArgumentException("日志类型仅支持 console 或 system");
        }
        String rawLog = pushTaskService.getLog(taskId, normalizedLogType);
        if (rawLog == null) {
            throw new IllegalStateException("Vectum 未返回任务日志");
        }
        return boundedLogResult(task, sourceMark, normalizedLogType, rawLog);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_repair_and_restart",
            description = "更新受管数据推送任务的完整配置并重新启动；调用前必须已向用户展示真实日志证据、具体失败原因及逐项配置旧值/新值")
    public Boolean repairAndRestart(
            @ToolParam(description = "数据推送任务 ID") Integer taskId,
            @ToolParam(description = "创建任务时使用的来源备注标识") String sourceMark,
            @ToolParam(description = "完整任务参数，必须包含名称、配置、source=SYSTEM 和与 sourceMark 相同的 mark；配置必须与已展示的逐项修改清单完全一致")
            PushTaskDto request) {
        requireOwnedTask(taskId, sourceMark);
        validateManagedRequest(request, sourceMark);
        return pushTaskService.updateAndStart(taskId, request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_detect_format", description = "检测数据推送配置文本格式，返回yaml、toml或json")
    public String detectFormat(@ToolParam(description = "数据推送配置文本") String content) {
        return pushTaskService.detectFormat(content);
    }

    private PushTaskVo requireOwnedTask(Integer taskId, String sourceMark) {
        if (taskId == null) {
            throw new IllegalArgumentException("数据推送任务 ID 不能为空");
        }
        if (sourceMark == null || sourceMark.isBlank()) {
            throw new IllegalArgumentException("数据推送任务 sourceMark 不能为空");
        }
        PushTaskVo task = pushTaskService.findById(taskId);
        if (task == null
                || !Objects.equals(taskId, task.getId())
                || !"SYSTEM".equals(task.getSource())
                || !Objects.equals(sourceMark, task.getMark())) {
            throw new IllegalArgumentException("任务 ID 与 sourceMark 不匹配或任务不属于 SYSTEM");
        }
        return task;
    }

    private void validateManagedRequest(PushTaskDto request, String sourceMark) {
        if (request == null) {
            throw new IllegalArgumentException("完整任务参数不能为空");
        }
        if (!"SYSTEM".equals(request.getSource())) {
            throw new IllegalArgumentException("任务的 source 必须为 SYSTEM");
        }
        if (sourceMark == null || sourceMark.isBlank()
                || !Objects.equals(sourceMark, request.getMark())) {
            throw new IllegalArgumentException("任务 mark 必须与 sourceMark 一致且不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (request.getConfig() == null || request.getConfig().isBlank()) {
            throw new IllegalArgumentException("任务完整配置不能为空");
        }
    }

    private Map<String, Object> boundedLogResult(PushTaskVo task,
                                                  String sourceMark,
                                                  String logType,
                                                  String rawLog) {
        String sanitized = redactSensitiveLog(rawLog);
        List<String> lines = sanitized.lines().toList();
        int fromIndex = Math.max(lines.size() - MAX_LOG_LINES, 0);
        String content = String.join("\n", lines.subList(fromIndex, lines.size()));
        boolean truncated = fromIndex > 0;
        if (content.length() > MAX_LOG_CHARS) {
            content = content.substring(content.length() - MAX_LOG_CHARS);
            truncated = true;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("sourceMark", sourceMark);
        result.put("taskStatus", task.getStatus());
        result.put("logType", logType);
        result.put("totalLines", lines.size());
        result.put("returnedLines", content.isEmpty() ? 0 : content.lines().count());
        result.put("truncated", truncated);
        result.put("content", content);
        return result;
    }

    private String redactSensitiveLog(String log) {
        String redacted = log.replaceAll("(?i)\\bBearer\\s+[^\\s,;]+", "Bearer ***");
        return redacted.replaceAll(
                "(?i)([\"']?(?:password|passwd|token|secret|api[_-]?key|access[_-]?key|authorization)"
                        + "[\"']?\\s*[:=]\\s*[\"']?)([^\\s,;\"']+)",
                "$1***"
        );
    }
}
