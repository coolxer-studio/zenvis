package com.coolxer.configuration.extend;

import com.coolxer.aop.AuthorityInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 注入单独的mapping bean 防止和默认的冲突
 */
@Configuration
public class MappingHandlerConfig {

    @Bean
    public RequestMappingHandlerMapping extendJarHandlerMapping(AuthorityInterceptor authorityInterceptor) {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        // 与默认的区分优先级，数字越小优先级越高
        mapping.setOrder(1);
        // 动态插件使用独立 HandlerMapping，不会自动继承 WebMvcConfigurer 注册的全局拦截器。
        mapping.setInterceptors(authorityInterceptor);
        return mapping;
    }
}
