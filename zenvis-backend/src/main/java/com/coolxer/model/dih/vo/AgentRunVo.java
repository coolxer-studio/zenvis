package com.coolxer.model.dih.vo;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunInteraction;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class AgentRunVo {
    private String runId;
    private String chatId;
    private String chatType;
    private String agentType;
    private String model;
    private AgentRunStatus status;
    private String statusLabel;
    private String goal;
    private AgentRunPlan plan;
    private Integer planRevision;
    private String currentStepId;
    private Integer decisionSteps;
    private Integer toolCalls;
    private Long effectiveMillis;
    private Integer maxSteps;
    private Integer maxDurationSeconds;
    private Integer maxToolCalls;
    private Long lastEventSequence;
    private String lastError;
    private String finalAnswer;
    private String pausedReason;
    private String pendingAction;
    private AgentRunInteraction pendingInteraction;
    private Integer attemptCount;
    private Integer recoveryCount;
    private Integer truncationCount;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;
    private List<String> skillIds;
    private boolean steered;

    public static AgentRunVo from(AgentRun run) {
        AgentRunVo vo = new AgentRunVo();
        vo.setRunId(run.getRunId());
        vo.setChatId(run.getChatId());
        vo.setChatType(run.getChatType());
        vo.setAgentType(run.getAgentType());
        vo.setModel(run.getModel());
        vo.setStatus(run.getStatus());
        vo.setStatusLabel(run.getStatus() == null ? "" : run.getStatus().getDescription());
        vo.setGoal(run.getGoal());
        vo.setPlan(parsePlan(run.getPlanJson()));
        vo.setPlanRevision(run.getPlanRevision());
        vo.setCurrentStepId(run.getCurrentStepId());
        vo.setDecisionSteps(run.getDecisionSteps());
        vo.setToolCalls(run.getToolCalls());
        vo.setEffectiveMillis(run.getEffectiveMillis());
        vo.setMaxSteps(run.getMaxSteps());
        vo.setMaxDurationSeconds(run.getMaxDurationSeconds());
        vo.setMaxToolCalls(run.getMaxToolCalls());
        vo.setLastEventSequence(run.getLastEventSequence());
        vo.setLastError(run.getLastError());
        vo.setFinalAnswer(run.getFinalAnswer());
        vo.setPausedReason(run.getPausedReason());
        vo.setPendingAction(run.getPendingAction());
        vo.setPendingInteraction(AgentRunInteraction.fromRun(run));
        vo.setAttemptCount(run.getAttemptCount());
        vo.setRecoveryCount(run.getRecoveryCount());
        vo.setTruncationCount(run.getTruncationCount());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setCreateTime(run.getCreateTime());
        vo.setUpdateTime(run.getUpdateTime());
        vo.setSkillIds(parseSkillIds(run.getSkillIds()));
        return vo;
    }

    private static AgentRunPlan parsePlan(String json) {
        if (json == null || json.isBlank()) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
        try {
            return JacksonUtil.toObject(json, AgentRunPlan.class);
        } catch (RuntimeException e) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
    }

    private static List<String> parseSkillIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JacksonUtil.toList(json, new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
