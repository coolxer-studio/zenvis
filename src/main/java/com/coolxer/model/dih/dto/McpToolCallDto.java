package com.coolxer.model.dih.dto;

import lombok.Data;

import java.util.Map;

@Data
public class McpToolCallDto {

    private Integer serverId;

    private String serverCode;

    private String name;

    private Map<String, Object> arguments;
}
