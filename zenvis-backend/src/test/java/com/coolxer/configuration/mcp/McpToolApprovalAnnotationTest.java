package com.coolxer.configuration.mcp;

import com.coolxer.controller.config.ConfigMcpTool;
import com.coolxer.controller.config.ConfigValidationMcpTool;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;
import static org.assertj.core.api.Assertions.assertThat;

class McpToolApprovalAnnotationTest {

    private static final List<Class<?>> LOCAL_TOOL_CLASSES = List.of(
            RetrievalMcpTool.class,
            AnalysisTaskMcpTool.class,
            PushTaskMcpTool.class,
            ConfigMcpTool.class,
            ConfigValidationMcpTool.class,
            MenuMcpTool.class,
            DashboardMcpTool.class
    );

    @Test
    void everyLocalToolDeclaresItsDefaultApprovalPolicy() {
        List<Method> methods = LOCAL_TOOL_CLASSES.stream()
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .toList();

        assertThat(methods).hasSize(74);
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

    @Test
    void configurationToolsUseOnlyCanonicalNamesAndExpectedRiskLevels() {
        Map<String, McpToolApproval> tools = List.of(ConfigMcpTool.class, ConfigValidationMcpTool.class).stream()
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .collect(Collectors.toMap(
                        method -> method.getAnnotation(Tool.class).name(),
                        method -> method.getAnnotation(McpToolApproval.class)
                ));

        assertThat(tools.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                "config_tree",
                "config_schema",
                "config_read",
                "config_meta_draft",
                "config_validate",
                "config_ensure_root",
                "config_add",
                "config_apply",
                "config_rename",
                "config_delete"
        ));
        assertThat(tools).allSatisfy((name, approval) -> {
            if (Set.of("config_tree", "config_schema", "config_read",
                    "config_meta_draft", "config_validate").contains(name)) {
                assertThat(approval.value()).isEqualTo(ALLOW);
                assertThat(approval.risk()).isEqualTo(LOW);
            } else {
                assertThat(approval.value()).isEqualTo(ASK);
                assertThat(approval.risk()).isEqualTo(HIGH);
            }
        });
    }

    @Test
    void pushTaskDiagnosticAndRepairToolsUseExpectedApprovalPolicies() {
        Map<String, Method> tools = List.of(PushTaskMcpTool.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .collect(Collectors.toMap(
                        method -> method.getAnnotation(Tool.class).name(),
                        method -> method
                ));

        assertThat(tools.get("push_task_get_log").getAnnotation(McpToolApproval.class)).satisfies(approval -> {
            assertThat(approval.value()).isEqualTo(ALLOW);
            assertThat(approval.risk()).isEqualTo(LOW);
        });
        assertThat(tools).containsOnlyKeys(
                "push_task_list",
                "push_task_get",
                "push_task_create_and_start",
                "push_task_update_and_restart",
                "push_task_start",
                "push_task_stop",
                "push_task_restart",
                "push_task_delete",
                "push_task_get_log",
                "push_task_validate_config",
                "push_task_diagnose");
        for (String toolName : List.of(
                "push_task_list",
                "push_task_get",
                "push_task_get_log",
                "push_task_validate_config",
                "push_task_diagnose")) {
            assertThat(tools.get(toolName).getAnnotation(McpToolApproval.class))
                    .satisfies(approval -> {
                        assertThat(approval.value()).isEqualTo(ALLOW);
                        assertThat(approval.risk()).isEqualTo(LOW);
                    });
        }
        for (String toolName : List.of(
                "push_task_create_and_start",
                "push_task_update_and_restart",
                "push_task_start",
                "push_task_stop",
                "push_task_restart",
                "push_task_delete")) {
            assertThat(tools.get(toolName).getAnnotation(McpToolApproval.class))
                    .satisfies(approval -> {
                        assertThat(approval.value()).isEqualTo(ASK);
                        assertThat(approval.risk()).isEqualTo(HIGH);
                    });
        }
        Method repairTool = tools.get("push_task_update_and_restart");
        assertThat(repairTool.getAnnotation(Tool.class).description())
                .contains("完整配置", "格式", "source", "mark", "保持不变");
    }
}
