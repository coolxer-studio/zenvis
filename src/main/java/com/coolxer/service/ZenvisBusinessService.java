package com.coolxer.service;

import com.coolxer.model.Task;

/**
 * Zenvis 业务服务通信接口。
 * <p>
 * 将 Vectum 任务生命周期转换为 Zenvis 业务事件。
 * </p>
 */
public interface ZenvisBusinessService {

    /**
     * 上报任务创建事件。
     *
     * @param task 已创建的任务
     */
    void reportTaskCreated(Task task);

    /**
     * 上报任务更新事件。
     *
     * @param task 已更新的任务
     */
    void reportTaskUpdated(Task task);

    /**
     * 上报任务删除事件。
     *
     * @param taskId 已删除的任务 ID
     */
    void reportTaskDeleted(Long taskId);

    /**
     * 上报任务启动事件。
     *
     * @param task 已启动的任务
     */
    void reportTaskStarted(Task task);

    /**
     * 上报任务停止事件。
     *
     * @param task 已停止的任务
     */
    void reportTaskStopped(Task task);
}
