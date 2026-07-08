package com.coolxer.lubinsun.model;

import lombok.Data;

import java.util.List;

@Data
public class LubinsunTaskDetailVo {

    private LubinsunSkillRunTaskVo task;

    private List<LubinsunSkillRunEventVo> events;

    public LubinsunTaskDetailVo(LubinsunSkillRunTaskVo task, List<LubinsunSkillRunEventVo> events) {
        this.task = task;
        this.events = events;
    }
}
