package com.coolxer.model.dih.dto;

import lombok.Data;

import java.util.Map;

@Data
public class McpToolCallDto {

    private Integer serverId;

    private String serverCode;

    private String name;

    private Map<String, Object> arguments;

    /**
     * ASK策略批准后重试时携带的单次审批请求ID。
     */
    private String approvalRequestId;
}
