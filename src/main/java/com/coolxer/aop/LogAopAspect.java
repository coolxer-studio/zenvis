package com.coolxer.aop;

import com.coolxer.utils.JacksonUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * 请求拦截器
 */
@Slf4j
@Aspect
@Component
public class LogAopAspect {

    /**
     * 统计请求处理时间
     */
    ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Pointcut("execution(public * com.coolxer.controller..*.*(..))")
    private void controllerAspect() {
    }


    @Pointcut("controllerAspect()")
    private void logAspect() {
    }

    @Before("logAspect()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {

        log.info("=== 开始 ===");
        startTime.set(System.currentTimeMillis());
        HttpServletRequest request = currentHttpRequest();
        if (request == null) {
            log.debug("Skip web request logging outside request context: {}.{}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());
            return;
        }

        log.info("请求地址: {} {}", request.getRequestURL().toString(), request.getMethod());
        log.info("类名方法: {}.{}", joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
        log.info("远程地址: {}", request.getRemoteAddr());
        // 打印请求参数
        Object[] args = joinPoint.getArgs();

        Object[] arguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest) {
                arguments[i] = "request";
            } else if (args[i] instanceof ServletResponse) {
                arguments[i] = "response";
            } else if (args[i] instanceof MultipartFile) {
                arguments[i] = "file";
            } else {
                arguments[i] = args[i];
            }

        }

        if (log.isInfoEnabled()) {
            log.info("请求的参数: {}", JacksonUtil.toJson(arguments));
        }
    }

    @AfterReturning(returning = "ret", pointcut = "logAspect()")
    public void doAfterReturning(Object ret) throws Throwable {

        Long startedAt = startTime.get();
        if (startedAt == null) {
            return;
        }

        // 接口耗时(ms)
        long timeConsuming = System.currentTimeMillis() - startedAt;

        // 接口耗时大于等于2s时打印日志
        if (timeConsuming >= 2000) {
            HttpServletRequest request1 = currentHttpRequest();
            if (Objects.isNull(request1)) {
                startTime.remove();
                return;
            }

            log.warn("URL [{}]，Filter condition [{}]，Time consuming [{}]ms!!!",
                    request1.getRequestURL().toString(), JacksonUtil.toJson(request1.getParameterMap()),
                    timeConsuming);
        }

        startTime.remove();
        log.info("=== 结束 ===");
    }

    private HttpServletRequest currentHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

}
