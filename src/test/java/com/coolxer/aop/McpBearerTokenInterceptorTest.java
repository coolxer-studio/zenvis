package com.coolxer.aop;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class McpBearerTokenInterceptorTest {

    @Test
    void shouldRejectWhenConfiguredTokenIsMissing() throws Exception {
        McpBearerTokenInterceptor interceptor = new McpBearerTokenInterceptor("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderIsInvalid() throws Exception {
        McpBearerTokenInterceptor interceptor = new McpBearerTokenInterceptor("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"zenvis-mcp\"");
    }

    @Test
    void shouldAllowWhenAuthorizationHeaderHasExpectedBearerToken() throws Exception {
        McpBearerTokenInterceptor interceptor = new McpBearerTokenInterceptor("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
