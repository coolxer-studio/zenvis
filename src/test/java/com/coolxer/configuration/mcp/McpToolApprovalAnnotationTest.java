package com.coolxer.configuration.mcp;

import com.coolxer.controller.policy.ConfigMcpTool;
import com.coolxer.controller.policy.PolicyConfigValidationMcpTool;
import com.coolxer.controller.retrieval.RetrievalMcpTool;
import com.coolxer.controller.system.AnalysisTaskMcpTool;
import com.coolxer.controller.system.DashboardMcpTool;
import com.coolxer.controller.system.MenuMcpTool;
import com.coolxer.controller.system.PushTaskMcpTool;
import com.coolxer.service.dih.mcp.McpToolApproval;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolApprovalAnnotationTest {

    private static final List<Class<?>> LOCAL_TOOL_CLASSES = List.of(
            RetrievalMcpTool.class,
            AnalysisTaskMcpTool.class,
            PushTaskMcpTool.class,
            ConfigMcpTool.class,
            PolicyConfigValidationMcpTool.class,
            MenuMcpTool.class,
            DashboardMcpTool.class
    );

    @Test
    void everyLocalToolDeclaresItsDefaultApprovalPolicy() {
        List<Method> methods = LOCAL_TOOL_CLASSES.stream()
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .toList();

        assertThat(methods).hasSize(69);
        assertThat(methods).allSatisfy(method -> {
            McpToolApproval approval = method.getAnnotation(McpToolApproval.class);
            assertThat(approval)
                    .as("%s.%s must declare @McpToolApproval",
                            method.getDeclaringClass().getSimpleName(), method.getName())
                    .isNotNull();
            assertThat(approval.risk())
                    .as("%s.%s must declare a known risk",
                            method.getDeclaringClass().getSimpleName(), method.getName())
                    .isNotEqualTo(com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN);
        });
    }
}
