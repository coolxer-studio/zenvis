package com.coolxer.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

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
