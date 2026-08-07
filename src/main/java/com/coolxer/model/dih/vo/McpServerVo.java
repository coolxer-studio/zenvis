package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class McpServerVo implements Serializable {

    private Integer id;

    private String code;

    private String name;

    private String description;

    private String baseUrl;

    private String sseEndpoint;

    private String headers;

    private List<String> headerNames;

    private Boolean enabled;

    private Integer requestTimeoutSeconds;

    private Integer connectTimeoutSeconds;

    private Boolean connected;

    private String lastError;

    private Date lastConnectedTime;

    private Integer toolCount;

    private String source;

    private Date createTime;

    private Date updateTime;

    public McpServerVo(McpServerConfig config) {
        this(config, null);
    }

    public McpServerVo(McpServerConfig config, Integer toolCount) {
        this(config, toolCount, false);
    }

    public McpServerVo(McpServerConfig config, Integer toolCount, boolean includeHeaders) {
        if (config == null) {
            return;
        }
        this.id = config.getId();
        this.code = config.getCode();
        this.name = config.getName();
        this.description = config.getDescription();
        this.baseUrl = config.getBaseUrl();
        this.sseEndpoint = config.getSseEndpoint();
        this.headers = includeHeaders ? config.getHeaders() : null;
        this.headerNames = resolveHeaderNames(config.getHeaders());
        this.enabled = config.getEnabled();
        this.requestTimeoutSeconds = config.getRequestTimeoutSeconds();
        this.connectTimeoutSeconds = config.getConnectTimeoutSeconds();
        this.connected = config.getConnected();
        this.lastError = config.getLastError();
        this.lastConnectedTime = config.getLastConnectedTime();
        this.toolCount = toolCount;
        this.source = config.getSource();
        this.createTime = config.getCreateTime();
        this.updateTime = config.getUpdateTime();
    }

    private static List<String> resolveHeaderNames(String headers) {
        try {
            Map<String, Object> parsed = JacksonConfig.OBJECT_MAPPER.readValue(headers, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed.keySet().stream().toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
