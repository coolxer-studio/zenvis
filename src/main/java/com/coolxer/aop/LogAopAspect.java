package com.coolxer.aop;

import com.coolxer.utils.JacksonUtil;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleConfigDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleDeleteDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleUpdateDto;
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
import java.util.LinkedHashMap;
import java.util.Map;

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


    @Pointcut("controllerAspect()"
            + " && !@annotation(com.coolxer.aop.SkipRequestLog)"
            + " && !@within(com.coolxer.aop.SkipRequestLog)")
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
        boolean retrievalEndpoint = request.getRequestURI().startsWith("/api/v1/retrieval");

        Object[] arguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest) {
                arguments[i] = "request";
            } else if (args[i] instanceof ServletResponse) {
                arguments[i] = "response";
            } else if (args[i] instanceof MultipartFile) {
                arguments[i] = "file";
            } else if (args[i] instanceof RetrievalRequestDto retrievalRequest) {
                arguments[i] = sanitizeRetrievalRequest(retrievalRequest);
            } else if (args[i] instanceof RetrievalRuleConfigDto ruleConfig) {
                arguments[i] = sanitizeRuleConfig(ruleConfig);
            } else if (args[i] instanceof RetrievalRuleDeleteDto deleteRequest) {
                Map<String, Object> safeDelete = new LinkedHashMap<>();
                safeDelete.put("id", deleteRequest.getId());
                arguments[i] = safeDelete;
            } else if (retrievalEndpoint && "listCandidateValue".equals(joinPoint.getSignature().getName()) && i == 3) {
                arguments[i] = args[i] == null ? null : "***";
            } else {
                arguments[i] = args[i];
            }

        }

        if (log.isInfoEnabled()) {
            log.info("请求的参数: {}", JacksonUtil.toJson(arguments));
        }
    }

    private Map<String, Object> sanitizeRetrievalRequest(RetrievalRequestDto request) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("id", request.getId());
        safe.put("type", request.getType());
        safe.put("entity", request.getEntity());
        safe.put("rule_name", request.getRuleName());
        safe.put("criteria_count", request.getCriteriaList() == null ? 0 : request.getCriteriaList().size());
        safe.put("display_count", request.getDisplayList() == null ? 0 : request.getDisplayList().stream()
                .filter(Objects::nonNull)
                .mapToInt(display -> display.getAttributeList() == null ? 0 : display.getAttributeList().size()).sum());
        safe.put("sql", request.getSql() == null ? null : "***");
        safe.put("token", request.getToken() == null ? null : "***");
        safe.put("page", request.getPage());
        safe.put("size", request.getSize());
        safe.put("sort_by", request.getSortBy());
        safe.put("order", request.getOrder());
        return safe;
    }

    private Map<String, Object> sanitizeRuleConfig(RetrievalRuleConfigDto request) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (request instanceof RetrievalRuleUpdateDto update) {
            safe.put("id", update.getId());
            safe.put("rule_name", update.getRuleName());
        } else if (request instanceof RetrievalRuleCreateDto create) {
            safe.put("rule_name", create.getRuleName());
        }
        safe.put("type", request.getType());
        safe.put("entity", request.getEntity());
        safe.put("criteria_count", request.getCriteriaList() == null ? 0 : request.getCriteriaList().size());
        safe.put("display_count", request.getDisplayList() == null ? 0 : request.getDisplayList().stream()
                .filter(Objects::nonNull)
                .mapToInt(display -> display.getAttributeList() == null ? 0 : display.getAttributeList().size()).sum());
        safe.put("sql", request.getSql() == null ? null : "***");
        safe.put("page", request.getPage());
        safe.put("size", request.getSize());
        safe.put("sort_by", request.getSortBy());
        safe.put("order", request.getOrder());
        return safe;
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
                    request1.getRequestURL().toString(), JacksonUtil.toJson(sanitizeRequestParameters(request1)),
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

    private Map<String, Object> sanitizeRequestParameters(HttpServletRequest request) {
        Map<String, Object> safe = new LinkedHashMap<>();
        boolean retrievalEndpoint = request.getRequestURI().startsWith("/api/v1/retrieval");
        request.getParameterMap().forEach((name, values) -> {
            String lowerName = name.toLowerCase(java.util.Locale.ROOT);
            boolean sensitive = retrievalEndpoint && (lowerName.contains("sql")
                    || lowerName.contains("token")
                    || lowerName.contains("value")
                    || lowerName.equals("text"));
            safe.put(name, sensitive ? "***" : values);
        });
        return safe;
    }

}
