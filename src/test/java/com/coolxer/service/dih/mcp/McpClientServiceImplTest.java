package com.coolxer.service.dih.mcp;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.dih.dto.McpServerDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientServiceImplTest {

    @Test
    void createRejectsLocalhostBaseUrlByDefault() {
        McpClientServiceImpl service = new McpClientServiceImpl(
                null,
                new ObjectMapper(),
                "1.0.0",
                false,
                false
        );
        McpServerDto dto = new McpServerDto();
        dto.setCode("local");
        dto.setName("Local MCP");
        dto.setBaseUrl("http://127.0.0.1:11002");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("MCP服务地址不允许指向本机或内网地址");
    }
}
