package com.coolxer.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置类
 * <p>
 * 配置异步任务执行的线程池
 * </p>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 获取异步任务执行器
     * @return 线程池执行器
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }

    /**
     * 获取日志监听任务执行器
     * @return 线程池执行器
     */
    @Bean("logMonitorExecutor")
    public Executor logMonitorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("log-monitor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
