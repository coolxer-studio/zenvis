package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
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
@Table(name = MysqlFinalTableName.T_AI_MCP_TOOL_POLICY)
public class McpToolPolicyConfig extends BaseEntity {

    @Column(name = "tool_key", nullable = false, unique = true, length = 255)
    private String toolKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private McpToolSourceType sourceType;

    @Column(name = "server_id")
    private Integer serverId;

    @Column(name = "server_code", length = 64)
    private String serverCode;

    @Column(name = "server_name", length = 128)
    private String serverName;

    @Column(name = "tool_name", nullable = false, length = 255)
    private String toolName;

    @Column(name = "ai_tool_name", length = 255)
    private String aiToolName;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "read_only_hint")
    private Boolean readOnlyHint;

    @Column(name = "destructive_hint")
    private Boolean destructiveHint;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private McpToolRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_policy", nullable = false, length = 16)
    private McpApprovalPolicy defaultPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "configured_policy", length = 16)
    private McpApprovalPolicy configuredPolicy;

    @Column(name = "available", nullable = false)
    private Boolean available = true;

    @Column(name = "last_seen_time")
    private Date lastSeenTime;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion = 0L;

    public McpApprovalPolicy effectivePolicy() {
        return configuredPolicy == null ? defaultPolicy : configuredPolicy;
    }
}
