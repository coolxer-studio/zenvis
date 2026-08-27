package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;

import java.util.List;

public interface AnalysisTaskService {

    List<AnalysisTaskVo> findAll(Integer currentUserId);

    PageRowsVo<AnalysisTaskVo> getPageList(AnalysisTaskSearchDto analysisTaskSearchDto,
                                           Integer currentUserId);

    AnalysisTask create(AnalysisTaskDto analysisTaskDto, Integer currentUserId);

    Boolean update(Long id, AnalysisTaskDto analysisTaskDto, Integer currentUserId);

    void delete(Long id, Integer currentUserId);

    void deleteByIds(List<Long> ids, Integer currentUserId);

    AnalysisTaskVo info(Long id, Integer currentUserId);

    AnalysisTaskVo detail(Long id, Integer currentUserId);

    AnalysisTaskVo enqueue(Long id, Integer currentUserId);

    AnalysisTaskVo cancel(Long id, Integer currentUserId);

    AnalysisTaskVo executeNextTask();

    AnalysisTaskVo executeNextTask(Integer currentUserId);

    AnalysisTaskQueueVo queueStatus(Integer currentUserId);

    void recoverRunningTasks();
}
