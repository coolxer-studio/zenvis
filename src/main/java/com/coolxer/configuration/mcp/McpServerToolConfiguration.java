package com.coolxer.configuration.mcp;

import com.coolxer.controller.policy.ConfigMcpTool;
import com.coolxer.controller.retrieval.RetrievalMcpTool;
import com.coolxer.controller.system.AnalysisTaskMcpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

/**
 * MCP服务器工具配置
 * 显式注册所有带有@Tool注解的方法
 */
@Slf4j
@Configuration
public class McpServerToolConfiguration {

    @Bean
    public MethodToolCallbackProvider retrievalToolCallbackProvider(RetrievalMcpTool retrievalMcpTool,
                                                                    AnalysisTaskMcpTool analysisTaskMcpTool,
                                                                    ConfigMcpTool configMcpTool) {
        log.info("=== Creating MethodToolCallbackProvider for MCP tools ===");

        logToolMethods(RetrievalMcpTool.class);
        logToolMethods(AnalysisTaskMcpTool.class);
        logToolMethods(ConfigMcpTool.class);

        // 创建 MethodToolCallbackProvider
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(retrievalMcpTool, analysisTaskMcpTool, configMcpTool)
                .build();

        log.info("=== MethodToolCallbackProvider created successfully ===");
        return provider;
    }

    private void logToolMethods(Class<?> toolClass) {
        Method[] methods = toolClass.getDeclaredMethods();
        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                log.info("Found @Tool method: class={}, name={}, description={}",
                    toolClass.getSimpleName(),
                    toolAnnotation.name(), toolAnnotation.description());
            }
        }
    }
}
