package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI分析任务展示对象
 */
@Data
public class AnalysisTaskVo implements Serializable {

    private Integer id;

    private String name;

    private String description;

    private String model;

    private String prompt;

    private String result;

    private String errorMessage;

    private AnalysisTaskStatus status;

    private String statusDescription;

    private Integer priority;

    private Date scheduledTime;

    private Date startTime;

    private Date finishTime;

    private Integer runCount;

    private Date createTime;

    private Date updateTime;

    private Integer createBy;

    public AnalysisTaskVo(AnalysisTask analysisTask) {
        this.id = analysisTask.getId();
        this.name = analysisTask.getName();
        this.description = analysisTask.getDescription();
        this.model = analysisTask.getModel();
        this.prompt = analysisTask.getPrompt();
        this.result = analysisTask.getResult();
        this.errorMessage = analysisTask.getErrorMessage();
        this.status = analysisTask.getStatus();
        this.statusDescription = analysisTask.getStatus() == null ? null : analysisTask.getStatus().getDescription();
        this.priority = analysisTask.getPriority();
        this.scheduledTime = analysisTask.getScheduledTime();
        this.startTime = analysisTask.getStartTime();
        this.finishTime = analysisTask.getFinishTime();
        this.runCount = analysisTask.getRunCount();
        this.createTime = analysisTask.getCreateTime();
        this.updateTime = analysisTask.getUpdateTime();
        this.createBy = analysisTask.getCreateBy();
    }
}
