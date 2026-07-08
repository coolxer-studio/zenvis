package com.coolxer.aop;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.configuration.CustomWebConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * 权限验证拦截器
 */
@Slf4j
@Component
@Order(2)
public class AuthorityInterceptor extends AbstractInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CustomWebConfig customWebConfig;

    /**
     * 需要放行的url集合
     */
    private List<String> releases = Arrays.asList(
            "/api/v1/system/about/info",
            "/api/v1/system/login/sign-in",
            "/api/v1/system/login/kaptcha",
            "/api/v1/system/login/encrypt/key",
            "/api/v1/system/login/sign-out",
            "/api/v1/dih/health",
            "/api/v1/lubinsun/tasks/ingest"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String requestUri = request.getRequestURI();
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


}
