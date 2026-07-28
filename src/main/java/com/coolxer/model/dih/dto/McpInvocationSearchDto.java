package com.coolxer.model.dih.dto;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.model.base.dto.PageDto;
import lombok.Data;

@Data
public class McpInvocationSearchDto extends PageDto {

    /** 匹配请求 ID、工具、服务、聊天 ID 和 Agent 类型。 */
    private String keyword;

    /** 精确匹配请求 ID。 */
    private String requestId;

    private McpInvocationChannel channel;

    private McpInvocationStatus status;

    private McpApprovalPolicy policy;

    private McpApprovalScope approvalScope;

    private Integer requesterUserId;

    private Integer decisionBy;

    private Integer analysisTaskId;

    private String executionId;
}
