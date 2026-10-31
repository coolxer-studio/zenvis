package com.coolxer.service.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.TaskRepository;
import com.coolxer.model.RunMetadata;
import com.coolxer.model.Task;
import com.coolxer.model.diagnostic.RuntimeDiagnostic;
import com.coolxer.model.validation.ValidationDiagnostic;
import com.coolxer.service.TaskDiagnosticsService;
import com.coolxer.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按当前 runId 分析启动日志并提供可修复性判断。
 */
@Slf4j
@Service
public class TaskDiagnosticsServiceImpl implements TaskDiagnosticsService {

    private static final int MAX_LOG_EXCERPT = 8_000;
    private static final int STARTUP_GRACE_SECONDS = 5;
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_-]?key|access[_-]?key)"
                    + "(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)(bearer\\s+)([^\\s,;]+)");
    private static final Pattern URI_USERINFO = Pattern.compile(
            "(https?://)([^/@\\s]+)@");
    private static final Pattern LOG_ERROR_LEVEL = Pattern.compile(
            "(?im)^\\s*(?:\\[[^\\]]+\\]\\s*)?"
                    + "(?:\\d{4}-\\d{2}-\\d{2}[T\\s][^\\s]+\\s+)?"
                    + "(?:\\[)?(?:ERROR|FATAL|PANIC)(?:\\]|[:\\s])");
    private static final Pattern LOG_VRL_OR_PANIC = Pattern.compile(
            "(?im)^\\s*(?:.*?\\s)?(?:error\\[|panicked at\\b)");
    private static final Pattern STRUCTURED_ERROR_LEVEL = Pattern.compile(
            "(?i)(?:^|\\s)level=(?:error|fatal|panic)\\b");

    private final TaskRepository taskRepository;
    private final VectorService vectorService;
    private final String taskWorkspace;

    public TaskDiagnosticsServiceImpl(
            TaskRepository taskRepository,
            VectorService vectorService,
            @Value("${task.workspace:${vector.home}/workspace}") String taskWorkspace) {
        this.taskRepository = taskRepository;
        this.vectorService = vectorService;
        this.taskWorkspace = taskWorkspace;
    }

    /**
     * 生成当前运行轮次诊断。
     *
     * @param taskId 任务 ID
     * @return 结构化诊断
     */
    @Override
    public RuntimeDiagnostic diagnose(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.TASK_NOT_FOUND));
        String taskPath = String.valueOf(taskId);
        RunMetadata persistedRun = vectorService.runMetadata(taskPath);
        String runId = firstNonBlank(persistedRun.getRunId(), task.getCurrentRunId());
        String runStartedAt = firstNonBlank(persistedRun.getStartedAt(), task.getRunStartedAt());
        String taskStatus = vectorService.status(taskPath);
        String stdout = currentRunLog(taskPath, "info.log", runId);
        String stderr = currentRunLog(taskPath, "error.log", runId);
        String combined = (stdout + "\n" + stderr).trim();

        RuntimeDiagnostic.RuntimeDiagnosticBuilder builder = RuntimeDiagnostic.builder()
                .runId(runId)
                .runStartedAt(runStartedAt)
                .taskStatus(taskStatus)
                .logExcerpt(sanitize(abbreviate(combined, MAX_LOG_EXCERPT)));

        if (runId == null || runId.isBlank()) {
            return builder
                    .verdict("UNAVAILABLE")
                    .failureStage("RUN_METADATA_MISSING")
                    .repairable(false)
                    .diagnostics(List.of(diagnostic(
                            "VECTOR_RUN_METADATA_MISSING", "ERROR", "PROCESS",
                            "$run", "当前任务没有可识别的本轮 runId。",
                            "重新启动任务以生成新的 runId 和启动标记；不得复用历史日志判断成功。")))
                    .build();
        }
        if (!hasCurrentRunMarker(taskPath, "info.log", runId)
                && !hasCurrentRunMarker(taskPath, "error.log", runId)) {
            return builder
                    .verdict("UNAVAILABLE")
                    .failureStage("CURRENT_RUN_LOG_MISSING")
                    .repairable(false)
                    .diagnostics(List.of(diagnostic(
                            "VECTOR_RUN_LOG_MISSING", "ERROR", "PROCESS",
                            "$logs", "未找到当前 runId 对应的启动日志标记。",
                            "确认本轮启动已完成；不得使用历史日志判断成功。")))
                    .build();
        }

        if (!hasErrors(combined)) {
            if ("running".equals(taskStatus) || "running[error]".equals(taskStatus)) {
                boolean starting = isStarting(runStartedAt);
                return builder
                        .verdict(starting ? "STARTING" : "RUNNING")
                        .failureStage(starting ? "STARTING" : "NONE")
                        .repairable(false)
                        .diagnostics(new ArrayList<>())
                        .build();
            }
            if ("stopped".equals(taskStatus)) {
                return builder
                        .verdict("STOPPED")
                        .failureStage("PROCESS_STOPPED")
                        .repairable(false)
                        .diagnostics(List.of(diagnostic(
                                "VECTOR_PROCESS_STOPPED", "WARNING", "PROCESS",
                                "$process", "任务当前未运行。",
                                "如需继续采集，请启动任务并重新检查本轮日志。")))
                        .build();
            }
        }

        List<ValidationDiagnostic> diagnostics = classify(combined);
        boolean externalBlocked = diagnostics.stream()
                .anyMatch(item -> item.getCode().startsWith("EXTERNAL_"));
        String failureStage;
        if (externalBlocked) {
            failureStage = "EXTERNAL_DEPENDENCY";
        } else if (containsVrlError(combined)) {
            failureStage = "VRL_ERROR";
        } else if (containsConfigError(combined)) {
            failureStage = "CONFIG_ERROR";
        } else {
            failureStage = "PROCESS_CRASH";
        }
        return builder
                .verdict(hasErrors(combined) ? "FAILED" : "CRASHED")
                .failureStage(failureStage)
                .repairable(!externalBlocked)
                .diagnostics(diagnostics)
                .build();
    }

    private List<ValidationDiagnostic> classify(String logText) {
        String normalized = logText == null ? "" : logText.toLowerCase(Locale.ROOT);
        List<ValidationDiagnostic> diagnostics = new ArrayList<>();
        if (containsAny(normalized, "connection refused", "connection reset",
                "no route to host", "connect error")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_CONNECTION_REFUSED", "ERROR", "RUNTIME",
                    "$network", "Vector 无法连接上游或目标服务。",
                    "确认目标地址、端口、网络策略和服务健康状态；不要继续修改配置。"));
        }
        if (containsAny(normalized, "authentication failed", "unauthorized",
                "invalid credentials", "access denied", "401", "403")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_AUTHENTICATION", "ERROR", "RUNTIME",
                    "$credentials", "外部服务认证失败或权限不足。",
                    "检查凭据、数据库用户权限和密钥注入；不要继续修改配置。"));
        }
        if (containsAny(normalized, "dns error", "name or service not known",
                "failed to lookup address", "no such host")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_DNS", "ERROR", "RUNTIME",
                    "$network", "外部服务域名无法解析。",
                    "修正 DNS 或服务发现配置；不要继续修改配置。"));
        }
        if (containsAny(normalized, "tls", "certificate", "handshake")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_TLS", "ERROR", "RUNTIME",
                    "$network", "TLS 握手或证书校验失败。",
                    "检查 CA、证书有效期和主机名；不要继续修改配置。"));
        }
        if (containsAny(normalized, "permission denied", "operation not permitted",
                "access is denied")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_PERMISSION", "ERROR", "RUNTIME",
                    "$filesystem", "Vector 对目标目录或文件没有权限。",
                    "修复挂载权限或运行用户权限；不要继续修改配置。"));
        }
        if (containsAny(normalized, "no such file or directory", "not a directory",
                "directory not found", "mount")) {
            diagnostics.add(diagnostic(
                    "EXTERNAL_PATH_MISSING", "ERROR", "RUNTIME",
                    "$filesystem", "配置依赖的目录、证书或数据文件不存在。",
                    "补齐挂载或文件后重启；不要继续修改配置。"));
        }
        if (containsVrlError(logText)) {
            diagnostics.add(diagnostic(
                    "RUNTIME_VRL_ERROR", "ERROR", "VECTOR",
                    "$transforms", "Transform VRL 在运行时编译或执行失败。",
                    "按日志行修复 VRL，并补充对应合法/异常单元测试。"));
        } else if (containsConfigError(logText)) {
            diagnostics.add(diagnostic(
                    "RUNTIME_CONFIG_ERROR", "ERROR", "VECTOR",
                    "$", "Vector 拒绝了当前配置。",
                    "按错误字段修复完整配置，提交前重新执行原生 validate 和 test。"));
        }
        if (diagnostics.isEmpty()) {
            diagnostics.add(diagnostic(
                    "RUNTIME_PROCESS_FAILURE", "ERROR", "PROCESS",
                    "$process", "Vector 进程已退出或本轮日志包含 ERROR/FATAL/PANIC。",
                    "结合日志证据修复；若无法定位则停止并报告真实错误。"));
        }
        return diagnostics;
    }

    private boolean containsVrlError(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return normalized.contains("vrl") || normalized.contains("error[e")
                || normalized.contains("failed to compile");
    }

    private boolean containsConfigError(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "unknown field", "invalid type",
                "failed to load", "configuration error", "invalid uri");
    }

    private boolean hasErrors(String logText) {
        if (logText == null || logText.isBlank()) {
            return false;
        }
        return LOG_ERROR_LEVEL.matcher(logText).find()
                || LOG_VRL_OR_PANIC.matcher(logText).find()
                || STRUCTURED_ERROR_LEVEL.matcher(logText).find();
    }

    private boolean isStarting(String startedAt) {
        if (startedAt == null || startedAt.isBlank()) {
            return true;
        }
        try {
            return Duration.between(Instant.parse(startedAt), Instant.now()).getSeconds()
                    < STARTUP_GRACE_SECONDS;
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    private String currentRunLog(String taskPath, String logName, String runId) {
        Path logPath = Path.of(taskWorkspace, taskPath, logName);
        if (!Files.exists(logPath)) {
            return "";
        }
        try {
            String content = Files.readString(logPath, StandardCharsets.UTF_8);
            if (runId == null || runId.isBlank()) {
                return content;
            }
            String marker = "=== Vectum run " + runId;
            int markerIndex = content.lastIndexOf(marker);
            return markerIndex >= 0 ? content.substring(markerIndex) : "";
        } catch (IOException e) {
            log.warn("Failed to read current run log: task={}, file={}, error={}",
                    taskPath, logName, e.getMessage());
            return "";
        }
    }

    private boolean hasCurrentRunMarker(String taskPath, String logName, String runId) {
        Path logPath = Path.of(taskWorkspace, taskPath, logName);
        if (!Files.exists(logPath) || runId == null || runId.isBlank()) {
            return false;
        }
        try {
            return Files.readString(logPath, StandardCharsets.UTF_8)
                    .contains("=== Vectum run " + runId);
        } catch (IOException e) {
            return false;
        }
    }

    private ValidationDiagnostic diagnostic(String code,
                                            String severity,
                                            String source,
                                            String path,
                                            String message,
                                            String repairHint) {
        return ValidationDiagnostic.builder()
                .code(code)
                .severity(severity)
                .source(source)
                .path(path)
                .message(message)
                .repairHint(repairHint)
                .build();
    }

    private String sanitize(String text) {
        String result = SECRET_VALUE.matcher(text == null ? "" : text)
                .replaceAll("$1$2***");
        result = BEARER_VALUE.matcher(result).replaceAll("$1***");
        Matcher uriMatcher = URI_USERINFO.matcher(result);
        return uriMatcher.replaceAll("$1***@");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String abbreviate(String value, int limit) {
        String text = value == null ? "" : value;
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }
}
