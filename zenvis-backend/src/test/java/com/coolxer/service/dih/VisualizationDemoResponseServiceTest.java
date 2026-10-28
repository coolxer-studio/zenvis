package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisualizationDemoResponseServiceTest {

    @Test
    void appConfigDemoWritesOnlyConfigAndNeverTouchesMenus() {
        List<String> calls = new ArrayList<>();
        VisualizationDemoSupport support = support(
                new VisualizationAppDemoResponseService(mock(ConfigService.class)));
        String pageConfig = (String) ReflectionTestUtils.getField(
                support, "USER_EVENT_PAGE_CONFIG");
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_ensure_root", calls, ignored -> "true"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "true"),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(pageConfig)),
                callback("menu_create", calls, ignored ->
                        fail("可视化应用配置写入不得调用 menu_create")),
                callback("dashboard_create", calls, ignored ->
                        fail("可视化应用配置写入不得调用 dashboard_create"))
        );

        String response = ReflectionTestUtils.invokeMethod(
                support, "applySinglePageLowCode", context);

        assertThat(response)
                .contains("zenvis:visualization-app-record")
                .contains("\"recordKind\": \"config\"")
                .doesNotContain("zenvis:visualization-app-record\n{\n  \"recordKind\": \"menu")
                .doesNotContain("zenvis:visualization-dashboard-record");
        assertThat(calls).containsExactly(
                "config_tree", "config_ensure_root", "config_add", "config_apply", "config_read");
    }

    @Test
    void dashboardConfigDemoCreatesDashboardButDoesNotPublishMenus() {
        List<String> calls = new ArrayList<>();
        VisualizationDemoSupport support = support(
                new VisualizationDashboardDemoResponseService(mock(ConfigService.class)));
        String dashboardConfig = (String) ReflectionTestUtils.getField(
                support, "USER_EVENT_DASHBOARD_CONFIG");
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_ensure_root", calls, ignored -> "true"),
                callback("config_add", calls, ignored -> "true"),
                callback("config_apply", calls, ignored -> "true"),
                callback("config_read", calls, ignored ->
                        JacksonUtil.toJson(dashboardConfig)),
                callback("dashboard_list", calls, ignored -> "{\"rows\":[]}"),
                callback("dashboard_create", calls, ignored ->
                        JacksonUtil.toJson(dashboardReadBack())),
                callback("dashboard_view", calls, ignored ->
                        JacksonUtil.toJson(dashboardReadBack())),
                callback("menu_create", calls, ignored ->
                        fail("数据大屏配置写入不得自动发布菜单"))
        );

        String response = ReflectionTestUtils.invokeMethod(
                support, "applyDashboardLowCode", context);

        assertThat(response)
                .contains("zenvis:visualization-dashboard-record")
                .contains("\"recordKind\": \"config\"")
                .contains("\"recordKind\": \"dashboard\"")
                .doesNotContain("\"recordKind\": \"menu\"")
                .doesNotContain("zenvis:visualization-app-record");
        assertThat(calls).containsExactly(
                "config_tree", "config_ensure_root", "config_add", "config_apply",
                "config_read", "dashboard_list", "dashboard_create", "dashboard_view");
    }

    @Test
    void menuDemoStillCreatesAndReadsBackTheMenu() throws Exception {
        List<String> calls = new ArrayList<>();
        VisualizationDemoSupport support = support(
                new VisualizationAppDemoResponseService(mock(ConfigService.class)));
        String pageConfig = (String) ReflectionTestUtils.getField(
                support, "USER_EVENT_PAGE_CONFIG");
        AtomicReference<Map<String, Object>> menuRequest = new AtomicReference<>();
        McpToolContext context = toolContext(
                callback("config_tree", calls, ignored ->
                        "[{\"fileName\":\"index.json\"}]"),
                callback("config_read", calls, ignored -> JacksonUtil.toJson(pageConfig)),
                callback("menu_list", calls, ignored -> "{\"rows\":[]}"),
                callback("menu_create", calls, input -> {
                    menuRequest.set(JacksonUtil.toMap(
                            JacksonUtil.toMap(input,
                                    new TypeReference<Map<String, Object>>() {
                                    }).get("request")));
                    return "{\"id\":34}";
                }),
                callback("menu_view", calls, ignored -> {
                    Map<String, Object> readBack = new LinkedHashMap<>(menuRequest.get());
                    readBack.put("id", 34);
                    return JacksonUtil.toJson(readBack);
                })
        );
        ChatSession session = new ChatSession();

        String response = ReflectionTestUtils.invokeMethod(
                support, "applyMenuDemo", session, context);

        assertThat(response)
                .contains("zenvis:visualization-app-record")
                .contains("\"recordKind\": \"menu\"")
                .contains("\"menuId\": \"34\"");
        assertThat(calls).containsExactly(
                "config_tree", "config_tree", "config_read",
                "menu_list", "menu_create", "menu_view");
    }

    @Test
    void eachDemoServiceRoutesOnlyItsOwnScenario() {
        assertThat(VisualizationChartDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationChartDemoResponseService.CHART_EXAMPLE_PROMPT)).isTrue();
        assertThat(VisualizationChartDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationAppDemoResponseService.PAGE_EXAMPLE_PROMPT)).isFalse();
        assertThat(VisualizationAppDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationDashboardDemoResponseService.DASHBOARD_EXAMPLE_PROMPT)).isFalse();
        assertThat(VisualizationAppDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationAppDemoResponseService.MENU_EXAMPLE_PROMPT)).isTrue();
        assertThat(VisualizationDashboardDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationDashboardDemoResponseService.DASHBOARD_EXAMPLE_PROMPT)).isTrue();
        assertThat(VisualizationDashboardDemoResponseService.isUserEventDemoRequirementPrompt(
                VisualizationAppDemoResponseService.SIDEBAR_APP_EXAMPLE_PROMPT)).isFalse();
    }

    @Test
    void missingMetaStopsBeforeAnyWriteTool() {
        List<String> calls = new ArrayList<>();
        VisualizationDemoSupport support = support(
                new VisualizationChartDemoResponseService(mock(ConfigService.class)));
        McpToolContext context = toolContext(
                callback("retrieval_list_display_entity", calls, ignored -> "{\"entityList\":[]}"),
                callback("config_apply", calls, ignored ->
                        fail("缺少 Meta 时不得调用 config_apply"))
        );

        String response = ReflectionTestUtils.invokeMethod(
                support, "buildValidatedArtifact", "临时图表生成", context,
                (java.util.function.Supplier<String>) () -> "must-not-render");

        assertThat(response)
                .contains("zenvis:visualization-chart-decision")
                .contains("\"kind\":\"missing_meta\"")
                .contains("agent_metadata_config")
                .contains("user_event")
                .contains("event_type")
                .contains("未调用任何写工具");
        assertThat(calls).containsExactly("retrieval_list_display_entity");
    }

    private VisualizationDemoSupport support(Object service) {
        return (VisualizationDemoSupport) ReflectionTestUtils.getField(service, "support");
    }

    private Map<String, Object> dashboardReadBack() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("id", 21);
        dashboard.put("name", "用户事件低代码看板");
        dashboard.put("code", "user-event-low-code-dashboard");
        dashboard.put("type", "LOW_CODE_PAGE");
        dashboard.put("configIndex", "user-event-dashboard");
        dashboard.put("source", "data-visualization-demo:user-event:dashboard-low-code");
        dashboard.put("isDefault", false);
        return dashboard;
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "visualization demo tools");
    }

    private ToolCallback callback(String name,
                                  List<String> calls,
                                  Function<String, String> result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                calls.add(name);
                return result.apply(toolInput);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }

    private String fail(String message) {
        throw new AssertionError(message);
    }
}
