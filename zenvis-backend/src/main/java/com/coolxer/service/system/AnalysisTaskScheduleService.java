package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskScheduleVo;

public interface AnalysisTaskScheduleService {

    PageRowsVo<AnalysisTaskScheduleVo> getPageList(AnalysisTaskScheduleSearchDto search,
                                                   Integer currentUserId);

    AnalysisTaskSchedule create(AnalysisTaskScheduleDto dto, Integer currentUserId);

    Boolean update(Long id, AnalysisTaskScheduleDto dto, Integer currentUserId);

    AnalysisTaskScheduleVo info(Long id, Integer currentUserId);

    AnalysisTaskScheduleVo setEnabled(Long id, boolean enabled, Integer currentUserId);

    void delete(Long id, Integer currentUserId);
}
