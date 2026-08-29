package com.coolxer.config;

import com.coolxer.service.McpTaskTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP配置类
 * <p>
 * 配置MCP服务器工具注册
 * </p>
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider taskToolCallbackProvider(McpTaskTools mcpTaskTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpTaskTools)
                .build();
    }
}