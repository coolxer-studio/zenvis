package com.coolxer.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.TaskRepository;
import com.coolxer.model.Task;
import com.coolxer.model.RunMetadata;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.diagnostic.RuntimeDiagnostic;
import com.coolxer.model.vo.TaskOperationVo;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.model.vo.TaskSummaryVo;
import com.coolxer.model.vo.TaskVo;
import com.coolxer.service.TaskService;
import com.coolxer.service.TaskDiagnosticsService;
import com.coolxer.service.VectorService;
import com.coolxer.service.ZenvisBusinessService;
import com.coolxer.utils.ConfigFormatDetector;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 任务服务实现类
 * <p>
 * 提供任务的增删改查、启停控制、日志查看等业务功能
 * </p>
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    private final VectorService vectorService;
    private final TaskRepository taskRepository;
    private final ZenvisBusinessService zenvisBusinessService;
    private final TaskDiagnosticsService taskDiagnosticsService;

    public TaskServiceImpl(VectorService vectorService,
                           TaskRepository taskRepository,
                           ZenvisBusinessService zenvisBusinessService,
                           TaskDiagnosticsService taskDiagnosticsService) {
        this.vectorService = vectorService;
        this.taskRepository = taskRepository;
        this.zenvisBusinessService = zenvisBusinessService;
        this.taskDiagnosticsService = taskDiagnosticsService;
    }

    /**
     * 查询全部列表
     * @return 任务视图对象列表
     */
    @Override
    public List<TaskVo> findAll() {
        return taskRepository.findAll().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    @Override
    public TaskPageVo query(TaskQueryDto query) {
        TaskQueryDto safeQuery = query == null ? new TaskQueryDto() : query;
        int offset = safeQuery.getOffset() == null ? 0 : Math.max(safeQuery.getOffset(), 0);
        int limit = safeQuery.getLimit() == null ? 20 : Math.max(1, Math.min(safeQuery.getLimit(), 100));
        List<TaskSummaryVo> matched = findAll().stream()
                .filter(task -> matchesSource(task, safeQuery.getSource()))
                .filter(task -> !hasText(safeQuery.getMark())
                        || Objects.equals(safeQuery.getMark(), task.getMark()))
                .filter(task -> !hasText(safeQuery.getNameContains())
                        || containsIgnoreCase(task.getName(), safeQuery.getNameContains()))
                .filter(task -> matchesStatus(task.getStatus(), safeQuery.getStatus()))
                .sorted(Comparator.comparing(
                                TaskVo::getUpdateTime,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(TaskSummaryVo::new)
                .toList();
        int from = Math.min(offset, matched.size());
        int to = Math.min(from + limit, matched.size());
        return TaskPageVo.builder()
                .items(matched.subList(from, to))
                .total(matched.size())
                .offset(offset)
                .limit(limit)
                .hasMore(to < matched.size())
                .build();
    }

    /**
     * 创建推送任务
     * @param taskDto 传输实体
     * @return 任务视图对象
     */
    @Override
    public TaskVo create(TaskDto taskDto) {
        Task task = new Task();
        task.updateFromDto(taskDto);
        task.setFormat(resolveFormat(null, taskDto.getFormat(), task.getConfig()));
        task.setCurrentRunId(null);
        task.setRunStartedAt(null);
        Task taskSaved = taskRepository.save(task);
        
        String errorInfo = vectorService.createProcess(
                String.valueOf(task.getId()), task.getConfig(), task.getLuaFiles(), task.getFormat());
        if (errorInfo != null) {
            taskRepository.delete(task);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), errorInfo);
        }

        zenvisBusinessService.reportTaskCreated(taskSaved);
        return new TaskVo(taskSaved, "created");
    }

    /**
     * 修改推送任务
     * @param id 推送任务id
     * @param taskDto 用户传输实体
     * @return 是否成功
     */
    @Override
    public Boolean update(Long id, TaskDto taskDto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + id));

        String config = taskDto.getConfig() == null ? task.getConfig() : taskDto.getConfig();
        String format = resolveFormat(null, taskDto.getFormat(), config);
        task.updateFromDto(taskDto);
        task.setFormat(format);
        
        String errorInfo = vectorService.updateProcess(
                String.valueOf(task.getId()), task.getConfig(), task.getLuaFiles(), format);
        if (errorInfo != null) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), errorInfo);
        }
        
        Task taskSaved = taskRepository.save(task);
        zenvisBusinessService.reportTaskUpdated(taskSaved);
        return true;
    }

    /**
     * 删除推送任务
     * @param id 推送任务id
     */
    @Override
    public void delete(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.TASK_NOT_FOUND));
        boolean deleted = vectorService.deleteProcess(String.valueOf(id));
        if (!deleted) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED.getCode(),
                    "删除任务进程失败: taskId=" + id);
        }
        taskRepository.deleteById(task.getId());
        zenvisBusinessService.reportTaskDeleted(id);
    }

    /**
     * 批量删除
     * @param ids 任务ID列表
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        ids.forEach(this::delete);
    }

    /**
     * 推送任务详情
     * @param id 推送任务id
     * @return 任务视图对象
     */
    @Override
    public TaskVo info(Long id) {
        return taskRepository.findById(id)
                .map(this::toVo)
                .orElse(null);
    }

    /**
     * 启动停止任务
     * @param id 任务id
     * @return 是否成功
     */
    @Override
    public Boolean toggle(Long id) {
        Task task = taskRepository.findById(id).orElse(null);
        if (task == null) {
            return false;
        }
        String status = vectorService.status(String.valueOf(task.getId()));
        if (status.startsWith("stopped")) {
            return start(id).isChanged();
        }
        return stop(id).isChanged();
    }

    @Override
    public TaskOperationVo start(Long id) {
        Task task = requireTask(id);
        String previousStatus = vectorService.status(String.valueOf(task.getId()));
        if (previousStatus.startsWith("running")) {
            return operation("start", task, previousStatus, false);
        }
        long processId = vectorService.startProcess(String.valueOf(task.getId()));
        if (processId <= 0) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED.getCode(),
                    "启动任务失败: taskId=" + id);
        }
        task.setPid((int) processId);
        applyRunMetadata(task);
        taskRepository.save(task);
        zenvisBusinessService.reportTaskStarted(task);
        return operation("start", task, previousStatus, true);
    }

    @Override
    public TaskOperationVo stop(Long id) {
        Task task = requireTask(id);
        String previousStatus = vectorService.status(String.valueOf(task.getId()));
        if (previousStatus.startsWith("stopped")) {
            return operation("stop", task, previousStatus, false);
        }
        if (!vectorService.stopProcess(String.valueOf(task.getId()))) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED.getCode(),
                    "停止任务失败: taskId=" + id);
        }
        task.setPid(0);
        taskRepository.save(task);
        zenvisBusinessService.reportTaskStopped(task);
        return operation("stop", task, previousStatus, true);
    }

    @Override
    public TaskOperationVo restart(Long id) {
        Task task = requireTask(id);
        String previousStatus = vectorService.status(String.valueOf(task.getId()));
        if (!previousStatus.startsWith("stopped")
                && !vectorService.stopProcess(String.valueOf(task.getId()))) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED.getCode(),
                    "重启前停止任务失败: taskId=" + id);
        }
        long processId = vectorService.startProcess(String.valueOf(task.getId()));
        if (processId <= 0) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED.getCode(),
                    "重启任务失败: taskId=" + id);
        }
        task.setPid((int) processId);
        applyRunMetadata(task);
        taskRepository.save(task);
        zenvisBusinessService.reportTaskStarted(task);
        return operation("restart", task, previousStatus, true);
    }

    /**
     * 获取日志
     * @param id 任务id
     * @param logLevel 日志级别（console/system）
     * @return 日志内容
     */
    @Override
    public String log(Long id, String logLevel) {
        if ("console".equals(logLevel)) {
            return vectorService.infoLog(String.valueOf(id));
        } else if ("system".equals(logLevel)) {
            return vectorService.errorLog(String.valueOf(id));
        }
        return null;
    }

    /**
     * 获取当前运行轮次诊断。
     *
     * @param id 任务 ID
     * @return 运行诊断
     */
    @Override
    public RuntimeDiagnostic diagnostics(Long id) {
        return taskDiagnosticsService.diagnose(id);
    }

    private Task requireTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.TASK_NOT_FOUND));
    }

    private void applyRunMetadata(Task task) {
        RunMetadata runMetadata = vectorService.runMetadata(String.valueOf(task.getId()));
        if (runMetadata != null) {
            task.setCurrentRunId(runMetadata.getRunId());
            task.setRunStartedAt(runMetadata.getStartedAt());
        }
    }

    private TaskOperationVo operation(String action, Task task,
                                      String previousStatus, boolean changed) {
        return TaskOperationVo.builder()
                .action(action)
                .taskId(task.getId())
                .previousStatus(previousStatus)
                .status(vectorService.status(String.valueOf(task.getId())))
                .changed(changed)
                .pid(task.getPid())
                .currentRunId(task.getCurrentRunId())
                .runStartedAt(task.getRunStartedAt())
                .build();
    }

    private TaskVo toVo(Task task) {
        TaskVo vo = new TaskVo(task, vectorService.status(String.valueOf(task.getId())));
        if (!hasText(vo.getFormat()) && hasText(task.getConfig())) {
            String detected = ConfigFormatDetector.detect(null, task.getConfig());
            vo.setFormat(ConfigFormatDetector.supported(detected) ? detected : null);
        }
        return vo;
    }

    private String resolveFormat(String persistedFormat, String requestedFormat, String config) {
        if (hasText(requestedFormat)) {
            return ConfigFormatDetector.resolve(requestedFormat, config);
        }
        if (hasText(persistedFormat)) {
            String normalized = ConfigFormatDetector.normalizeExplicit(persistedFormat);
            if (normalized != null) {
                return normalized;
            }
        }
        return ConfigFormatDetector.resolve(null, config);
    }

    private boolean matchesSource(TaskVo task, String source) {
        return !hasText(source)
                || source.trim().equalsIgnoreCase(Objects.toString(task.getSource(), ""));
    }

    private boolean matchesStatus(String status, String requested) {
        if (!hasText(requested)) {
            return true;
        }
        String actual = Objects.toString(status, "").toLowerCase(Locale.ROOT);
        String expected = requested.trim().toLowerCase(Locale.ROOT);
        return actual.equals(expected)
                || ("running".equals(expected) && actual.startsWith("running"));
    }

    private boolean containsIgnoreCase(String value, String search) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT)
                .contains(search.trim().toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
