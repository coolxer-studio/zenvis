package com.coolxer.service.dih.mcp;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.dih.dto.McpServerDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void resolvesRuntimePropertiesInBaseUrlAndHeadersWithoutPersistingSecrets() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "11001")
                .withProperty("app.security.api.bearer-token", "runtime-secret");
        McpClientServiceImpl service = new McpClientServiceImpl(
                null,
                new ObjectMapper(),
                environment,
                "1.0.0",
                true,
                false
        );

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service, "validateBaseUrl", "http://127.0.0.1:${server.port}"))
                .doesNotThrowAnyException();
        Map<String, String> headers = ReflectionTestUtils.invokeMethod(
                service,
                "parseHeaders",
                "{\"Authorization\":\"Bearer ${app.security.api.bearer-token}\"}"
        );

        assertThat(headers).containsEntry("Authorization", "Bearer runtime-secret");
    }

    @Test
    void rejectsUnresolvedRuntimeProperty() {
        McpClientServiceImpl service = new McpClientServiceImpl(
                null,
                new ObjectMapper(),
                new MockEnvironment(),
                "1.0.0",
                true,
                false
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateBaseUrl", "http://127.0.0.1:${missing.port}"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("无法解析");
    }
}
