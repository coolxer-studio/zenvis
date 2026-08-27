package com.coolxer.service.system.impl;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 插件安装/卸载后台执行器。
 */
@Service
public class PluginOperationExecutor {

    private final ThreadPoolTaskExecutor executor;

    public PluginOperationExecutor() {
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setThreadNamePrefix("plugin-operation-");
        this.executor.setCorePoolSize(2);
        this.executor.setMaxPoolSize(4);
        this.executor.setQueueCapacity(64);
        this.executor.initialize();
    }

    public void submit(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
