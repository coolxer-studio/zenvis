package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskScheduleVo;

public interface AnalysisTaskScheduleService {

    PageRowsVo<AnalysisTaskScheduleVo> getPageList(AnalysisTaskScheduleSearchDto search);

    AnalysisTaskSchedule create(AnalysisTaskScheduleDto dto);

    Boolean update(Long id, AnalysisTaskScheduleDto dto);

    AnalysisTaskScheduleVo info(Long id);

    AnalysisTaskScheduleVo setEnabled(Long id, boolean enabled);

    void delete(Long id);
}
