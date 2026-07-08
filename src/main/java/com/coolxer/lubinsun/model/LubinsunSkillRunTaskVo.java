package com.coolxer.lubinsun.model;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.entity.LubinsunSkillRunTask;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class LubinsunSkillRunTaskVo implements Serializable {

    private Integer id;
    private String name;
    private String skill;
    private String agent;
    private String externalId;
    private String title;
    private String taskType;
    private String ip;
    private String rawLog;
    private JsonNode input;
    private JsonNode metadata;
    private JsonNode eventLogs;
    private JsonNode alarmLogs;
    private String inputJson;
    private String metadataJson;
    private String eventLogsJson;
    private String alarmLogsJson;
    private LubinsunTaskStatus status;
    private String statusDescription;
    private String platformStatus;
    private String runId;
    private String sessionId;
    private String platformTaskId;
    private String responseTitle;
    private String summary;
    private String resultSummary;
    private JsonNode keyPoints;
    private JsonNode result;
    private JsonNode publicResult;
    private JsonNode pendingPermissions;
    private JsonNode snapshot;
    private JsonNode createResponse;
    private String errorMessage;
    private Long lastSeq;
    private Date executedAt;
    private Date lastPolledAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;
    private Integer createBy;

    public LubinsunSkillRunTaskVo(LubinsunSkillRunTask task) {
        this.id = task.getId();
        this.name = task.getName();
        this.skill = task.getSkill();
        this.agent = task.getAgent();
        this.externalId = task.getExternalId();
        this.title = task.getPlatformTitle();
        this.taskType = task.getTaskType();
        this.ip = task.getIp();
        this.rawLog = task.getRawLog();
        this.inputJson = task.getInputJson();
        this.metadataJson = task.getMetadataJson();
        this.eventLogsJson = task.getEventLogsJson();
        this.alarmLogsJson = task.getAlarmLogsJson();
        this.input = readJson(task.getInputJson());
        this.metadata = readJson(task.getMetadataJson());
        this.eventLogs = readJson(task.getEventLogsJson());
        this.alarmLogs = readJson(task.getAlarmLogsJson());
        this.status = task.getStatus();
        this.statusDescription = task.getStatus() == null ? null : task.getStatus().getDescription();
        this.platformStatus = task.getPlatformStatus();
        this.runId = task.getRunId();
        this.sessionId = task.getSessionId();
        this.platformTaskId = task.getPlatformTaskId();
        this.responseTitle = task.getResponseTitle();
        this.summary = task.getSummary();
        this.resultSummary = task.getResultSummary();
        this.keyPoints = readJson(task.getKeyPointsJson());
        this.result = readJson(task.getResultJson());
        this.publicResult = readJson(task.getPublicResultJson());
        this.pendingPermissions = readJson(task.getPendingPermissionsJson());
        this.snapshot = readJson(task.getSnapshotJson());
        this.createResponse = readJson(task.getCreateResponseJson());
        this.errorMessage = task.getErrorMessage();
        this.lastSeq = task.getLastSeq();
        this.executedAt = task.getExecutedAt();
        this.lastPolledAt = task.getLastPolledAt();
        this.finishedAt = task.getFinishedAt();
        this.createTime = task.getCreateTime();
        this.updateTime = task.getUpdateTime();
        this.createBy = task.getCreateBy();
    }

    private static JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            return JacksonConfig.OBJECT_MAPPER.getNodeFactory().textNode(json);
        }
    }
}
