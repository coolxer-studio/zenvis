package com.coolxer.service.dih.mcp;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.dih.dto.McpServerDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientServiceImplTest {

    @Test
    void createRejectsLocalhostBaseUrlWhenPrivateUrlsDisabled() {
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

    @Test
    void validateBaseUrlAllowsLocalhostWhenPrivateUrlsEnabled() {
        McpClientServiceImpl service = new McpClientServiceImpl(
                null,
                new ObjectMapper(),
                "1.0.0",
                true,
                false
        );

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateBaseUrl", "http://127.0.0.1:11002"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "validateBaseUrl", "http://192.168.1.10:11002"))
                .doesNotThrowAnyException();
    }
}
