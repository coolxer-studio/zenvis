package com.coolxer.service.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI分析任务队列调度器
 */
@Slf4j
@Component
public class AnalysisTaskScheduler {

    private final AnalysisTaskService analysisTaskService;

    public AnalysisTaskScheduler(AnalysisTaskService analysisTaskService) {
        this.analysisTaskService = analysisTaskService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverRunningTasks() {
        analysisTaskService.recoverRunningTasks();
    }

    @Scheduled(fixedDelayString = "${app.ai.analysis-task.dispatch-delay-ms:5000}")
    public void dispatch() {
        try {
            for (int i = 0; i < 100; i++) {
                if (analysisTaskService.executeNextTask() == null) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("AI分析任务队列调度失败", e);
        }
    }
}
