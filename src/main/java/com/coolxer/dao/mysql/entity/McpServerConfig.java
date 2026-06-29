package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.dih.dto.McpServerDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * MCP客户端服务配置。
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_AI_MCP_SERVER)
public class McpServerConfig extends BaseEntity {

    /**
     * 服务标识，用于区分多个外部 MCP 服务。
     */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /**
     * 服务名称。
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 服务描述。
     */
    @Column(name = "description", length = 512)
    private String description;

    /**
     * MCP SSE 基础地址，例如 http://127.0.0.1:11002。
     */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /**
     * SSE endpoint，默认 /sse。
     */
    @Column(name = "sse_endpoint", length = 512)
    private String sseEndpoint;

    /**
     * 固定 HTTP 请求头，JSON对象格式。
     */
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    /**
     * 是否启用。
     */
    @Column(name = "enabled", columnDefinition = "bit default 1")
    private Boolean enabled = true;

    /**
     * MCP请求超时秒数。
     */
    @Column(name = "request_timeout_seconds")
    private Integer requestTimeoutSeconds = 30;

    /**
     * HTTP连接超时秒数。
     */
    @Column(name = "connect_timeout_seconds")
    private Integer connectTimeoutSeconds = 10;

    /**
     * 最近一次连接是否成功。
     */
    @Column(name = "connected", columnDefinition = "bit default 0")
    private Boolean connected = false;

    /**
     * 最近一次连接错误。
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * 最近一次连接成功时间。
     */
    @Column(name = "last_connected_time")
    private Date lastConnectedTime;

    public void updateFromDto(McpServerDto dto) {
        if (dto.getCode() != null) {
            this.code = dto.getCode();
        }
        if (dto.getName() != null) {
            this.name = dto.getName();
        }
        if (dto.getDescription() != null) {
            this.description = dto.getDescription();
        }
        if (dto.getBaseUrl() != null) {
            this.baseUrl = dto.getBaseUrl();
        }
        if (dto.getSseEndpoint() != null) {
            this.sseEndpoint = dto.getSseEndpoint();
        }
        if (dto.getHeaders() != null) {
            this.headers = dto.getHeaders();
        }
        if (dto.getEnabled() != null) {
            this.enabled = dto.getEnabled();
        }
        if (dto.getRequestTimeoutSeconds() != null) {
            this.requestTimeoutSeconds = dto.getRequestTimeoutSeconds();
        }
        if (dto.getConnectTimeoutSeconds() != null) {
            this.connectTimeoutSeconds = dto.getConnectTimeoutSeconds();
        }
    }
}
