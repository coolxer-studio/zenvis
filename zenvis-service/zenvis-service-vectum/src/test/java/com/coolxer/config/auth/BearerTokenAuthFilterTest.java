package com.coolxer.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearer Token 认证过滤器测试
 */
class BearerTokenAuthFilterTest {

    private static final String TEST_TOKEN = "test-token";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"/sse", "/mcp/message", "/vectum/api/v1/task/all"})
    void shouldRejectProtectedPathWithoutToken(String path) throws Exception {
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(enabledProperties(), objectMapper);
        MockHttpServletResponse response = execute(filter, request(path), new AtomicBoolean());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(response.getContentAsString()).contains("未认证或认证失败");
    }

    @Test
    void shouldRejectProtectedPathWithInvalidToken() throws Exception {
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(enabledProperties(), objectMapper);
        MockHttpServletRequest request = request("/sse");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = execute(filter, request, chainCalled);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void shouldAllowProtectedPathWithValidToken() throws Exception {
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(enabledProperties(), objectMapper);
        MockHttpServletRequest request = request("/mcp/message");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_TOKEN);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = execute(filter, request, chainCalled);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/index",
            "/sdk/sdk.js",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/actuator/health"
    })
    void shouldAllowPublicPathWithoutToken(String path) throws Exception {
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(enabledProperties(), objectMapper);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = execute(filter, request(path), chainCalled);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void shouldAllowAllPathsWhenAuthDisabled() throws Exception {
        AuthProperties authProperties = enabledProperties();
        authProperties.setEnabled(false);
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(authProperties, objectMapper);
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        MockHttpServletResponse response = execute(filter, request("/sse"), chainCalled);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    private AuthProperties enabledProperties() throws Exception {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken(TEST_TOKEN);
        authProperties.afterPropertiesSet();
        return authProperties;
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private MockHttpServletResponse execute(
            BearerTokenAuthFilter filter,
            MockHttpServletRequest request,
            AtomicBoolean chainCalled) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (servletRequest, servletResponse) -> {
            chainCalled.set(true);
            servletResponse.setContentType("text/plain");
            servletResponse.getWriter().write("ok");
        };
        filter.doFilter(request, response, filterChain);
        return response;
    }
}
