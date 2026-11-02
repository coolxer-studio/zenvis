package com.coolxer.configuration.mcp;

import com.coolxer.controller.config.ConfigMcpTool;
import com.coolxer.controller.config.ConfigValidationMcpTool;
import com.coolxer.controller.dih.DataAppMcpTool;
import com.coolxer.controller.retrieval.RetrievalMcpTool;
import com.coolxer.controller.system.AnalysisTaskMcpTool;
import com.coolxer.controller.system.AnalysisTaskScheduleMcpTool;
import com.coolxer.controller.system.MenuMcpTool;
import com.coolxer.controller.system.PushTaskMcpTool;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpApprovalToolCallbackProvider;
import com.coolxer.service.dih.mcp.McpToolPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpServerToolConfigurationTest {

    @Test
    void registersDashboardDescriptorsWhenToolBeanIsAopProxied() {
        com.coolxer.controller.dih.DashboardMcpTool target = new com.coolxer.controller.dih.DashboardMcpTool(
                null, null, null, null, null, null);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodBeforeAdvice) (method, args, proxyTarget) -> {
        });
        com.coolxer.controller.dih.DashboardMcpTool dashboardProxy =
                (com.coolxer.controller.dih.DashboardMcpTool) proxyFactory.getProxy();
        assertThat(AopUtils.isAopProxy(dashboardProxy)).isTrue();

        McpApprovalToolCallbackProvider provider = new McpServerToolConfiguration()
                .retrievalToolCallbackProvider(
                        new RetrievalMcpTool(),
                        new AnalysisTaskMcpTool(mock(ObjectProvider.class)),
                        new AnalysisTaskScheduleMcpTool(mock(ObjectProvider.class)),
                        new PushTaskMcpTool(null, null),
                        new ConfigMcpTool(null),
                        new ConfigValidationMcpTool(null),
                        new MenuMcpTool(null),
                        new com.coolxer.controller.system.DashboardMcpTool(null),
                        new DataAppMcpTool(null, null, null, null, null, null),
                        dashboardProxy,
                        mock(McpApprovalService.class),
                        mock(McpToolPolicyService.class));

        assertThat(provider.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .contains(
                        "visualization_dashboard_list",
                        "visualization_dashboard_get",
                        "visualization_dashboard_validate",
                        "visualization_dashboard_apply");
        assertThat(provider.descriptor("visualization_dashboard_validate")).isNotNull();
        assertThat(provider.descriptor("visualization_dashboard_apply").defaultPolicy())
                .isEqualTo(McpApprovalPolicy.ASK);
    }
}
