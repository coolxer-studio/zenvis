package com.coolxer.model.system.vo;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.model.dih.ChatMessagePart;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 详情接口返回的结构化结果片段；列表接口不填充该字段。
     */
    private List<ChatMessagePart> resultParts;

    private String errorMessage;

    private AnalysisTaskStatus status;

    private String statusDescription;

    private Integer priority;

    private AnalysisTaskApprovalMode approvalMode;

    private String executionId;

    private String lastExecutionId;

    private AnalysisTaskStatus lastRunStatus;

    private List<String> skillIds;

    private long pendingApprovalCount;

    private Date scheduledTime;

    private String cronExpression;

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
        this.approvalMode = analysisTask.getApprovalMode() == null
                ? AnalysisTaskApprovalMode.MANUAL : analysisTask.getApprovalMode();
        this.executionId = analysisTask.getExecutionId();
        this.lastExecutionId = analysisTask.getLastExecutionId();
        this.lastRunStatus = analysisTask.getLastRunStatus();
        this.skillIds = analysisTask.getSkillIds() == null
                ? new ArrayList<>() : new ArrayList<>(analysisTask.getSkillIds());
        this.scheduledTime = analysisTask.getScheduledTime();
        this.cronExpression = analysisTask.getCronExpression();
        this.startTime = analysisTask.getStartTime();
        this.finishTime = analysisTask.getFinishTime();
        this.runCount = analysisTask.getRunCount();
        this.createTime = analysisTask.getCreateTime();
        this.updateTime = analysisTask.getUpdateTime();
        this.createBy = analysisTask.getCreateBy();
    }
}
