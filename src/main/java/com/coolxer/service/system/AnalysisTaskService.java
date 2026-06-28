package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;

import java.util.List;

public interface AnalysisTaskService {

    List<AnalysisTaskVo> findAll();

    PageRowsVo<AnalysisTaskVo> getPageList(AnalysisTaskSearchDto analysisTaskSearchDto);

    AnalysisTask create(AnalysisTaskDto analysisTaskDto);

    Boolean update(Long id, AnalysisTaskDto analysisTaskDto);

    void delete(Long id);

    void deleteByIds(List<Long> ids);

    AnalysisTaskVo info(Long id);

    AnalysisTaskVo enqueue(Long id);

    AnalysisTaskVo cancel(Long id);

    AnalysisTaskVo executeNextTask();

    AnalysisTaskQueueVo queueStatus();

    void recoverRunningTasks();
}
