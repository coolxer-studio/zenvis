package com.coolxer.model.dih.vo;

import com.coolxer.commons.enums.McpInvocationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolCallResultVo {
    private String requestId;
    private McpInvocationStatus status;
    private Object result;
    private String error;
}
