package com.coolxer.configuration.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.controller.config.ConfigMcpTool;
import com.coolxer.controller.config.ConfigValidationMcpTool;
import com.coolxer.controller.retrieval.RetrievalMcpTool;
import com.coolxer.controller.system.AnalysisTaskMcpTool;
import com.coolxer.controller.system.AnalysisTaskScheduleMcpTool;
import com.coolxer.controller.system.DashboardMcpTool;
import com.coolxer.controller.system.MenuMcpTool;
import com.coolxer.controller.system.PushTaskMcpTool;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpApprovalToolCallbackProvider;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.dih.mcp.McpToolDescriptor;
import com.coolxer.service.dih.mcp.McpToolPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP服务器工具配置
 * 显式注册所有带有@Tool注解的方法
 */
@Slf4j
@Configuration
public class McpServerToolConfiguration {

    @Bean
    public ToolCallbackProvider retrievalToolCallbackProvider(RetrievalMcpTool retrievalMcpTool,
                                                                    AnalysisTaskMcpTool analysisTaskMcpTool,
                                                                    AnalysisTaskScheduleMcpTool analysisTaskScheduleMcpTool,
                                                                    PushTaskMcpTool pushTaskMcpTool,
                                                                    ConfigMcpTool configMcpTool,
                                                                    ConfigValidationMcpTool configValidationMcpTool,
                                                                    MenuMcpTool menuMcpTool,
                                                                    DashboardMcpTool dashboardMcpTool,
                                                                    McpApprovalService approvalService,
                                                                    McpToolPolicyService policyService) {
        log.info("=== Creating MethodToolCallbackProvider for MCP tools ===");

        logToolMethods(RetrievalMcpTool.class);
        logToolMethods(AnalysisTaskMcpTool.class);
        logToolMethods(AnalysisTaskScheduleMcpTool.class);
        logToolMethods(PushTaskMcpTool.class);
        logToolMethods(ConfigMcpTool.class);
        logToolMethods(ConfigValidationMcpTool.class);
        logToolMethods(MenuMcpTool.class);
        logToolMethods(DashboardMcpTool.class);

        // 创建 MethodToolCallbackProvider
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(retrievalMcpTool, analysisTaskMcpTool, analysisTaskScheduleMcpTool,
                        pushTaskMcpTool, configMcpTool,
                        configValidationMcpTool, menuMcpTool, dashboardMcpTool)
                .build();

        Map<String, McpToolDescriptor> descriptors = new LinkedHashMap<>();
        addToolDescriptors(descriptors, RetrievalMcpTool.class);
        addToolDescriptors(descriptors, AnalysisTaskMcpTool.class);
        addToolDescriptors(descriptors, AnalysisTaskScheduleMcpTool.class);
        addToolDescriptors(descriptors, PushTaskMcpTool.class);
        addToolDescriptors(descriptors, ConfigMcpTool.class);
        addToolDescriptors(descriptors, ConfigValidationMcpTool.class);
        addToolDescriptors(descriptors, MenuMcpTool.class);
        addToolDescriptors(descriptors, DashboardMcpTool.class);

        log.info("=== MethodToolCallbackProvider created successfully ===");
        return new McpApprovalToolCallbackProvider(provider, descriptors, approvalService, policyService);
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

    private void addToolDescriptors(Map<String, McpToolDescriptor> descriptors, Class<?> toolClass) {
        for (Method method : toolClass.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            McpToolApproval approval = method.getAnnotation(McpToolApproval.class);
            McpApprovalPolicy defaultPolicy = approval == null ? McpApprovalPolicy.ASK : approval.value();
            String name = tool.name();
            descriptors.put(name, new McpToolDescriptor(
                    McpToolDescriptor.localKey(name),
                    McpToolSourceType.LOCAL,
                    null,
                    "local",
                    "ZenVis 内置工具",
                    name,
                    name,
                    null,
                    tool.description(),
                    defaultPolicy == McpApprovalPolicy.ALLOW,
                    name.contains("delete") || name.contains("apply") || name.contains("run"),
                    approval == null ? com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN : approval.risk(),
                    defaultPolicy
            ));
        }
    }
}
