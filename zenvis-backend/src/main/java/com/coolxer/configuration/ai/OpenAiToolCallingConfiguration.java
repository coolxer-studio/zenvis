package com.coolxer.configuration.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 兼容接口的工具调用配置。
 */
@Configuration(proxyBeanMethods = false)
public class OpenAiToolCallingConfiguration {

    @Bean
    @ConditionalOnMissingBean(ToolCallingManager.class)
    ToolCallingManager openAiCompatibleToolCallingManager(
            ToolCallbackResolver toolCallbackResolver,
            ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ToolCallingObservationConvention> observationConvention
    ) {
        DefaultToolCallingManager delegate = ToolCallingManager.builder()
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
                .build();
        observationConvention.ifAvailable(delegate::setObservationConvention);
        return new OpenAiCompatibleToolCallingManager(delegate);
    }
}
