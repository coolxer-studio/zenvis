package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.dao.mysql.repository.AnalysisTaskRepository;
import com.coolxer.dao.mysql.repository.AnalysisTaskScheduleRepository;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisTaskScheduleDispatchServiceTest {

    @Test
    void dueScheduleCreatesIndependentTaskAndAdvancesSchedule() {
        AnalysisTaskScheduleRepository scheduleRepository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskRepository taskRepository = mock(AnalysisTaskRepository.class);
        SkillService skillService = mock(SkillService.class);
        Date checkedAt = new Date();
        AnalysisTaskSchedule schedule = schedule(checkedAt);
        when(scheduleRepository.findByIdForUpdate(9)).thenReturn(Optional.of(schedule));
        when(taskRepository.saveAndFlush(any(AnalysisTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisTaskScheduleDispatchService service = new AnalysisTaskScheduleDispatchService(
                scheduleRepository, taskRepository, skillService);
        service.dispatch(9, checkedAt, 60_000L);

        ArgumentCaptor<AnalysisTask> taskCaptor = ArgumentCaptor.forClass(AnalysisTask.class);
        verify(taskRepository).saveAndFlush(taskCaptor.capture());
        AnalysisTask task = taskCaptor.getValue();
        assertThat(task.getName()).isEqualTo(schedule.getName());
        assertThat(task.getPrompt()).isEqualTo(schedule.getPrompt());
        assertThat(task.getSkillIds()).containsExactly("risk-skill");
        assertThat(task.getApprovalMode()).isEqualTo(AnalysisTaskApprovalMode.AUTO);
        assertThat(task.getStatus()).isEqualTo(AnalysisTaskStatus.PENDING);
        assertThat(task.getScheduleId()).isEqualTo(9);
        assertThat(task.getScheduleFireTime()).isEqualTo(checkedAt);
        assertThat(task.getScheduledTime()).isEqualTo(checkedAt);
        assertThat(task.getCreateBy()).isEqualTo(23);
        assertThat(task.getExecutionId()).isNotBlank();
        assertThat(schedule.getGeneratedCount()).isEqualTo(4);
        assertThat(schedule.getLastFireTime()).isEqualTo(checkedAt);
        assertThat(schedule.getNextFireTime()).isAfter(checkedAt);
        assertThat(schedule.getLastError()).isNull();
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void oldMisfireIsSkippedWithoutCreatingCompensationTask() {
        AnalysisTaskScheduleRepository scheduleRepository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskRepository taskRepository = mock(AnalysisTaskRepository.class);
        SkillService skillService = mock(SkillService.class);
        Date checkedAt = new Date();
        AnalysisTaskSchedule schedule = schedule(new Date(checkedAt.getTime() - 61_000L));
        when(scheduleRepository.findByIdForUpdate(9)).thenReturn(Optional.of(schedule));

        AnalysisTaskScheduleDispatchService service = new AnalysisTaskScheduleDispatchService(
                scheduleRepository, taskRepository, skillService);
        service.dispatch(9, checkedAt, 60_000L);

        verify(taskRepository, never()).saveAndFlush(any());
        assertThat(schedule.getGeneratedCount()).isEqualTo(3);
        assertThat(schedule.getLastFireTime()).isNull();
        assertThat(schedule.getNextFireTime()).isAfter(checkedAt);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void disabledSkillRecordsErrorAndWaitsForNextPeriod() {
        AnalysisTaskScheduleRepository scheduleRepository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskRepository taskRepository = mock(AnalysisTaskRepository.class);
        SkillService skillService = mock(SkillService.class);
        Date checkedAt = new Date();
        AnalysisTaskSchedule schedule = schedule(checkedAt);
        when(scheduleRepository.findByIdForUpdate(9)).thenReturn(Optional.of(schedule));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Skill risk-skill 已停用"))
                .when(skillService).validateEnabledSkillIds(any());

        AnalysisTaskScheduleDispatchService service = new AnalysisTaskScheduleDispatchService(
                scheduleRepository, taskRepository, skillService);
        service.dispatch(9, checkedAt, 60_000L);

        verify(taskRepository, never()).saveAndFlush(any());
        assertThat(schedule.getLastError()).contains("risk-skill").contains("停用");
        assertThat(schedule.getGeneratedCount()).isEqualTo(3);
        assertThat(schedule.getNextFireTime()).isAfter(checkedAt);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void generationFailureCanBeRecordedAndAdvancedAfterRollback() {
        AnalysisTaskScheduleRepository scheduleRepository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskRepository taskRepository = mock(AnalysisTaskRepository.class);
        SkillService skillService = mock(SkillService.class);
        Date checkedAt = new Date();
        AnalysisTaskSchedule schedule = schedule(checkedAt);
        when(scheduleRepository.findByIdForUpdate(9)).thenReturn(Optional.of(schedule));

        AnalysisTaskScheduleDispatchService service = new AnalysisTaskScheduleDispatchService(
                scheduleRepository, taskRepository, skillService);
        service.recordFailureAndAdvance(9, checkedAt, new IllegalStateException("insert failed"));

        assertThat(schedule.getLastError()).contains("创建任务失败").contains("insert failed");
        assertThat(schedule.getGeneratedCount()).isEqualTo(3);
        assertThat(schedule.getNextFireTime()).isAfter(checkedAt);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void repeatedScanOfSameFireTimeCreatesOnlyOneTask() {
        AnalysisTaskScheduleRepository scheduleRepository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskRepository taskRepository = mock(AnalysisTaskRepository.class);
        SkillService skillService = mock(SkillService.class);
        Date checkedAt = new Date();
        AnalysisTaskSchedule schedule = schedule(checkedAt);
        when(scheduleRepository.findByIdForUpdate(9)).thenReturn(Optional.of(schedule));
        when(taskRepository.saveAndFlush(any(AnalysisTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisTaskScheduleDispatchService service = new AnalysisTaskScheduleDispatchService(
                scheduleRepository, taskRepository, skillService);
        service.dispatch(9, checkedAt, 60_000L);
        service.dispatch(9, checkedAt, 60_000L);

        verify(taskRepository, times(1)).saveAndFlush(any(AnalysisTask.class));
        assertThat(schedule.getGeneratedCount()).isEqualTo(4);
    }

    private static AnalysisTaskSchedule schedule(Date fireTime) {
        AnalysisTaskSchedule schedule = new AnalysisTaskSchedule()
                .setName("周期风险分析")
                .setDescription("识别风险")
                .setModel("auto")
                .setPrompt("分析最近数据")
                .setPriority(5)
                .setApprovalMode(AnalysisTaskApprovalMode.AUTO)
                .setCronExpression("* * * * * *")
                .setEnabled(true)
                .setSkillIds(new LinkedHashSet<>(Set.of("risk-skill")))
                .setNextFireTime(fireTime)
                .setGeneratedCount(3);
        schedule.setId(9);
        schedule.setCreateBy(23);
        return schedule;
    }
}
