package com.coolxer.controller.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.dto.PushTaskQueryDto;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticResultVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.mcp.McpPreExecutionException;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.PushTaskConfigException;
import com.coolxer.service.system.PushTaskConfigFormatDetector;
import com.coolxer.service.system.PushTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * 数据推送任务 MCP 工具。
 */
@Service
public class PushTaskMcpTool {

    private static final int DEFAULT_LOG_LINES = 100;
    private static final int MAX_LOG_LINES = 1_000;
    private static final int MAX_LOG_CHARS = 6_000;

    private final PushTaskService pushTaskService;
    private final ConfigService configService;
    private final Object createLock = new Object();

    public PushTaskMcpTool(PushTaskService pushTaskService,
                           ConfigService configService) {
        this.pushTaskService = pushTaskService;
        this.configService = configService;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_create_and_start",
            description = "创建并启动数据推送任务，返回真实 taskId、状态和 runId；按 SYSTEM+mark 幂等复用")
    public PushTaskOperationVo createAndStart(
            @ToolParam(description = "完整任务参数，必须包含任务名、描述、配置、format、source=SYSTEM 和 mark")
            PushTaskDto request) {
        validateCreateRequest(request);
        try {
            synchronized (createLock) {
                return createOrReuse(request);
            }
        } catch (PushTaskConfigException e) {
            throw new McpPreExecutionException(e.getMessage(), e);
        }
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_list",
            description = "分页查询所有来源的数据推送任务摘要，可按来源、备注、名称和状态筛选")
    public PushTaskPageVo list(
            @ToolParam(description = "来源备注，精确匹配，可选") String sourceMark,
            @ToolParam(description = "任务名称包含的文本，可选") String nameContains,
            @ToolParam(description = "任务来源 SYSTEM/MCP/API/WEB，可选") String source,
            @ToolParam(description = "任务状态，例如 running、stopped、error，可选") String status,
            @ToolParam(description = "偏移量，默认 0") Integer offset,
            @ToolParam(description = "每页数量，默认 20，最大 100") Integer limit) {
        PushTaskQueryDto query = new PushTaskQueryDto();
        query.setSourceMark(sourceMark);
        query.setNameContains(nameContains);
        query.setSource(source);
        query.setStatus(status);
        query.setOffset(offset == null ? 0 : Math.max(offset, 0));
        query.setLimit(limit == null ? 20 : Math.max(1, Math.min(limit, 100)));
        return pushTaskService.query(query);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_get", description = "按任务 ID 获取完整详情，包括配置、格式和当前运行轮次")
    public PushTaskVo get(@ToolParam(description = "数据推送任务 ID") Integer taskId) {
        return requireTask(taskId);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_get_log", description = "读取任务最新运行日志，返回脱敏内容和当前 runId")
    public Map<String, Object> getLog(
            @ToolParam(description = "数据推送任务 ID") Integer taskId,
            @ToolParam(description = "日志类型，仅支持 console 或 system") String logType,
            @ToolParam(description = "返回末尾行数，默认 100，最大 1000") Integer tailLines) {
        PushTaskVo task = requireTask(taskId);
        String normalizedLogType = normalizeLogType(logType);
        String rawLog = pushTaskService.getLog(taskId, normalizedLogType);
        if (rawLog == null) {
            throw new IllegalStateException("Vectum 未返回任务日志");
        }
        return boundedLogResult(task, normalizedLogType, rawLog, tailLines);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_update_and_restart",
            description = "更新任务名称、描述、完整配置和格式并重启；原 source 与 mark 保持不变")
    public PushTaskOperationVo updateAndRestart(
            @ToolParam(description = "数据推送任务 ID") Integer taskId,
            @ToolParam(description = "完整任务参数，必须包含名称、描述、配置和显式 format") PushTaskDto request) {
        PushTaskVo current = requireTask(taskId);
        validateUpdateRequest(request, current);
        try {
            request.setSource(null);
            request.setMark(null);
            return pushTaskService.updateAndRestartTask(taskId, request);
        } catch (PushTaskConfigException e) {
            throw new McpPreExecutionException(e.getMessage(), e);
        }
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_start", description = "启动任务；已运行时保持现状")
    public PushTaskOperationVo start(@ToolParam(description = "数据推送任务 ID") Integer taskId) {
        requireTask(taskId);
        return pushTaskService.startTask(taskId);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_stop", description = "停止任务；已停止时保持现状，并保留最后一轮 runId")
    public PushTaskOperationVo stop(@ToolParam(description = "数据推送任务 ID") Integer taskId) {
        requireTask(taskId);
        return pushTaskService.stopTask(taskId);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_restart", description = "重启任务并生成新的 runId")
    public PushTaskOperationVo restart(@ToolParam(description = "数据推送任务 ID") Integer taskId) {
        requireTask(taskId);
        return pushTaskService.restartTask(taskId);
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "push_task_delete", description = "按唯一任务 ID 删除任务，不提供备注或批量删除")
    public PushTaskOperationVo delete(@ToolParam(description = "数据推送任务 ID") Integer taskId) {
        requireTask(taskId);
        return pushTaskService.deleteTask(taskId);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_validate_config",
            description = "只读校验完整配置；format 必填，执行拓扑、Meta、Vector validate 和 test")
    public PushTaskValidationVo validateConfig(
            @ToolParam(description = "完整 Vector 配置文本") String config,
            @ToolParam(description = "配置格式 yaml、toml 或 json，必填") String format,
            @ToolParam(description = "已发布 Meta 文件名；纯 source→sink 原始转发可留空") String metaFileName) {
        String normalizedFormat = requireFormat(format, config);
        PushTaskValidationRequest request = new PushTaskValidationRequest();
        request.setConfig(config);
        request.setFormat(normalizedFormat);
        request.setMetaFileName(metaFileName);
        request.setMetaContent(readPublishedMeta(metaFileName));
        return pushTaskService.validateConfig(request);
    }

    private String readPublishedMeta(String metaFileName) {
        if (!StringUtils.hasText(metaFileName)) {
            return "";
        }
        if (!configService.fileExistsInConfigPath("meta", metaFileName)) {
            throw new McpPreExecutionException(
                    "已发布 Meta 不存在或不可读取: " + metaFileName);
        }
        String content = configService.readFile("meta", metaFileName);
        if (!StringUtils.hasText(content)) {
            throw new McpPreExecutionException(
                    "已发布 Meta 内容为空: " + metaFileName);
        }
        return content;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "push_task_diagnose",
            description = "只读诊断任务当前 runId 的状态和日志，区分配置、VRL、进程和外部依赖阻塞")
    public PushTaskDiagnosticResultVo diagnose(
            @ToolParam(description = "数据推送任务 ID") Integer taskId) {
        requireTask(taskId);
        return pushTaskService.diagnose(taskId);
    }

    private PushTaskOperationVo createOrReuse(PushTaskDto request) {
        PushTaskPageVo page;
        try {
            PushTaskQueryDto query = new PushTaskQueryDto();
            query.setSource("SYSTEM");
            query.setSourceMark(request.getMark());
            query.setOffset(0);
            query.setLimit(100);
            page = pushTaskService.query(query);
        } catch (RuntimeException e) {
            throw new McpPreExecutionException("查询 sourceMark 现有任务失败，尚未创建任务", e);
        }
        List<com.coolxer.model.system.vo.PushTaskSummaryVo> items = page == null
                || page.getItems() == null ? List.of() : page.getItems();
        if (items.size() > 1 || page != null && page.isHasMore()) {
            throw new McpPreExecutionException(
                    "sourceMark=" + request.getMark() + " 已存在多个任务，拒绝继续创建");
        }
        if (items.isEmpty()) {
            return pushTaskService.createAndStartTask(request);
        }
        PushTaskVo task = pushTaskService.findById(items.get(0).getId());
        if (task == null) {
            throw new McpPreExecutionException("sourceMark 已存在但无法读取完整任务");
        }
        String persistedFormat = PushTaskConfigFormatDetector.detect(
                task.getFormat(), task.getConfig()).format();
        if ((StringUtils.hasText(task.getConfig())
                && !task.getConfig().trim().equals(request.getConfig().trim()))
                || !Objects.equals(persistedFormat, request.getFormat())) {
            throw new McpPreExecutionException(
                    "sourceMark 已存在但持久化配置或格式与本次候选不同，必须走更新流程");
        }
        String status = Objects.toString(task.getStatus(), "").toLowerCase(Locale.ROOT);
        if (status.startsWith("running")) {
            return operation("create_and_start", task, status, false);
        }
        return pushTaskService.startTask(task.getId());
    }

    private PushTaskOperationVo operation(String action, PushTaskVo task,
                                          String previousStatus, boolean changed) {
        return PushTaskOperationVo.builder()
                .action(action)
                .taskId(task.getId())
                .previousStatus(previousStatus)
                .status(task.getStatus())
                .changed(changed)
                .pid(task.getPid())
                .currentRunId(task.getCurrentRunId())
                .runStartedAt(task.getRunStartedAt())
                .build();
    }

    private PushTaskVo requireTask(Integer taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("数据推送任务 ID 不能为空");
        }
        PushTaskVo task = pushTaskService.findById(taskId);
        if (task == null || !Objects.equals(taskId, task.getId())) {
            throw new IllegalArgumentException("数据推送任务不存在: " + taskId);
        }
        return task;
    }

    private void validateCreateRequest(PushTaskDto request) {
        if (request == null) {
            throw new McpPreExecutionException("完整任务参数不能为空");
        }
        if (!"SYSTEM".equals(request.getSource())) {
            throw new McpPreExecutionException("智能体创建任务的 source 必须为 SYSTEM");
        }
        if (!StringUtils.hasText(request.getMark())) {
            throw new McpPreExecutionException("任务 mark 不能为空");
        }
        validateCommonRequest(request);
    }

    private void validateUpdateRequest(PushTaskDto request, PushTaskVo current) {
        if (request == null) {
            throw new McpPreExecutionException("完整任务参数不能为空");
        }
        if (StringUtils.hasText(request.getSource())
                && !request.getSource().equalsIgnoreCase(current.getSource())) {
            throw new McpPreExecutionException("更新任务时不得改变任务 source");
        }
        if (StringUtils.hasText(request.getMark())
                && !Objects.equals(request.getMark(), current.getMark())) {
            throw new McpPreExecutionException("更新任务时不得改变任务 mark");
        }
        validateCommonRequest(request);
    }

    private void validateCommonRequest(PushTaskDto request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new McpPreExecutionException("任务名称不能为空");
        }
        if (!StringUtils.hasText(request.getConfig())) {
            throw new McpPreExecutionException("任务完整配置不能为空");
        }
        if (!StringUtils.hasText(request.getFormat())) {
            throw new McpPreExecutionException("任务 format 不能为空，必须显式指定 yaml、toml 或 json");
        }
        request.setFormat(requireFormat(request.getFormat(), request.getConfig()));
    }

    private String requireFormat(String format, String config) {
        if (!StringUtils.hasText(format)) {
            throw new McpPreExecutionException("format 不能为空，必须显式指定 yaml、toml 或 json");
        }
        PushTaskConfigFormatDetector.Detection detection =
                PushTaskConfigFormatDetector.detect(format, config);
        if (!StringUtils.hasText(detection.format())) {
            throw new McpPreExecutionException(
                    "不支持的配置格式：" + format + "；仅支持 yaml、toml 或 json");
        }
        return detection.format();
    }

    private String normalizeLogType(String logType) {
        String normalized = Objects.toString(logType, "").trim().toLowerCase(Locale.ROOT);
        if (!"console".equals(normalized) && !"system".equals(normalized)) {
            throw new IllegalArgumentException("日志类型仅支持 console 或 system");
        }
        return normalized;
    }

    private Map<String, Object> boundedLogResult(PushTaskVo task,
                                                  String logType,
                                                  String rawLog,
                                                  Integer tailLines) {
        String sanitized = redactSensitiveLog(rawLog);
        List<String> lines = sanitized.lines().toList();
        int requestedLines = tailLines == null ? DEFAULT_LOG_LINES
                : Math.max(1, Math.min(tailLines, MAX_LOG_LINES));
        int fromIndex = Math.max(lines.size() - requestedLines, 0);
        String content = String.join("\n", lines.subList(fromIndex, lines.size()));
        boolean truncated = fromIndex > 0;
        if (content.length() > MAX_LOG_CHARS) {
            content = content.substring(content.length() - MAX_LOG_CHARS);
            truncated = true;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("sourceMark", task.getMark());
        result.put("taskStatus", task.getStatus());
        result.put("format", task.getFormat());
        result.put("currentRunId", task.getCurrentRunId());
        result.put("runStartedAt", task.getRunStartedAt());
        result.put("logType", logType);
        result.put("totalLines", lines.size());
        result.put("returnedLines", content.isEmpty() ? 0 : content.lines().count());
        result.put("truncated", truncated);
        result.put("content", content);
        return result;
    }

    private String redactSensitiveLog(String log) {
        String redacted = log.replaceAll("(?i)\\bBearer\\s+[^\\s,;]+", "Bearer ***");
        redacted = redacted.replaceAll(
                "(?i)([\"']?(?:password|passwd|token|secret|api[_-]?key|access[_-]?key|authorization)"
                        + "[\"']?\\s*[:=]\\s*[\"']?)([^\\s,;\"']+)",
                "$1***"
        );
        return redacted.replaceAll("(?i)(https?://)([^/@\\s]+)@", "$1***@");
    }
}
