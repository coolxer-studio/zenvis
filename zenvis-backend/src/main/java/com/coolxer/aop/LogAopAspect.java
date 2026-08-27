package com.coolxer.aop;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.utils.JacksonUtil;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleConfigDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleDeleteDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleUpdateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
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
import java.util.Locale;
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
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

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

        startTime.set(System.currentTimeMillis());
        HttpServletRequest request = currentHttpRequest();
        if (request == null) {
            log.debug("Skip web request logging outside request context: {}.{}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());
            return;
        }

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
                arguments[i] = sanitizeArgument(args[i]);
            }

        }

        if (log.isInfoEnabled()) {
            log.info("HTTP请求开始 method={} uri={} handler={}.{} remote={} args={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    request.getRemoteAddr(),
                    JacksonUtil.toJson(arguments));
        }
    }

    private Object sanitizeArgument(Object argument) {
        if (argument == null) {
            return null;
        }
        try {
            JsonNode node = JacksonConfig.OBJECT_MAPPER.valueToTree(argument);
            redactSensitiveValues(node);
            return node;
        } catch (IllegalArgumentException ex) {
            return argument.getClass().getSimpleName();
        }
    }

    private void redactSensitiveValues(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.properties().forEach(entry -> {
                if (isSensitiveName(entry.getKey())) {
                    object.put(entry.getKey(), "***");
                } else {
                    redactSensitiveValues(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::redactSensitiveValues);
        }
    }

    private boolean isSensitiveName(String name) {
        String normalized = name == null ? ""
                : name.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("authcode")
                || normalized.contains("authorization")
                || normalized.contains("apikey")
                || normalized.contains("privatekey")
                || normalized.contains("accesskey")
                || normalized.contains("credential")
                || normalized.contains("sessionid")
                || normalized.contains("cookie");
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

        long timeConsuming = System.currentTimeMillis() - startedAt;
        HttpServletRequest request = currentHttpRequest();

        if (timeConsuming >= 2000) {
            if (request != null) {
                log.warn("HTTP慢请求 method={} uri={} params={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        JacksonUtil.toJson(sanitizeRequestParameters(request)),
                        timeConsuming);
            }
        }

        startTime.remove();
        if (request != null) {
            log.info("HTTP请求完成 method={} uri={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), timeConsuming);
        }
    }

    @AfterThrowing(pointcut = "logAspect()", throwing = "throwable")
    public void doAfterThrowing(Throwable throwable) {
        Long startedAt = startTime.get();
        startTime.remove();
        if (startedAt == null) {
            return;
        }
        HttpServletRequest request = currentHttpRequest();
        if (request != null) {
            log.warn("HTTP请求异常 method={} uri={} durationMs={} error={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    System.currentTimeMillis() - startedAt,
                    throwable.getClass().getSimpleName());
        }
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
            String lowerName = name.toLowerCase(Locale.ROOT);
            boolean sensitive = isSensitiveName(name)
                    || (retrievalEndpoint && (lowerName.contains("sql")
                    || lowerName.contains("value")
                    || lowerName.equals("text")));
            safe.put(name, sensitive ? "***" : values);
        });
        return safe;
    }

}
