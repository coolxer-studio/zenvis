package com.coolxer.aop;

import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.utils.JacksonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Lightweight Bearer Token authentication for Spring AI MCP endpoints.
 */
@Slf4j
@Component
public class McpBearerTokenInterceptor implements HandlerInterceptor {

    private static final String BEARER_SCHEME = "Bearer";
    private static final String WWW_AUTHENTICATE_VALUE = "Bearer realm=\"zenvis-mcp\"";
    private static final String AUTH_FAILED_MESSAGE = "MCP Bearer Token认证失败";

    private final String bearerToken;

    public McpBearerTokenInterceptor(@Value("${app.security.mcp.bearer-token:}") String bearerToken) {
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!StringUtils.hasText(bearerToken)) {
            log.warn("MCP Bearer Token未配置，拒绝访问: uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response);
            return false;
        }

        String requestToken = resolveBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!constantTimeEquals(bearerToken, requestToken)) {
            writeUnauthorizedResponse(response);
            return false;
        }

        return true;
    }

    private static String resolveBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "";
        }

        String header = authorizationHeader.trim();
        if (header.length() <= BEARER_SCHEME.length()
                || !header.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length())
                || !Character.isWhitespace(header.charAt(BEARER_SCHEME.length()))) {
            return "";
        }

        return header.substring(BEARER_SCHEME.length()).trim();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(Objects.requireNonNull(JacksonUtil.toJson(
                    ResponseWrap.fail(HttpServletResponse.SC_UNAUTHORIZED, AUTH_FAILED_MESSAGE))));
        }
    }
}
