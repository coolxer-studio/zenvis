package com.coolxer.aop;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 权限验证拦截器
 */
@Slf4j
@Component
@Order(2)
public class AuthorityInterceptor extends AbstractInterceptor {

    public static final String API_BEARER_USER_ID_ATTR = "zenvis.apiBearer.userId";
    public static final String API_BEARER_USER_NAME_ATTR = "zenvis.apiBearer.userName";

    private static final String BEARER_SCHEME = "Bearer";

    private static final Set<String> PUBLIC_BUSINESS_SERVICE_REPORT_PATHS = Set.of(
            "/api/v1/public/business-services/heartbeat",
            "/api/v1/public/business-services/events"
    );

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.security.api.bearer-token:}")
    private String apiBearerToken;

    @Value("${app.security.api.bearer-user:admin@admin.com}")
    private String apiBearerUser;

    /**
     * 需要放行的url集合
     */
    private List<String> releases = Arrays.asList(
            "/api/v1/system/about/info",
            "/api/v1/system/login/sign-in",
            "/api/v1/system/login/kaptcha",
            "/api/v1/system/login/encrypt/key",
            "/api/v1/system/login/sign-out",
            "/api/v1/dih/health"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String requestUri = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && PUBLIC_BUSINESS_SERVICE_REPORT_PATHS.contains(requestUri)) {
            return true;
        }
        List<Boolean> booleans = releases.stream().map(requestUri::contains)
                .toList();
        if (booleans.contains(true)) {
            return true;
        }

        String referer = request.getHeader("referer");
        String host = request.getHeader("host");
        response.setCharacterEncoding(Charset.defaultCharset().toString());

        // 防止CSRF跨站点请求伪造
//        if (referer == null) {
//            writErrorInfoToResponse(response, ResultCodeEnum.PLEASE_LOGIN);
//            return false;
//        } else {
//            if (!referer.contains(host)) {
//                writErrorInfoToResponse(response, ResultCodeEnum.PLEASE_LOGIN);
//                return false;
//            }
//        }

        if (requestUri.contains(customWebConfig.getNeedCheckPath())) {

            BearerAuthResult bearerAuthResult = authenticateByBearerToken(request, response);
            if (bearerAuthResult == BearerAuthResult.AUTHENTICATED) {
                return true;
            }
            if (bearerAuthResult == BearerAuthResult.REJECTED) {
                return false;
            }

            if (request.getRequestedSessionId() == null) {
                writErrorInfoToResponse(response, ResultCodeEnum.PLEASE_LOGIN);
                return false;
            } else {

                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(request.getRequestedSessionId()))) {
                    writErrorInfoToResponse(response, ResultCodeEnum.PLEASE_LOGIN);
                    return false;
                }

            }

            // 重置session超时时间，防止用户重新登录
            stringRedisTemplate.expire(request.getRequestedSessionId(), customWebConfig.getSessionTimeout());


        }
        return true;
    }

    private BearerAuthResult authenticateByBearerToken(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        String requestToken = resolveBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(requestToken)) {
            return BearerAuthResult.NOT_PRESENT;
        }

        if (!StringUtils.hasText(apiBearerToken)) {
            log.warn("API Bearer Token未配置，拒绝访问: uri={}", request.getRequestURI());
            writErrorInfoToResponse(response, ResultCodeEnum.NO_AUTHORITY);
            return BearerAuthResult.REJECTED;
        }

        if (!constantTimeEquals(apiBearerToken.trim(), requestToken)) {
            writErrorInfoToResponse(response, ResultCodeEnum.PLEASE_LOGIN);
            return BearerAuthResult.REJECTED;
        }

        User user = userRepository.findByEmail(apiBearerUser);
        if (user == null) {
            log.warn("API Bearer Token用户不存在: user={}, uri={}", apiBearerUser, request.getRequestURI());
            writErrorInfoToResponse(response, ResultCodeEnum.NO_AUTHORITY);
            return BearerAuthResult.REJECTED;
        }

        request.setAttribute(API_BEARER_USER_ID_ATTR, user.getId());
        request.setAttribute(API_BEARER_USER_NAME_ATTR, user.getName());
        return BearerAuthResult.AUTHENTICATED;
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

    private enum BearerAuthResult {
        AUTHENTICATED,
        NOT_PRESENT,
        REJECTED
    }


}
