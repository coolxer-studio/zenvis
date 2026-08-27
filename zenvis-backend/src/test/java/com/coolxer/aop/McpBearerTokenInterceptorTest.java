package com.coolxer.aop;

import com.coolxer.service.dih.mcp.BuiltinMcpServiceDefinition;
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/retrieval/sse");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderIsInvalid() throws Exception {
        McpBearerTokenInterceptor interceptor = new McpBearerTokenInterceptor("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/retrieval/sse");
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
        for (BuiltinMcpServiceDefinition service : BuiltinMcpServiceDefinition.orderedValues()) {
            MockHttpServletRequest sseRequest = new MockHttpServletRequest("GET", service.sseEndpoint());
            sseRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
            MockHttpServletResponse sseResponse = new MockHttpServletResponse();
            MockHttpServletRequest messageRequest = new MockHttpServletRequest("POST", service.messageEndpoint());
            messageRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
            MockHttpServletResponse messageResponse = new MockHttpServletResponse();

            assertThat(interceptor.preHandle(sseRequest, sseResponse, new Object())).isTrue();
            assertThat(interceptor.preHandle(messageRequest, messageResponse, new Object())).isTrue();
            assertThat(sseResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(messageResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }
}
