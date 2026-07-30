package com.coolxer.model.dih.vo;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class McpApprovalVo {
    private String requestId;
    private String toolKey;
    private String toolName;
    private McpToolSourceType sourceType;
    private String serverName;
    private String description;
    private McpInvocationChannel channel;
    private McpApprovalPolicy policy;
    private McpApprovalScope approvalScope;
    private McpInvocationStatus status;
    private Integer requesterUserId;
    private String chatId;
    private String turnId;
    private String agentType;
    private Integer analysisTaskId;
    private String taskExecutionId;
    private String arguments;
    private String result;
    private Long resultLength;
    private String errorSummary;
    private Integer decisionBy;
    private String decisionComment;
    private Date decisionTime;
    private Date expireTime;
    private Date finishTime;
    private Long durationMillis;
    private Date createTime;
    private String riskLevel;
    private Boolean sessionApprovalAllowed;

    public McpApprovalVo(McpToolInvocation invocation) {
        this.requestId = invocation.getRequestId();
        this.toolKey = invocation.getToolKey();
        this.toolName = invocation.getToolName();
        this.sourceType = invocation.getToolKey() != null && invocation.getToolKey().startsWith("external::")
                ? McpToolSourceType.EXTERNAL : McpToolSourceType.LOCAL;
        this.serverName = invocation.getServerName();
        this.description = invocation.getDescription();
        this.channel = invocation.getChannel();
        this.policy = invocation.getPolicySnapshot();
        this.approvalScope = invocation.getApprovalScope();
        this.status = invocation.getStatus();
        this.requesterUserId = invocation.getRequesterUserId();
        this.chatId = invocation.getChatId();
        this.turnId = invocation.getTurnId();
        this.agentType = invocation.getAgentType();
        this.analysisTaskId = invocation.getAnalysisTaskId();
        this.taskExecutionId = invocation.getTaskExecutionId();
        this.arguments = invocation.getArguments();
        this.result = invocation.getResult();
        this.resultLength = invocation.getResultLength();
        this.errorSummary = invocation.getErrorSummary();
        this.decisionBy = invocation.getDecisionBy();
        this.decisionComment = invocation.getDecisionComment();
        this.decisionTime = invocation.getDecisionTime();
        this.expireTime = invocation.getExpireTime();
        this.finishTime = invocation.getFinishTime();
        this.durationMillis = invocation.getDurationMillis();
        this.createTime = invocation.getCreateTime();
        this.riskLevel = invocation.getRiskLevel() == null
                ? "unknown" : invocation.getRiskLevel().name().toLowerCase(java.util.Locale.ROOT);
        this.sessionApprovalAllowed =
                invocation.getChannel() == McpInvocationChannel.CHAT_AGENT
                        && !com.coolxer.service.dih.mcp.McpInvocationContext
                        .isExplicitApprovalDemoClient(
                                invocation.getMcpClientInfo());
    }
}
