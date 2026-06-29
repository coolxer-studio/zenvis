package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.McpServerConfig;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class McpServerVo implements Serializable {

    private Integer id;

    private String code;

    private String name;

    private String description;

    private String baseUrl;

    private String sseEndpoint;

    private String headers;

    private Boolean enabled;

    private Integer requestTimeoutSeconds;

    private Integer connectTimeoutSeconds;

    private Boolean connected;

    private String lastError;

    private Date lastConnectedTime;

    private Integer toolCount;

    private Date createTime;

    private Date updateTime;

    public McpServerVo(McpServerConfig config) {
        this(config, null);
    }

    public McpServerVo(McpServerConfig config, Integer toolCount) {
        if (config == null) {
            return;
        }
        this.id = config.getId();
        this.code = config.getCode();
        this.name = config.getName();
        this.description = config.getDescription();
        this.baseUrl = config.getBaseUrl();
        this.sseEndpoint = config.getSseEndpoint();
        this.headers = config.getHeaders();
        this.enabled = config.getEnabled();
        this.requestTimeoutSeconds = config.getRequestTimeoutSeconds();
        this.connectTimeoutSeconds = config.getConnectTimeoutSeconds();
        this.connected = config.getConnected();
        this.lastError = config.getLastError();
        this.lastConnectedTime = config.getLastConnectedTime();
        this.toolCount = toolCount;
        this.createTime = config.getCreateTime();
        this.updateTime = config.getUpdateTime();
    }
}
