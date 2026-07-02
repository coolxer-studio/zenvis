package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.McpServerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerVoTest {

    @Test
    void doesNotExposeRawHeaders() {
        McpServerConfig config = new McpServerConfig();
        config.setId(1);
        config.setCode("demo");
        config.setName("Demo");
        config.setBaseUrl("https://example.com");
        config.setHeaders("{\"Authorization\":\"Bearer secret\",\"X-Tenant\":\"demo\"}");

        McpServerVo vo = new McpServerVo(config);

        assertThat(vo.getHeaders()).isNull();
        assertThat(vo.getHeaderNames()).containsExactly("Authorization", "X-Tenant");
    }
}
