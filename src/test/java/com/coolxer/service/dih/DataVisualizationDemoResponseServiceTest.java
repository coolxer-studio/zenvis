package com.coolxer.service.dih;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.MenuOrderRowDto;
import com.coolxer.model.system.dto.MenuSearchDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DataVisualizationDemoResponseServiceTest {

    private final FakeConfigService configService = new FakeConfigService();
    private final FakeMenuService menuService = new FakeMenuService();
    private final FakeDashboardService dashboardService = new FakeDashboardService();
    private final DataVisualizationDemoResponseService service = new DataVisualizationDemoResponseService(
            configService,
            menuService,
            dashboardService
    );

    @Test
    void chartRequirementReturnsInfoSteps() {
        configService.metaExists = true;

        String response = responseOf(service.findResponse(null, "chat-1", """
                # 用户事件数据可视化：临时图表
                请查看用户事件数据的上报情况。
                """, null));

        assertThat(response)
                .contains("zenvis:info-steps")
                .contains("用户事件临时图表信息确认")
                .contains("时间范围")
                .contains("图表类型");
    }

    @Test
    void plainDemoPromptRoutesToSelectedScenario() {
        configService.metaExists = true;

        assertThat(responseOf(service.findResponse(null, "chat-1", "请查看用户事件数据的上报情况，并生成一个临时性的可视化图表。", null)))
                .contains("用户事件临时图表信息确认")
                .doesNotContain("用户事件单页面应用实现方式确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请根据用户事件数据生成一个单页面应用。", null)))
                .contains("用户事件单页面应用实现方式确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请生成一个带侧边栏的用户事件数据应用。", null)))
                .contains("用户事件侧边栏应用信息确认");
        assertThat(responseOf(service.findResponse(null, "chat-1", "请生成一个用户事件数据看板。", null)))
                .contains("用户事件数据看板信息确认");
    }

    @Test
    void chartInfoSubmittedUsesRealAggregateOptionAndFullProtocol() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = realDataContext(calls);
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件临时图表信息确认","answers":[{"value":"查看近 24 小时用户事件上报情况"},{"value":"使用曲线图并按 event_type 分组展示"}]}
                """, null, context));

        assertThat(response)
                .contains("zenvis:visualization-chart-preview")
                .contains("\"planId\"")
                .contains("\"entities\":[\"user_event\"]")
                .contains("\"tool\":\"entity_aggregate\"")
                .contains("\"preset\":\"LAST_24_HOURS\"")
                .contains("\"event_type\"")
                .contains("\"queryMeta\"")
                .contains("\"echartsOption\"")
                .contains("\"sentinel\":987654321")
                .contains("\"validationStatus\":\"success\"")
                .contains("\"action\":\"data_visualization.add_chart_library\"")
                .doesNotContain("12,18,46")
                .doesNotContain("示例数据");
        assertThat(calls).containsExactly(
                "retrieval_list_display_entity",
                "retrieval_list_display_attribute",
                "entity_aggregate");
    }

    @Test
    void addChartLibraryCopiesLatestRealPreviewWithoutRequery() {
        String preview = responseOf(service.findResponse(
                null,
                "chat-1",
                """
                        {"title":"用户事件临时图表信息确认","answers":[{"value":"查看近 24 小时用户事件上报情况"}]}
                        """,
                null,
                realDataContext(new ArrayList<>())));
        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(
                new Message("ai", preview)
        )));

        String response = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认把上一轮临时图表加入图表库。",
                null));

        assertThat(response)
                .contains("未重新查询或替换数据")
                .contains("zenvis:visualization-chart-record")
                .contains("\"sentinel\":987654321")
                .contains("\"validationStatus\":\"success\"")
                .contains("\"status\":\"temporary\"")
                .doesNotContain("12,18,46");
        List<ChatMessagePart> parts =
                new ChatMessagePartParser().parse(response, MessageType.TEXT);
        assertThat(parts)
                .extracting("type")
                .contains("visualization-chart-record");
        ChatMessagePart chartRecord = parts.stream()
                .filter(part -> "visualization-chart-record".equals(
                        part.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(chartRecord.getMetadata().get("echartsOption"))
                .isInstanceOf(Map.class);
    }

    @Test
    void chartGenerationBlocksWhenMetaIsMissing() {
        McpToolContext context = toolContext(
                callback("retrieval_list_display_entity",
                        new ArrayList<>(), ignored -> """
                                {"entityList":[]}
                                """)
        );

        String response = responseOf(service.findResponse(
                null,
                "chat-1",
                "{\"title\":\"用户事件临时图表信息确认\"}",
                null,
                context));

        assertThat(response)
                .contains("用户事件真实数据演示已阻止")
                .contains("不存在 user_event 实体")
                .doesNotContain("visualization-chart-preview");
    }

    @Test
    void menuExampleQueriesMcpCapabilitiesBeforeShowingConfirmation() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = toolContext(
                metaEntityCallback(calls),
                metaAttributeCallback(calls),
                callback("config_tree", calls, arguments ->
                        arguments.contains("user-event-page")
                                ? "[{\"fileName\":\"index.json\"}]"
                                : "[]"),
                callback("config_read", calls, ignored ->
                        JacksonUtil.toJson(
                                "{\"api\":\"/zenvis/api/v1/entity/user_event/list\"}")),
                callback("menu_type_options", calls, ignored -> """
                        {"options":[
                          {"label":"低代码页面","value":"LOW_CODE_PAGE"}]}
                        """),
                callback("menu_parent_options", calls, ignored -> """
                        {"options":[{"label":"数据应用","value":"8"}]}
                        """),
                callback("menu_list", calls, ignored -> """
                        {"rows":[],"total":0}
                        """)
        );

        String response = ReflectionTestUtils.invokeMethod(
                service, "buildMenuConfirmationResponse", null, context);

        assertThat(response)
                .contains("menu_type_options")
                .contains("menu_parent_options")
                .contains("menu_list")
                .contains("\"demoScenario\":\"menu\"")
                .contains("\"action\":\"data_visualization.apply_config\"")
                .contains("\"type\":\"LOW_CODE_PAGE\"")
                .contains("\"params\":\"user-event-page\"")
                .doesNotContain("example.com")
                .doesNotContain("EXTERNAL_APP")
                .contains("menu_create")
                .contains("MCP 审批")
                .contains("menu_view");
        assertThat(new ChatMessagePartParser().parse(response, MessageType.TEXT))
                .extracting("type")
                .contains("confirm");
        assertThat(calls).containsExactly(
                "retrieval_list_display_entity",
                "retrieval_list_display_attribute",
                "config_tree",
                "config_tree",
                "config_read",
                "menu_type_options",
                "menu_parent_options",
                "menu_list");
    }

    @Test
    void menuCreationUsesApprovalToolThenReadBackAndReturnsRecord() {
        List<String> calls = new ArrayList<>();
        String menu = """
                {"id":21,"name":"用户事件数据入口","type":"LOW_CODE_PAGE",
                 "route":"low-code-page",
                 "params":"user-event-page",
                 "parentId":0,"level":"LEVEL_1","superscript":"演示",
                 "source":"data-visualization-demo:user-event:menu-single-page-entry"}
                """;
        McpToolContext context = toolContext(
                callback("config_tree", calls, arguments ->
                        arguments.contains("user-event-page")
                                ? "[{\"fileName\":\"index.json\"}]"
                                : "[]"),
                callback("config_read", calls, ignored ->
                        JacksonUtil.toJson(
                                "{\"api\":\"/zenvis/api/v1/entity/user_event/list\"}")),
                callback("menu_list", calls, ignored -> """
                        {"rows":[],"total":0}
                        """),
                callback("menu_create", calls, arguments -> {
                    assertThat(arguments)
                            .contains("\"request\"")
                            .contains("\"name\":\"用户事件数据入口\"")
                            .contains("\"type\":\"LOW_CODE_PAGE\"");
                    return menu;
                }),
                callback("menu_view", calls, arguments -> {
                    assertThat(arguments).contains("\"id\":21");
                    return menu;
                })
        );

        String response = ReflectionTestUtils.invokeMethod(
                service, "applyMenuDemo", null, context);

        assertThat(response)
                .contains("菜单已通过 MCP 审批创建")
                .contains("menu_list → menu_create（已审批） → menu_view")
                .contains("zenvis:menu-config-record")
                .contains("\"menuId\": \"21\"")
                .contains("\"source\": \"data-visualization-demo:user-event:menu-single-page-entry\"");
        assertThat(new ChatMessagePartParser().parse(response, MessageType.TEXT))
                .extracting("type")
                .contains("menu-config-record");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_tree",
                "config_read",
                "menu_list",
                "menu_create",
                "menu_view");
    }

    @Test
    void rejectedMenuApprovalStopsBeforeReadBackAndDoesNotCreateRecord() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = toolContext(
                callback("config_tree", calls, arguments ->
                        arguments.contains("user-event-page")
                                ? "[{\"fileName\":\"index.json\"}]"
                                : "[]"),
                callback("config_read", calls, ignored ->
                        JacksonUtil.toJson(
                                "{\"api\":\"/zenvis/api/v1/entity/user_event/list\"}")),
                callback("menu_list", calls, ignored -> """
                        {"rows":[],"total":0}
                        """),
                callback("menu_create", calls, ignored -> """
                        {"status":"rejected","message":"用户拒绝了 MCP 工具调用"}
                        """),
                callback("menu_view", calls, ignored -> "{}")
        );

        String response = ReflectionTestUtils.invokeMethod(
                service, "applyMenuDemo", null, context);

        assertThat(response)
                .contains("添加菜单演示失败")
                .contains("status=rejected")
                .contains("未生成菜单成功记录")
                .doesNotContain("zenvis:menu-config-record");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_tree",
                "config_read",
                "menu_list",
                "menu_create");
    }

    @Test
    void menuExampleBlocksWhenSinglePageWasNotApplied() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = toolContext(
                metaEntityCallback(calls),
                metaAttributeCallback(calls),
                callback("config_tree", calls, ignored -> "[]")
        );

        String response = ReflectionTestUtils.invokeMethod(
                service, "buildMenuConfirmationResponse", null, context);

        assertThat(response)
                .contains("添加菜单演示失败")
                .contains("先运行并应用“单页面应用”演示")
                .doesNotContain("zenvis:confirm");
        assertThat(calls).containsExactly(
                "retrieval_list_display_entity",
                "retrieval_list_display_attribute",
                "config_tree",
                "config_tree");
    }

    @Test
    void singlePageLowCodeSelectionReturnsLowCodeConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件单页面应用实现方式确认","content":"请选择用低代码 amis 还是静态 HTML 实现用户事件增删改查单页面。","answers":[{"id":"implementation","title":"实现方式","value":"使用低代码 amis 方式实现单页面 CRUD 应用"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("zenvis:low-code-page-config")
                .contains("\"type\": \"crud\"")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"low_code\"")
                .contains("config_tree")
                .contains("config_apply（高风险 MCP 审批）")
                .contains("menu_create（高风险 MCP 审批）")
                .contains("menu_view")
                .doesNotContain("zenvis:html-page-config")
                .doesNotContain("\"implementation\":\"html\"");
    }

    @Test
    void singlePageHtmlSelectionReturnsHtmlConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件单页面应用实现方式确认","content":"请选择用低代码 amis 还是静态 HTML 实现用户事件增删改查单页面。","answers":[{"id":"implementation","title":"实现方式","value":"使用静态 HTML 单页面直接调用实体 REST API"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("zenvis:html-page-config")
                .contains("<!doctype html>")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"html\"")
                .contains("config_read")
                .contains("menu_create（高风险 MCP 审批）")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("\"implementation\":\"low_code\"");
    }

    @Test
    void sidebarSelectionExplainsConfigAndMenuMcpApprovals() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件侧边栏应用信息确认","answers":[{"id":"scope","title":"应用范围","value":"生成用户事件侧边栏应用"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("zenvis:low-code-app-config")
                .contains("site.json")
                .contains("index.json")
                .contains("manage.json")
                .contains("trend.json")
                .contains("config_apply（高风险 MCP 审批）")
                .contains("menu_create（高风险 MCP 审批）")
                .contains("menu_view");
    }

    @Test
    void dashboardLowCodeSelectionReturnsLowCodeDashboardConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","content":"请选择低代码、静态 HTML 或外链接方式。","answers":[{"id":"implementation","title":"实现方式","value":"使用低代码 amis 页面实现数据看板"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("zenvis:low-code-page-config")
                .contains("用户事件数据看板")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"dashboardType\":\"low_code\"")
                .contains("config_apply（高风险 MCP 审批）")
                .contains("dashboard_create（高风险 MCP 审批）")
                .contains("dashboard_view")
                .doesNotContain("zenvis:html-page-config")
                .doesNotContain("\"dashboardType\":\"html\"")
                .doesNotContain("\"dashboardType\":\"link\"");
    }

    @Test
    void dashboardHtmlSelectionReturnsHtmlDashboardConfig() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","content":"请选择低代码、静态 HTML 或外链接方式。","answers":[{"id":"implementation","title":"实现方式","value":"使用静态 HTML 页面实现数据看板"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("zenvis:html-page-config")
                .contains("<title>用户事件数据态势看板</title>")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"dashboardType\":\"html\"")
                .contains("config_read")
                .contains("dashboard_create（高风险 MCP 审批）")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("\"dashboardType\":\"low_code\"")
                .doesNotContain("\"dashboardType\":\"link\"");
    }

    @Test
    void dashboardInfoDoesNotOfferExternalLink() {
        String response = responseOf(service.findResponse(
                null,
                "chat-1",
                DataVisualizationDemoResponseService.DASHBOARD_EXAMPLE_PROMPT,
                null));

        assertThat(response)
                .contains("低代码看板")
                .contains("静态 HTML 看板")
                .doesNotContain("外链接看板")
                .doesNotContain("example.com");
    }

    @Test
    void dashboardConfigContainsOnlyRealUserEventApis() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我已根据上一条补充信息卡片提交以下结构化补充内容，请基于这些信息继续处理，不要重复询问已补充项。
                {"title":"用户事件数据看板信息确认","answers":[{"value":"使用低代码 amis 页面实现数据看板"}]}
                """, null, realDataContext(new ArrayList<>())));

        assertThat(response)
                .contains("/zenvis/api/v1/entity/overview/query")
                .contains("/zenvis/api/v1/entity/summary/query")
                .contains("/zenvis/api/v1/entity/aggregate/query")
                .contains("/zenvis/api/v1/entity/histogram/query")
                .contains("/zenvis/api/v1/entity/user_event/list")
                .contains("LAST_24_HOURS")
                .doesNotContain("/entity/user-event/")
                .doesNotContain("example.com");
    }

    @Test
    void reviseVisualizationConfigReturnsUpdatedDemoConfigWithoutModelFallback() {
        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(
                new Message("ai", "{\"demoScenario\":\"single_page\",\"implementation\":\"low_code\"}")
        )));

        String response = responseOf(service.findResponse(session, "chat-1", """
                我需要补充信息继续更新数据可视化配置。调整要求如下：
                增加用户事件趋势图。
                """, null));

        assertThat(response)
                .contains("已根据补充信息更新用户事件单页面应用配置")
                .contains("zenvis:low-code-page-config")
                .contains("\"actions\":[\"apply_config\",\"abandon\",\"revise\"]")
                .contains("\"implementation\":\"low_code\"");
    }

    @Test
    void abandonVisualizationConfigReturnsNotice() {
        String response = responseOf(service.findResponse(null, "chat-1", """
                我选择放弃本次数据可视化配置。请不要写入 open_config。
                """, null));

        assertThat(response)
                .contains("已放弃本次数据可视化配置")
                .contains("zenvis:notice")
                .doesNotContain("zenvis:low-code-page-config")
                .doesNotContain("zenvis:html-page-config");
    }

    @Test
    void applySinglePageLowCodeUsesMcpApprovalAndReadBack() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, null, null);
        ChatSession session = new ChatSession();
        session.setMessages(JacksonUtil.toJson(List.of(
                new Message("ai", "{\"demoScenario\": \"single_page\",\"implementation\":\"low_code\"}")
        )));

        String response = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认并授权应用上一轮数据可视化配置。",
                null,
                context));

        assertThat(response)
                .contains("通过 MCP 审批写入系统并完成读回")
                .contains("zenvis:visualization-config-record")
                .contains("zenvis:menu-config-record")
                .contains("user-event-page")
                .contains("用户事件单页面应用");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_ensure_root",
                "config_add",
                "config_apply",
                "config_read",
                "menu_parent_options",
                "menu_list",
                "menu_create",
                "menu_view",
                "menu_list",
                "menu_create",
                "menu_view");
    }

    @Test
    void applySidebarAppUsesMcpForAllConfigsAndMenus() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, null, null);

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applySidebarApp",
                context);

        assertThat(response)
                .contains("侧边栏低代码应用已通过 MCP 审批")
                .contains("zenvis:visualization-config-record")
                .contains("用户事件应用配置")
                .contains("用户事件侧边栏应用");
        assertThat(calls.stream()
                .filter("config_apply"::equals)
                .count()).isEqualTo(4);
        assertThat(calls.stream()
                .filter("config_read"::equals)
                .count()).isEqualTo(4);
        assertThat(calls.stream()
                .filter("menu_create"::equals)
                .count()).isEqualTo(2);
        assertThat(calls.stream()
                .filter("menu_view"::equals)
                .count()).isEqualTo(2);
    }

    @Test
    void applyDashboardUsesConfigMenuAndDashboardMcpWithReadBack() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, null, null);

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applyDashboardLowCode",
                context);

        assertThat(response)
                .contains("低代码看板已通过 MCP 审批")
                .contains("zenvis:visualization-config-record")
                .contains("zenvis:menu-config-record")
                .contains("zenvis:dashboard-config-record");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_ensure_root",
                "config_add",
                "config_apply",
                "config_read",
                "menu_parent_options",
                "menu_list",
                "menu_create",
                "menu_view",
                "dashboard_list",
                "dashboard_create",
                "dashboard_view");
    }

    @Test
    void rejectedDashboardApprovalDoesNotProduceSuccessRecord() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, "dashboard_create", null);

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applyDashboardLowCode",
                context);

        assertThat(response)
                .contains("执行数据可视化演示工作流失败")
                .contains("用户拒绝了 MCP 工具调用")
                .contains("未生成配置、菜单或看板成功记录")
                .doesNotContain("zenvis:dashboard-config-record");
        assertThat(calls)
                .contains("dashboard_create")
                .doesNotContain("dashboard_view");
    }

    @Test
    void rejectedConfigApprovalStopsBeforeReadBackAndMenuCreation() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, "config_apply", null);

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applySinglePageLowCode",
                context);

        assertThat(response)
                .contains("执行数据可视化演示工作流失败")
                .contains("config_apply 未成功")
                .contains("用户拒绝了 MCP 工具调用")
                .doesNotContain("zenvis:visualization-config-record")
                .doesNotContain("zenvis:menu-config-record");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_ensure_root",
                "config_add",
                "config_apply");
    }

    @Test
    void configReadBackMismatchBlocksVisualizationRecords() {
        List<String> calls = new ArrayList<>();
        McpToolContext context = visualizationWriteContext(
                calls, null, "config_read");

        String response = ReflectionTestUtils.invokeMethod(
                service,
                "applySinglePageLowCode",
                context);

        assertThat(response)
                .contains("执行数据可视化演示工作流失败")
                .contains("config_read 读回与已确认配置不一致")
                .doesNotContain("zenvis:visualization-config-record")
                .doesNotContain("zenvis:menu-config-record");
        assertThat(calls).containsExactly(
                "config_tree",
                "config_ensure_root",
                "config_add",
                "config_apply",
                "config_read");
    }

    private McpToolContext realDataContext(List<String> calls) {
        return toolContext(
                metaEntityCallback(calls),
                metaAttributeCallback(calls),
                callback("entity_overview", calls, ignored -> """
                        {
                          "meta":{"query_type":"overview"},
                          "result":{"rows":[],"total":0},
                          "echarts":{"chart_type":"bar","option":{"series":[]}}
                        }
                        """),
                callback("entity_aggregate", calls, arguments -> {
                    assertThat(arguments)
                            .contains("\"entity\":\"user_event\"")
                            .contains("\"time_field\":\"server_time\"");
                    return """
                            {
                              "meta":{"query_type":"aggregate","result_count":1,"truncated":false},
                              "result":{"rows":[{"event_time":"2026-07-30 10:00:00","event_count":4}]},
                              "echarts":{
                                "chart_type":"line",
                                "option":{
                                  "sentinel":987654321,
                                  "dataset":{"source":[["event_time","event_count"],["2026-07-30 10:00:00",4]]},
                                  "xAxis":{"type":"category"},
                                  "yAxis":{"type":"value"},
                                  "series":[{"type":"line"}]
                                }
                              }
                            }
                            """;
                })
        );
    }

    private ToolCallback metaEntityCallback(List<String> calls) {
        return callback("retrieval_list_display_entity", calls, ignored -> """
                {"entityList":[{"name":"user_event","label":"用户事件数据"}]}
                """);
    }

    private ToolCallback metaAttributeCallback(List<String> calls) {
        return callback("retrieval_list_display_attribute", calls, arguments -> {
            assertThat(arguments).contains("\"entity\":\"user_event\"");
            return """
                    {
                      "entity":"user_event",
                      "attributeList":[
                        {"name":"event_id"},{"name":"procid"},{"name":"user"},
                        {"name":"event_type"},{"name":"reliability"},{"name":"detail"},
                        {"name":"tags"},{"name":"server_time"}
                      ]
                    }
                    """;
        });
    }

    private McpToolContext visualizationWriteContext(
            List<String> calls,
            String rejectedTool,
            String mismatchTool) {
        Map<String, String> configs = new HashMap<>();
        Map<Long, Map<String, Object>> menus = new HashMap<>();
        Map<Long, Map<String, Object>> dashboards = new HashMap<>();
        AtomicInteger menuIds = new AtomicInteger(30);
        AtomicInteger dashboardIds = new AtomicInteger(40);

        return toolContext(
                callback("config_tree", calls, ignored -> "[]"),
                callback("config_ensure_root", calls, ignored ->
                        approvalResult("config_ensure_root", rejectedTool)),
                callback("config_add", calls, ignored ->
                        approvalResult("config_add", rejectedTool)),
                callback("config_apply", calls, arguments -> {
                    if ("config_apply".equals(rejectedTool)) {
                        return rejectedResult();
                    }
                    Map<String, Object> input = jsonMap(arguments);
                    Map<String, Object> config = mapValue(
                            input.get("configDto"));
                    configs.put(
                            input.get("type") + "/"
                                    + config.get("fileName"),
                            String.valueOf(config.get("text")));
                    return "true";
                }),
                callback("config_read", calls, arguments -> {
                    if ("config_read".equals(mismatchTool)) {
                        return JacksonUtil.toJson("{\"unexpected\":true}");
                    }
                    Map<String, Object> input = jsonMap(arguments);
                    String content = configs.get(
                            input.get("type") + "/"
                                    + input.get("fileName"));
                    return JacksonUtil.toJson(content);
                }),
                callback("menu_parent_options", calls, ignored -> """
                        {"options":[{"label":"配置管理","value":"1"}]}
                        """),
                callback("menu_list", calls, ignored -> """
                        {"rows":[],"total":0}
                        """),
                callback("menu_create", calls, arguments -> {
                    if ("menu_create".equals(rejectedTool)) {
                        return rejectedResult();
                    }
                    Map<String, Object> request = new LinkedHashMap<>(
                            mapValue(jsonMap(arguments).get("request")));
                    if ("POLICY_CONFIG".equals(request.get("type"))) {
                        assertThat(request)
                                .containsEntry("level", "LEVEL_2")
                                .containsEntry("parentId", 1);
                    } else {
                        assertThat(request)
                                .containsEntry("level", "LEVEL_1")
                                .containsEntry("parentId", 0);
                    }
                    long id = menuIds.incrementAndGet();
                    request.put("id", id);
                    menus.put(id, request);
                    return JacksonUtil.toJson(request);
                }),
                callback("menu_view", calls, arguments -> {
                    long id = longValue(jsonMap(arguments).get("id"));
                    Map<String, Object> result =
                            new LinkedHashMap<>(menus.get(id));
                    if ("menu_view".equals(mismatchTool)) {
                        result.put("name", "被篡改的菜单");
                    }
                    return JacksonUtil.toJson(result);
                }),
                callback("dashboard_list", calls, ignored -> """
                        {"rows":[],"total":0}
                        """),
                callback("dashboard_create", calls, arguments -> {
                    if ("dashboard_create".equals(rejectedTool)) {
                        return rejectedResult();
                    }
                    Map<String, Object> request = new LinkedHashMap<>(
                            mapValue(jsonMap(arguments).get("request")));
                    long id = dashboardIds.incrementAndGet();
                    request.put("id", id);
                    dashboards.put(id, request);
                    return JacksonUtil.toJson(request);
                }),
                callback("dashboard_view", calls, arguments -> {
                    long id = longValue(jsonMap(arguments).get("id"));
                    Map<String, Object> result =
                            new LinkedHashMap<>(dashboards.get(id));
                    if ("dashboard_view".equals(mismatchTool)) {
                        result.put("code", "mismatched-dashboard");
                    }
                    return JacksonUtil.toJson(result);
                })
        );
    }

    private String approvalResult(String toolName,
                                  String rejectedTool) {
        return toolName.equals(rejectedTool) ? rejectedResult() : "true";
    }

    private String rejectedResult() {
        return """
                {"status":"rejected","message":"用户拒绝了 MCP 工具调用"}
                """;
    }

    private Map<String, Object> jsonMap(String json) {
        return JacksonUtil.toMap(
                json,
                new TypeReference<Map<String, Object>>() {
                });
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, entryValue) ->
                normalized.put(String.valueOf(key), entryValue));
        return normalized;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String responseOf(Optional<Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.get().collectList().block());
    }

    private McpToolContext toolContext(ToolCallback... callbacks) {
        return new McpToolContext(
                ToolCallbackProvider.from(callbacks),
                "deterministic visualization demo tools");
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

    private static class FakeConfigService implements ConfigService {
        private boolean metaExists;
        private final List<String> ensuredRoots = new ArrayList<>();
        private final List<String> addedFiles = new ArrayList<>();
        private final List<String> existingFiles = new ArrayList<>();

        @Override
        public List<ConfigVo> getConfigFileTree(String type) {
            return List.of();
        }

        @Override
        public String readFileSchema(String type, String fileName) {
            return null;
        }

        @Override
        public String readFile(String type, String fileName) {
            return null;
        }

        @Override
        public void modifyConfig(String type, ConfigDto configDto) {
            existingFiles.add(type + "/" + configDto.getFileName());
        }

        @Override
        public boolean addFile(String type, String fileName) {
            addedFiles.add(type + "/" + fileName);
            existingFiles.add(type + "/" + fileName);
            return true;
        }

        @Override
        public boolean renameFile(String type, String originalFile, String newFile) {
            return false;
        }

        @Override
        public boolean deleteFile(String type, String fileName) {
            return false;
        }

        @Override
        public String configPath(String type) {
            return "";
        }

        @Override
        public void applyConfig(String type, ConfigDto configDto) {
        }

        @Override
        public boolean addRootPath(String type) {
            return ensureRootPath(type);
        }

        @Override
        public boolean ensureRootPath(String type) {
            ensuredRoots.add(type);
            return true;
        }

        @Override
        public boolean fileExistsInConfigPath(String type, String fileName) {
            if ("meta".equals(type)) {
                return metaExists;
            }
            return existingFiles.contains(type + "/" + fileName);
        }
    }

    private static class FakeMenuService implements MenuService {
        private List<Menu> parentMenus = List.of();
        private final List<Menu> menus = new ArrayList<>();
        private int nextId = 10;

        @Override
        public List<MenuVo> findAll() {
            return menus.stream().map(MenuVo::new).toList();
        }

        @Override
        public Menu create(MenuDto menuDto) {
            Menu menu = new Menu()
                    .setName(menuDto.getName())
                    .setType(menuDto.getType())
                    .setParams(menuDto.getParams())
                    .setRoute(menuDto.getType() == null ? null : menuDto.getType().getRoute())
                    .setParentId(menuDto.getParentId())
                    .setLevel(menuDto.getLevel())
                    .setSource(menuDto.getSource());
            menu.setId(nextId++);
            menus.add(menu);
            return menu;
        }

        @Override
        public Boolean update(Long id, MenuDto menuDto) {
            return false;
        }

        @Override
        public Boolean updateOrder(MenuOrderRowDto menuOrderRowDto) {
            return false;
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public void deleteByIds(List<Long> ids) {
        }

        @Override
        public MenuVo info(Long id) {
            return null;
        }

        @Override
        public PageRowsVo<MenuVo> getPageList(MenuSearchDto menuSearchDto) {
            return null;
        }

        @Override
        public List<Menu> findAllParentMenu() {
            return parentMenus;
        }

        @Override
        public List<Menu> findBySource(String source) {
            return menus.stream().filter(menu -> source.equals(menu.getSource())).toList();
        }
    }

    private static class FakeDashboardService implements DashboardService {
        private final List<Dashboard> dashboards = new ArrayList<>();
        private int nextId = 20;

        @Override
        public List<DashboardVo> findAll() {
            return dashboards.stream().map(DashboardVo::new).toList();
        }

        @Override
        public Dashboard create(DashboardDto dashboardDto) {
            Dashboard dashboard = new Dashboard();
            dashboard.updateFromDto(dashboardDto);
            dashboard.setId(nextId++);
            dashboards.add(dashboard);
            return dashboard;
        }

        @Override
        public Boolean update(Long id, DashboardDto dashboardDto) {
            return false;
        }

        @Override
        public Boolean bulkUpdate(List<Long> ids, DashboardDto dashboardDto) {
            return false;
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public void deleteByIds(List<Long> ids) {
        }

        @Override
        public DashboardVo info(Long id) {
            return null;
        }

        @Override
        public PageRowsVo<DashboardVo> getPageList(DashboardSearchDto dashboardSearchDto) {
            return null;
        }
    }
}
