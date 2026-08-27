package com.coolxer.configuration.extend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

@Component
public class Cleaner {

    @Autowired
    private ConfigurableApplicationContext ctx;

    @Autowired
    @Qualifier("extendJarHandlerMapping")
    private RequestMappingHandlerMapping extendJarHandlerMapping;

    public void cleanup(ExtendJar extendJar) {
        ConfigurableListableBeanFactory bf = ctx.getBeanFactory();
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) bf;
        // 1) 移除 Singleton Bean
        for (String name : bf.getBeanDefinitionNames()) {
            if (name.startsWith(extendJar.getBeanNamePrefix())) {
                // 1) 先注销 BeanDefinition
                if (registry.containsBeanDefinition(name)) {
                    registry.removeBeanDefinition(name);
                }
                // 2) 再销毁单例实例（强制转型）
                if (bf instanceof DefaultSingletonBeanRegistry singletonRegistry) {
                    singletonRegistry.destroySingleton(name);
                }
            }
        }
        // 2) 移除 RequestMapping
        // 拿到所有映射
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = extendJarHandlerMapping.getHandlerMethods();
        // 收集要删除的 key
        List<RequestMappingInfo> toRemove = handlerMethods.keySet().stream()
                .filter(info -> {
                    PathPatternsRequestCondition pc = info.getPathPatternsCondition();
                    return pc != null &&
                            pc.getPatterns().stream().anyMatch(p -> p.getPatternString().startsWith(extendJar.getUrlPrefix()));
                })
                .toList();
        // 逐个移除
        toRemove.forEach(extendJarHandlerMapping::unregisterMapping);
    }
}
