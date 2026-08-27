package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.dao.mysql.repository.AnalysisTaskScheduleRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskScheduleVo;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.system.AnalysisTaskScheduleService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnalysisTaskScheduleServiceImpl implements AnalysisTaskScheduleService {

    private final AnalysisTaskScheduleRepository scheduleRepository;
    private final SkillService skillService;

    public AnalysisTaskScheduleServiceImpl(AnalysisTaskScheduleRepository scheduleRepository,
                                           SkillService skillService) {
        this.scheduleRepository = scheduleRepository;
        this.skillService = skillService;
    }

    @Override
    public PageRowsVo<AnalysisTaskScheduleVo> getPageList(AnalysisTaskScheduleSearchDto condition,
                                                          Integer currentUserId) {
        Integer ownerId = requireUserId(currentUserId);
        AnalysisTaskScheduleSearchDto search = condition == null
                ? new AnalysisTaskScheduleSearchDto() : condition;
        Pageable pageable = PageRequest.of(Math.max(search.getPage(), 1) - 1,
                Math.max(search.getPerPage(), 1));
        Page<AnalysisTaskSchedule> page = scheduleRepository.findByPage(
                pageable, StringUtils.trimToNull(search.getName()), search.getEnabled(), ownerId);
        return new PageRowsVo<>(page.getContent().stream().map(AnalysisTaskScheduleVo::new).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public AnalysisTaskSchedule create(AnalysisTaskScheduleDto dto, Integer currentUserId) {
        Integer ownerId = requireUserId(currentUserId);
        normalizeAndValidate(dto);
        AnalysisTaskSchedule schedule = new AnalysisTaskSchedule();
        schedule.updateFromDto(dto);
        schedule.setGeneratedCount(0);
        schedule.setLastError(null);
        schedule.setNextFireTime(Boolean.TRUE.equals(schedule.getEnabled())
                ? nextFireTime(schedule.getCronExpression(), new Date()) : null);
        schedule.setCreateBy(ownerId);
        schedule.setUpdateBy(ownerId);
        return scheduleRepository.save(schedule);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public Boolean update(Long id, AnalysisTaskScheduleDto dto, Integer currentUserId) {
        Integer ownerId = requireUserId(currentUserId);
        AnalysisTaskSchedule schedule = requireOwnedScheduleForUpdate(id, ownerId);
        normalizeAndValidate(dto);
        schedule.updateFromDto(dto);
        schedule.setLastError(null);
        schedule.setNextFireTime(Boolean.TRUE.equals(schedule.getEnabled())
                ? nextFireTime(schedule.getCronExpression(), new Date()) : null);
        schedule.setUpdateBy(ownerId);
        scheduleRepository.save(schedule);
        return true;
    }

    @Override
    public AnalysisTaskScheduleVo info(Long id, Integer currentUserId) {
        Integer ownerId = requireUserId(currentUserId);
        if (id == null) {
            return null;
        }
        try {
            return scheduleRepository.findByIdAndCreateBy(Math.toIntExact(id), ownerId)
                    .map(AnalysisTaskScheduleVo::new).orElse(null);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public AnalysisTaskScheduleVo setEnabled(Long id, boolean enabled, Integer currentUserId) {
        Integer ownerId = requireUserId(currentUserId);
        AnalysisTaskSchedule schedule = requireOwnedScheduleForUpdate(id, ownerId);
        schedule.setEnabled(enabled).setLastError(null);
        schedule.setNextFireTime(enabled ? nextFireTime(schedule.getCronExpression(), new Date()) : null);
        schedule.setUpdateBy(ownerId);
        return new AnalysisTaskScheduleVo(scheduleRepository.save(schedule));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public void delete(Long id, Integer currentUserId) {
        AnalysisTaskSchedule schedule = requireOwnedScheduleForUpdate(id, requireUserId(currentUserId));
        scheduleRepository.deleteById(schedule.getId());
    }

    private AnalysisTaskSchedule requireOwnedScheduleForUpdate(Long id, Integer ownerId) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        try {
            return scheduleRepository.findOwnedByIdForUpdate(Math.toIntExact(id), ownerId)
                    .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_AUTHORITY));
        } catch (ArithmeticException e) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }

    private static Integer requireUserId(Integer currentUserId) {
        if (currentUserId == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        return currentUserId;
    }

    private void normalizeAndValidate(AnalysisTaskScheduleDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getPrompt())
                || dto.getApprovalMode() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        String cronExpression = StringUtils.trimToNull(dto.getCronExpression());
        if (cronExpression == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "Cron表达式不能为空");
        }
        try {
            if (nextFireTime(cronExpression, new Date()) == null) {
                throw new IllegalArgumentException("Cron expression has no next execution");
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "周期执行 Cron 表达式无效，请使用包含秒的 6 段格式");
        }
        List<String> skillIds = normalizeSkillIds(dto.getSkillIds());
        skillService.validateEnabledSkillIds(skillIds);
        dto.setCronExpression(cronExpression);
        dto.setSkillIds(skillIds);
    }

    static Date nextFireTime(String cronExpression, Date after) {
        ZonedDateTime base = ZonedDateTime.ofInstant(after.toInstant(), ZoneId.systemDefault());
        ZonedDateTime next = CronExpression.parse(cronExpression).next(base);
        return next == null ? null : Date.from(next.toInstant());
    }

    private static List<String> normalizeSkillIds(List<String> skillIds) {
        if (skillIds == null) {
            return new ArrayList<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String skillId : skillIds) {
            if (StringUtils.isNotBlank(skillId)) {
                normalized.add(skillId.trim());
            }
        }
        return new ArrayList<>(normalized);
    }
}
