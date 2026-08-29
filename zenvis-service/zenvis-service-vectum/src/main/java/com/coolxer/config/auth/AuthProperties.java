package com.coolxer.config.auth;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 认证配置属性
 * <p>
 * 管理 MCP 与 REST API 的 Bearer Token 认证开关和密钥。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "vectum.auth")
public class AuthProperties implements InitializingBean {

    private boolean enabled = true;

    private String token;

    /**
     * 校验认证配置是否完整。
     */
    @Override
    public void afterPropertiesSet() {
        if (enabled && !StringUtils.hasText(token)) {
            throw new IllegalStateException("vectum.auth.token must be configured when authentication is enabled");
        }
    }
}
