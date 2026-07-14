package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Entity
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_AI_MCP_INVOCATION)
public class McpToolInvocation extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "tool_key", nullable = false, length = 255)
    private String toolKey;

    @Column(name = "tool_name", nullable = false, length = 255)
    private String toolName;

    @Column(name = "server_name", length = 128)
    private String serverName;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private McpToolRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private McpInvocationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_snapshot", nullable = false, length = 16)
    private McpApprovalPolicy policySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_scope", columnDefinition = "VARCHAR(32)")
    private McpApprovalScope approvalScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private McpInvocationStatus status;

    @Column(name = "requester_user_id")
    private Integer requesterUserId;

    @Column(name = "chat_id", length = 128)
    private String chatId;

    @Column(name = "turn_id", length = 64)
    private String turnId;

    @Column(name = "agent_type", length = 64)
    private String agentType;

    @Column(name = "analysis_task_id")
    private Integer analysisTaskId;

    @Column(name = "task_execution_id", length = 64)
    private String taskExecutionId;

    @Column(name = "mcp_session_id", length = 255)
    private String mcpSessionId;

    @Column(name = "mcp_client_info", length = 1000)
    private String mcpClientInfo;

    @Column(name = "arguments_summary", columnDefinition = "TEXT")
    private String argumentsSummary;

    @Column(name = "arguments_digest", length = 64)
    private String argumentsDigest;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "decision_by")
    private Integer decisionBy;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "decision_time")
    private Date decisionTime;

    @Column(name = "expire_time")
    private Date expireTime;

    @Column(name = "finish_time")
    private Date finishTime;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion = 0L;
}
