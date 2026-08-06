package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AnalysisTaskScheduleVo implements Serializable {

    private Integer id;
    private String name;
    private String description;
    private String model;
    private String prompt;
    private Integer priority;
    private AnalysisTaskApprovalMode approvalMode;
    private String cronExpression;
    private Boolean enabled;
    private List<String> skillIds;
    private Date nextFireTime;
    private Date lastFireTime;
    private Integer generatedCount;
    private String lastError;
    private Date createTime;
    private Date updateTime;
    private Integer createBy;

    public AnalysisTaskScheduleVo(AnalysisTaskSchedule schedule) {
        this.id = schedule.getId();
        this.name = schedule.getName();
        this.description = schedule.getDescription();
        this.model = schedule.getModel();
        this.prompt = schedule.getPrompt();
        this.priority = schedule.getPriority();
        this.approvalMode = schedule.getApprovalMode();
        this.cronExpression = schedule.getCronExpression();
        this.enabled = schedule.getEnabled();
        this.skillIds = schedule.getSkillIds() == null
                ? new ArrayList<>() : new ArrayList<>(schedule.getSkillIds());
        this.nextFireTime = schedule.getNextFireTime();
        this.lastFireTime = schedule.getLastFireTime();
        this.generatedCount = schedule.getGeneratedCount();
        this.lastError = schedule.getLastError();
        this.createTime = schedule.getCreateTime();
        this.updateTime = schedule.getUpdateTime();
        this.createBy = schedule.getCreateBy();
    }
}
