package com.coolxer.service.dih.mcp;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpClientServiceImplTest {

    @Test
    void infoIncludesHeadersForEditing() {
        McpServerConfigRepository repository = mock(McpServerConfigRepository.class);
        McpServerConfig config = new McpServerConfig();
        config.setId(52);
        config.setHeaders("{\"Authorization\":\"Bearer ${MCP_TOKEN}\"}");
        when(repository.findById(52)).thenReturn(Optional.of(config));
        McpClientServiceImpl service = new McpClientServiceImpl(
                repository,
                new ObjectMapper(),
                "1.0.0",
                true,
                false
        );

        McpServerVo result = service.info(52);

        assertThat(result.getHeaders()).isEqualTo("{\"Authorization\":\"Bearer ${MCP_TOKEN}\"}");
    }

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

    @Test
    void initializesEnabledMcpServersOnlyAfterApplicationIsReady() throws NoSuchMethodException {
        McpServerConfigRepository repository = mock(McpServerConfigRepository.class);
        McpServerConfig config = new McpServerConfig()
                .setCode("jmr")
                .setEnabled(true);
        config.setId(52);
        when(repository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(config));
        McpClientServiceImpl service = spy(new McpClientServiceImpl(
                repository,
                new ObjectMapper(),
                new MockEnvironment().withProperty("server.port", "11001"),
                "1.0.0",
                true,
                false
        ));
        doReturn(new McpServerVo(config, 3)).when(service).refresh(52);

        service.initializeEnabledServersAfterStartup();

        verify(service).refresh(52);

        Method initializer = McpClientServiceImpl.class
                .getDeclaredMethod("initializeEnabledServersAfterStartup");
        assertThat(initializer.getAnnotation(EventListener.class).value())
                .containsExactly(ApplicationReadyEvent.class);
        assertThat(Arrays.stream(McpClientServiceImpl.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(PostConstruct.class));
    }
}
