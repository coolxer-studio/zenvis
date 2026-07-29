package com.coolxer.aop;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.system.dto.LoginDto;
import com.coolxer.utils.JacksonUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogAopAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldSkipRequestLoggingOutsideWebRequest() {
        RequestContextHolder.resetRequestAttributes();
        LogAopAspect aspect = new LogAopAspect();
        JoinPoint joinPoint = new TestJoinPoint();

        assertThatCode(() -> {
            aspect.doBefore(joinPoint);
            aspect.doAfterReturning(null);
        }).doesNotThrowAnyException();
    }

    @Test
    void ruleMutationLogSummaryDoesNotContainSqlOrConditionValues() {
        RetrievalRuleCreateDto request = new RetrievalRuleCreateDto();
        request.setType("advanced");
        request.setEntity("asset");
        request.setSql("secret SQL value");
        RequestCriteriaDto criteria = new RequestCriteriaDto();
        criteria.setAttribute("ip");
        criteria.setOperator("equal");
        criteria.setValueList(List.of("secret condition value"));
        request.setCriteriaList(List.of(criteria));

        Map<String, Object> safe = ReflectionTestUtils.invokeMethod(
                new LogAopAspect(), "sanitizeRuleConfig", request);

        assertThat(safe).containsEntry("sql", "***").containsEntry("criteria_count", 1);
        assertThat(safe.toString()).doesNotContain("secret SQL value", "secret condition value");
    }

    @Test
    void genericArgumentSanitizerRedactsLoginCredentials() {
        LoginDto request = new LoginDto();
        request.setUserName("super@admin.com");
        request.setPassword("encrypted-password-payload");
        request.setAuthCode("b882");

        Object safe = ReflectionTestUtils.invokeMethod(
                new LogAopAspect(), "sanitizeArgument", request);
        String json = JacksonUtil.toJson(safe);

        assertThat(json)
                .contains("super@admin.com", "\"password\":\"***\"", "\"auth_code\":\"***\"")
                .doesNotContain("encrypted-password-payload", "b882");
    }

    private static class TestJoinPoint implements JoinPoint {

        private final Signature signature = new TestSignature();

        @Override
        public String toShortString() {
            return signature.toShortString();
        }

        @Override
        public String toLongString() {
            return signature.toLongString();
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object[] getArgs() {
            return new Object[0];
        }

        @Override
        public Signature getSignature() {
            return signature;
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String getKind() {
            return JoinPoint.METHOD_EXECUTION;
        }

        @Override
        public StaticPart getStaticPart() {
            return null;
        }
    }

    private static class TestSignature implements Signature {

        @Override
        public String toShortString() {
            return "RetrievalMcpTool.listDisplayEntity(..)";
        }

        @Override
        public String toLongString() {
            return "com.coolxer.controller.retrieval.RetrievalMcpTool.listDisplayEntity(..)";
        }

        @Override
        public String getName() {
            return "listDisplayEntity";
        }

        @Override
        public int getModifiers() {
            return 0;
        }

        @Override
        public Class<?> getDeclaringType() {
            return Object.class;
        }

        @Override
        public String getDeclaringTypeName() {
            return "com.coolxer.controller.retrieval.RetrievalMcpTool";
        }
    }
}
