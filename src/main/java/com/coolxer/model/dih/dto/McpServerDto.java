package com.coolxer.model.dih.dto;

import lombok.Data;

@Data
public class McpServerDto {

    private String code;

    private String name;

    private String description;

    private String baseUrl;

    private String sseEndpoint;

    /**
     * 固定 HTTP 请求头，JSON对象格式，例如 {"Authorization":"Bearer xxx"}。
     */
    private String headers;

    private Boolean enabled;

    private Integer requestTimeoutSeconds;

    private Integer connectTimeoutSeconds;
}
