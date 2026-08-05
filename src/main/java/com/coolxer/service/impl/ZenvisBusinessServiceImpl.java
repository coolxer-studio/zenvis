package com.coolxer.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.coolxer.model.Task;
import com.coolxer.service.ZenvisBusinessService;
import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;

/**
 * Zenvis 业务服务通信实现。
 * <p>
 * 使用 Zenvis Starter 提供的异步上报器发送任务生命周期事件，
 * 且不让观测链路异常影响任务操作。
 * </p>
 */
@Slf4j
@Service
public class ZenvisBusinessServiceImpl implements ZenvisBusinessService {

    private static final String TASK_CREATED = "TASK_CREATED";
    private static final String TASK_UPDATED = "TASK_UPDATED";
    private static final String TASK_DELETED = "TASK_DELETED";
    private static final String TASK_STARTED = "TASK_STARTED";
    private static final String TASK_STOPPED = "TASK_STOPPED";

    private final BusinessServiceReporter businessServiceReporter;

    public ZenvisBusinessServiceImpl(BusinessServiceReporter businessServiceReporter) {
        this.businessServiceReporter = businessServiceReporter;
    }

    /**
     * 上报任务创建事件。
     *
     * @param task 已创建的任务
     */
    @Override
    public void reportTaskCreated(Task task) {
        report(TASK_CREATED, "Vectum 任务已创建", task, task.getId());
    }

    /**
     * 上报任务更新事件。
     *
     * @param task 已更新的任务
     */
    @Override
    public void reportTaskUpdated(Task task) {
        report(TASK_UPDATED, "Vectum 任务已更新", task, task.getId());
    }

    /**
     * 上报任务删除事件。
     *
     * @param taskId 已删除的任务 ID
     */
    @Override
    public void reportTaskDeleted(Long taskId) {
        report(TASK_DELETED, "Vectum 任务已删除", null, taskId);
    }

    /**
     * 上报任务启动事件。
     *
     * @param task 已启动的任务
     */
    @Override
    public void reportTaskStarted(Task task) {
        report(TASK_STARTED, "Vectum 任务已启动", task, task.getId());
    }

    /**
     * 上报任务停止事件。
     *
     * @param task 已停止的任务
     */
    @Override
    public void reportTaskStopped(Task task) {
        report(TASK_STOPPED, "Vectum 任务已停止", task, task.getId());
    }

    private void report(String eventType, String title, Task task, Long taskId) {
        try {
            businessServiceReporter.reportEvent(
                    eventType,
                    BusinessServiceEventSeverity.INFO,
                    title,
                    title + ": taskId=" + taskId,
                    null,
                    taskData(task, taskId));
        } catch (RuntimeException exception) {
            log.warn("Failed to submit Zenvis event: eventType={}, taskId={}", eventType, taskId, exception);
        }
    }

    private Map<String, Object> taskData(Task task, Long taskId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        if (task != null) {
            data.put("taskName", task.getName());
            data.put("taskSource", task.getSource() == null ? null : task.getSource().name());
            data.put("processId", task.getPid());
        }
        return data;
    }
}
