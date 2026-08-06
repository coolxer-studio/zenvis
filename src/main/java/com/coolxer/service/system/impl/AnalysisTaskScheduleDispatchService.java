package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.dao.mysql.repository.AnalysisTaskRepository;
import com.coolxer.dao.mysql.repository.AnalysisTaskScheduleRepository;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * 在独立事务中锁定一个周期配置并生成一次性任务。
 */
@Service
public class AnalysisTaskScheduleDispatchService {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final AnalysisTaskScheduleRepository scheduleRepository;
    private final AnalysisTaskRepository taskRepository;
    private final SkillService skillService;

    public AnalysisTaskScheduleDispatchService(AnalysisTaskScheduleRepository scheduleRepository,
                                               AnalysisTaskRepository taskRepository,
                                               SkillService skillService) {
        this.scheduleRepository = scheduleRepository;
        this.taskRepository = taskRepository;
        this.skillService = skillService;
    }

    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public void dispatch(Integer scheduleId, Date checkedAt, long misfireGraceMs) {
        AnalysisTaskSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())
                || schedule.getNextFireTime() == null || schedule.getNextFireTime().after(checkedAt)) {
            return;
        }

        Date fireTime = schedule.getNextFireTime();
        Date nextTime;
        try {
            nextTime = AnalysisTaskScheduleServiceImpl.nextFireTime(schedule.getCronExpression(), checkedAt);
            if (nextTime == null) {
                schedule.setEnabled(false).setNextFireTime(null)
                        .setLastError("Cron 表达式没有可用的下一次执行时间，周期任务已停用");
                scheduleRepository.save(schedule);
                return;
            }
        } catch (IllegalArgumentException e) {
            schedule.setEnabled(false).setNextFireTime(null)
                    .setLastError("Cron 表达式无效，周期任务已停用");
            scheduleRepository.save(schedule);
            return;
        }

        if (checkedAt.getTime() - fireTime.getTime() > Math.max(misfireGraceMs, 0L)) {
            schedule.setNextFireTime(nextTime).setLastError(null);
            scheduleRepository.save(schedule);
            return;
        }

        try {
            skillService.validateEnabledSkillIds(new ArrayList<>(schedule.getSkillIds()));
            AnalysisTask task = buildTask(schedule, fireTime);
            taskRepository.saveAndFlush(task);
            schedule.setLastFireTime(fireTime)
                    .setNextFireTime(nextTime)
                    .setGeneratedCount((schedule.getGeneratedCount() == null ? 0 : schedule.getGeneratedCount()) + 1)
                    .setLastError(null);
        } catch (IllegalArgumentException e) {
            schedule.setNextFireTime(nextTime).setLastError(limitError(e));
        }
        scheduleRepository.save(schedule);
    }

    /**
     * 成功投递事务回滚后，另起事务记录错误并跳到下一个未来周期，避免热重试。
     */
    @Transactional(transactionManager = "mysqlTransactionManager",
            propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailureAndAdvance(Integer scheduleId, Date checkedAt, Exception failure) {
        AnalysisTaskSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())
                || schedule.getNextFireTime() == null || schedule.getNextFireTime().after(checkedAt)) {
            return;
        }
        try {
            Date nextTime = AnalysisTaskScheduleServiceImpl.nextFireTime(
                    schedule.getCronExpression(), checkedAt);
            if (nextTime == null) {
                schedule.setEnabled(false).setNextFireTime(null);
            } else {
                schedule.setNextFireTime(nextTime);
            }
        } catch (IllegalArgumentException e) {
            schedule.setEnabled(false).setNextFireTime(null);
        }
        schedule.setLastError("创建任务失败：" + limitError(failure));
        scheduleRepository.save(schedule);
    }

    private AnalysisTask buildTask(AnalysisTaskSchedule schedule, Date fireTime) {
        AnalysisTask task = new AnalysisTask()
                .setName(schedule.getName())
                .setDescription(schedule.getDescription())
                .setModel(schedule.getModel())
                .setPrompt(schedule.getPrompt())
                .setPriority(schedule.getPriority() == null ? 0 : schedule.getPriority())
                .setApprovalMode(schedule.getApprovalMode())
                .setSkillIds(new LinkedHashSet<>(schedule.getSkillIds()))
                .setScheduledTime(fireTime)
                .setScheduleId(schedule.getId())
                .setScheduleFireTime(fireTime)
                .setStatus(AnalysisTaskStatus.PENDING)
                .setRunCount(0)
                .setExecutionId(UUID.randomUUID().toString());
        task.setCreateBy(schedule.getCreateBy());
        return task;
    }

    private static String limitError(Exception error) {
        String message = StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName());
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
