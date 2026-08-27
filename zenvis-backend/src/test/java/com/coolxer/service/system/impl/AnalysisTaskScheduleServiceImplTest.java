package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.dao.mysql.repository.AnalysisTaskScheduleRepository;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisTaskScheduleServiceImplTest {

    @Test
    void createStartsAtNextCronTimeWithoutCreatingTask() {
        AnalysisTaskScheduleRepository repository = mock(AnalysisTaskScheduleRepository.class);
        SkillService skillService = mock(SkillService.class);
        when(repository.save(any(AnalysisTaskSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AnalysisTaskScheduleServiceImpl service = new AnalysisTaskScheduleServiceImpl(
                repository, skillService);
        AnalysisTaskScheduleDto dto = validDto();
        dto.setCronExpression(" 0 */10 * * * * ");
        dto.setSkillIds(List.of(" risk-skill ", "risk-skill"));

        AnalysisTaskSchedule schedule = service.create(dto, 42);

        assertThat(schedule.getCronExpression()).isEqualTo("0 */10 * * * *");
        assertThat(schedule.getNextFireTime()).isAfter(new java.util.Date());
        assertThat(schedule.getLastFireTime()).isNull();
        assertThat(schedule.getGeneratedCount()).isZero();
        assertThat(schedule.getSkillIds()).containsExactly("risk-skill");
        assertThat(schedule.getCreateBy()).isEqualTo(42);
        verify(skillService).validateEnabledSkillIds(List.of("risk-skill"));
    }

    @Test
    void invalidCronIsRejected() {
        AnalysisTaskScheduleServiceImpl service = new AnalysisTaskScheduleServiceImpl(
                mock(AnalysisTaskScheduleRepository.class), mock(SkillService.class));
        AnalysisTaskScheduleDto dto = validDto();
        dto.setCronExpression("not-a-cron");

        assertThatThrownBy(() -> service.create(dto, 42))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cron");
    }

    @Test
    void disableAndEnableOnlyControlFutureFireTime() {
        AnalysisTaskScheduleRepository repository = mock(AnalysisTaskScheduleRepository.class);
        SkillService skillService = mock(SkillService.class);
        AnalysisTaskSchedule schedule = new AnalysisTaskSchedule()
                .setEnabled(true)
                .setCronExpression("0 */10 * * * *")
                .setNextFireTime(new Date());
        schedule.setId(8);
        when(repository.findOwnedByIdForUpdate(8, 42)).thenReturn(Optional.of(schedule));
        when(repository.save(schedule)).thenReturn(schedule);
        AnalysisTaskScheduleServiceImpl service = new AnalysisTaskScheduleServiceImpl(
                repository, skillService);

        service.setEnabled(8L, false, 42);
        assertThat(schedule.getEnabled()).isFalse();
        assertThat(schedule.getNextFireTime()).isNull();

        service.setEnabled(8L, true, 42);
        assertThat(schedule.getEnabled()).isTrue();
        assertThat(schedule.getNextFireTime()).isAfter(new Date());
    }

    @Test
    void anotherUserCannotReadOrMutateScheduleById() {
        AnalysisTaskScheduleRepository repository = mock(AnalysisTaskScheduleRepository.class);
        AnalysisTaskScheduleServiceImpl service = new AnalysisTaskScheduleServiceImpl(
                repository, mock(SkillService.class));
        when(repository.findByIdAndCreateBy(8, 43)).thenReturn(Optional.empty());
        when(repository.findOwnedByIdForUpdate(8, 43)).thenReturn(Optional.empty());

        assertThat(service.info(8L, 43)).isNull();
        assertThatThrownBy(() -> service.setEnabled(8L, false, 43))
                .isInstanceOf(ApiException.class);
        verify(repository, never()).save(any(AnalysisTaskSchedule.class));
    }

    private static AnalysisTaskScheduleDto validDto() {
        AnalysisTaskScheduleDto dto = new AnalysisTaskScheduleDto();
        dto.setName("周期风险分析");
        dto.setPrompt("分析最近数据");
        dto.setApprovalMode(AnalysisTaskApprovalMode.MANUAL);
        dto.setCronExpression("0 0 9 * * *");
        return dto;
    }
}
