package com.coolxer.configuration.extend;

import com.coolxer.aop.AuthorityInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MappingHandlerConfigTest {

    @Test
    void dynamicPluginMappingUsesPlatformAuthorityInterceptor() {
        AuthorityInterceptor authorityInterceptor = mock(AuthorityInterceptor.class);
        RequestMappingHandlerMapping mapping = new MappingHandlerConfig()
                .extendJarHandlerMapping(authorityInterceptor);
        mapping.setApplicationContext(new StaticApplicationContext());
        mapping.afterPropertiesSet();

        assertThat(mapping.getAdaptedInterceptors()).containsExactly(authorityInterceptor);
    }
}
