package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ThreadPoolExecutor;

@AutoConfiguration
@EnableConfigurationProperties(ZenvisBusinessServiceProperties.class)
public class ZenvisBusinessServiceAutoConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "zenvis.business-service",
            name = "enabled",
            havingValue = "false")
    @ConditionalOnMissingBean(BusinessServiceReporter.class)
    BusinessServiceReporter disabledBusinessServiceReporter() {
        return (eventType, severity, title, message, traceId, data) -> {
            // Explicit no-op: disabled reporting must remain safe to inject.
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnProperty(
            prefix = "zenvis.business-service",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class EnabledReportingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ZenvisJsonSupport zenvisJsonSupport(ObjectProvider<ObjectMapper> objectMappers) {
            ObjectMapper objectMapper = objectMappers.orderedStream()
                    .findFirst()
                    .orElseGet(ObjectMapper::new);
            return new ZenvisJsonSupport(objectMapper);
        }

        @Bean(name = "zenvisBusinessServiceRestTemplate")
        @ConditionalOnMissingBean(name = "zenvisBusinessServiceRestTemplate")
        RestTemplate zenvisBusinessServiceRestTemplate(
                ZenvisBusinessServiceProperties properties,
                ZenvisJsonSupport jsonSupport) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(toTimeout(properties.getConnectTimeoutMillis()));
            requestFactory.setReadTimeout(toTimeout(properties.getReadTimeoutMillis()));
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            restTemplate.getMessageConverters().stream()
                    .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                    .map(MappingJackson2HttpMessageConverter.class::cast)
                    .forEach(converter -> converter.setObjectMapper(jsonSupport.objectMapper()));
            return restTemplate;
        }

        @Bean(name = "zenvisBusinessServiceEventExecutor")
        @ConditionalOnMissingBean(name = "zenvisBusinessServiceEventExecutor")
        ThreadPoolTaskExecutor zenvisBusinessServiceEventExecutor(
                ZenvisBusinessServiceProperties properties) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(Math.max(1, properties.getEventQueueCapacity()));
            executor.setThreadNamePrefix("zenvis-event-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(3);
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
            return executor;
        }

        @Bean(name = "zenvisBusinessServiceHeartbeatScheduler")
        @ConditionalOnMissingBean(name = "zenvisBusinessServiceHeartbeatScheduler")
        ThreadPoolTaskScheduler zenvisBusinessServiceHeartbeatScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("zenvis-heartbeat-");
            scheduler.setWaitForTasksToCompleteOnShutdown(false);
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }

        @Bean
        @ConditionalOnMissingBean
        ZenvisBusinessServiceIdentity zenvisBusinessServiceIdentity(
                ZenvisBusinessServiceProperties properties,
                Environment environment,
                ObjectProvider<BuildProperties> buildProperties) {
            return new ZenvisBusinessServiceIdentity(
                    properties,
                    environment,
                    buildProperties.getIfAvailable());
        }

        @Bean
        @ConditionalOnMissingBean(ZenvisBusinessServiceTransport.class)
        ZenvisBusinessServiceTransport zenvisBusinessServiceTransport(
                @Qualifier("zenvisBusinessServiceRestTemplate") RestTemplate restTemplate,
                ZenvisBusinessServiceProperties properties) {
            return new ZenvisBusinessServiceClient(restTemplate, properties);
        }

        @Bean
        @ConditionalOnMissingBean(BusinessServiceReporter.class)
        ZenvisBusinessServiceManager zenvisBusinessServiceReporter(
                ZenvisBusinessServiceProperties properties,
                ZenvisBusinessServiceIdentity identity,
                ZenvisBusinessServiceTransport transport,
                @Qualifier("zenvisBusinessServiceEventExecutor")
                TaskExecutor eventExecutor,
                @Qualifier("zenvisBusinessServiceHeartbeatScheduler")
                TaskScheduler heartbeatScheduler,
                ZenvisJsonSupport jsonSupport) {
            return new ZenvisBusinessServiceManager(
                    properties,
                    identity,
                    transport,
                    eventExecutor,
                    heartbeatScheduler,
                    jsonSupport.objectMapper());
        }

        private static int toTimeout(long timeoutMillis) {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutMillis));
        }
    }
}
