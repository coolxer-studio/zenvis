package com.coolxer.service.system;

import com.coolxer.dao.mysql.repository.AnalysisTaskScheduleRepository;
import com.coolxer.service.system.impl.AnalysisTaskScheduleDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class AnalysisTaskScheduleScheduler {

    private final AnalysisTaskScheduleRepository scheduleRepository;
    private final AnalysisTaskScheduleDispatchService dispatchService;

    @Value("${app.ai.analysis-task.schedule-misfire-grace-ms:60000}")
    private long misfireGraceMs;

    public AnalysisTaskScheduleScheduler(AnalysisTaskScheduleRepository scheduleRepository,
                                         AnalysisTaskScheduleDispatchService dispatchService) {
        this.scheduleRepository = scheduleRepository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${app.ai.analysis-task.schedule-dispatch-delay-ms:5000}")
    public void dispatch() {
        Date checkedAt = new Date();
        List<Integer> dueIds = scheduleRepository.findDueIds(checkedAt, PageRequest.of(0, 100));
        for (Integer scheduleId : dueIds) {
            try {
                dispatchService.dispatch(scheduleId, checkedAt, misfireGraceMs);
            } catch (Exception e) {
                log.error("创建周期AI分析任务失败, scheduleId={}", scheduleId, e);
                try {
                    dispatchService.recordFailureAndAdvance(scheduleId, checkedAt, e);
                } catch (Exception recordError) {
                    log.error("记录周期AI分析任务创建错误失败, scheduleId={}", scheduleId, recordError);
                }
            }
        }
    }
}
