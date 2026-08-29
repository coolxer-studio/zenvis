package com.coolxer.config.auth;

import com.coolxer.model.vo.ResponseWrap;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bearer Token 认证过滤器
 * <p>
 * 仅保护 MCP SSE、MCP message 以及 REST API 入口。
 * </p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int UNAUTHORIZED_CODE = HttpServletResponse.SC_UNAUTHORIZED;
    private static final String UNAUTHORIZED_MESSAGE = "未认证或认证失败";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造 Bearer Token 认证过滤器。
     * @param authProperties 认证配置
     * @param objectMapper JSON 序列化器
     */
    public BearerTokenAuthFilter(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行受保护入口的 Bearer Token 校验。
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        writeUnauthorizedResponse(response);
    }

    /**
     * 判断当前请求是否需要跳过认证。
     * @param request HTTP 请求
     * @return true 表示跳过认证
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authProperties.isEnabled() || !isProtectedPath(getRequestPath(request));
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }

        String providedToken = authorization.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(providedToken) && constantTimeEquals(authProperties.getToken(), providedToken);
    }

    private boolean isProtectedPath(String path) {
        return "/sse".equals(path) || "/mcp/message".equals(path) || path.startsWith("/vectum/api/");
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }
        return requestUri.startsWith("/") ? requestUri : "/" + requestUri;
    }

    private boolean constantTimeEquals(String expectedToken, String providedToken) {
        byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedToken.getBytes(StandardCharsets.UTF_8);
        int maxLength = Math.max(expectedBytes.length, providedBytes.length);
        int diff = expectedBytes.length ^ providedBytes.length;

        for (int i = 0; i < maxLength; i++) {
            byte expectedByte = i < expectedBytes.length ? expectedBytes[i] : 0;
            byte providedByte = i < providedBytes.length ? providedBytes[i] : 0;
            diff |= expectedByte ^ providedByte;
        }
        return diff == 0;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(UNAUTHORIZED_CODE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                new ResponseWrap<>(UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE, null));
    }
}
