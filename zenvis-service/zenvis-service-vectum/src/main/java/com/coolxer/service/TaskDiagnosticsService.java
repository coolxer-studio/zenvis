package com.coolxer.service;

import com.coolxer.model.diagnostic.RuntimeDiagnostic;

/**
 * 当前运行轮次诊断服务。
 */
public interface TaskDiagnosticsService {

    /**
     * 仅分析任务当前 runId 对应的启动日志。
     *
     * @param taskId 任务 ID
     * @return 结构化运行诊断
     */
    RuntimeDiagnostic diagnose(Long taskId);
}
