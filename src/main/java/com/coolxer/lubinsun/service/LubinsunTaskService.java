package com.coolxer.lubinsun.service;

import com.coolxer.lubinsun.model.LubinsunSkillRunEventVo;
import com.coolxer.lubinsun.model.LubinsunSkillRunTaskVo;
import com.coolxer.lubinsun.model.LubinsunTaskDetailVo;
import com.coolxer.lubinsun.model.LubinsunTaskDto;
import com.coolxer.lubinsun.model.LubinsunTaskSearchDto;
import com.coolxer.model.base.vo.PageRowsVo;

import java.util.List;

public interface LubinsunTaskService {

    LubinsunSkillRunTaskVo create(LubinsunTaskDto dto);

    LubinsunSkillRunTaskVo update(Long id, LubinsunTaskDto dto);

    PageRowsVo<LubinsunSkillRunTaskVo> getPageList(LubinsunTaskSearchDto searchDto);

    LubinsunTaskDetailVo info(Long id);

    void delete(Long id);

    void deleteByIds(List<Long> ids);

    LubinsunSkillRunTaskVo execute(Long id);

    LubinsunSkillRunTaskVo refresh(Long id);

    List<LubinsunSkillRunEventVo> events(Long id);

    void pollActiveTasks();
}
