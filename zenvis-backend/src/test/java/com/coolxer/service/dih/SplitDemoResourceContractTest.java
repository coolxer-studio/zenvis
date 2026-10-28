package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SplitDemoResourceContractTest {

    private static final String METADATA_ROOT = "demo/metadata-config/";
    private static final String PUSH_ROOT = "demo/data-push/";
    private static final String CHART_ROOT = "demo/visualization-chart/";
    private static final String APP_ROOT = "demo/visualization-app/";
    private static final String DASHBOARD_ROOT = "demo/visualization-dashboard/";

    @Test
    void metadataDemoLoadsOnlyMetadataResources() throws Exception {
        String requirement = resource(METADATA_ROOT, "user-event-requirement.md");
        String meta = resource(METADATA_ROOT, "user-event-meta.json");
        MetadataConfigDemoResponseService service =
                new MetadataConfigDemoResponseService();

        assertThat(MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT)
                .isEqualTo(requirement)
                .contains("| 实体英文名 | user_event |");
        assertThat(ReflectionTestUtils.getField(service, "DEMO_META_CONFIG"))
                .isEqualTo(meta);
    }

    @Test
    void pushDemoLoadsOnlyPushResources() throws Exception {
        String requirement = resource(PUSH_ROOT, "user-event-push-requirement.md");
        String push = resource(PUSH_ROOT, "user-event-push.toml");
        DataPushDemoResponseService service = new DataPushDemoResponseService();

        assertThat(DataPushDemoResponseService.USER_EVENT_EXAMPLE_PROMPT)
                .isEqualTo(requirement)
                .contains("user_event.json");
        assertThat(ReflectionTestUtils.getField(service, "DEMO_PUSH_CONFIG"))
                .isEqualTo(push);
    }

    @Test
    void metaAndPushResourcesKeepTheUserEventContract() throws Exception {
        JsonNode meta = JacksonConfig.OBJECT_MAPPER.readTree(
                resource(METADATA_ROOT, "user-event-meta.json"));
        List<String> attributes = meta.path("attribute").findValuesAsText("name");
        String push = resource(PUSH_ROOT, "user-event-push.toml");

        assertThat(meta.path("entity").path(0).path("name").asText())
                .isEqualTo("user_event");
        assertThat(meta.path("entity").path(0).path("label").asText())
                .isEqualTo("用户事件数据");
        assertThat(attributes).containsExactly(
                "event_id",
                "procid",
                "user",
                "event_type",
                "reliability",
                "detail",
                "tags",
                "server_time");
        assertThat(push)
                .contains(
                        "[sources.generator_demo_logs]",
                        "[transforms.parse_json]",
                        "[sinks.my_clickhouse_sink]",
                        "table = \"msg_user_event\"",
                        ".event_id = uuid_v4()",
                        ".server_time = format_timestamp!");
    }

    @Test
    void chartDemoLoadsOnlyChartResources() throws Exception {
        String chart = resource(CHART_ROOT, "chart-amis.json");
        VisualizationDemoSupport support = new VisualizationDemoSupport(
                null, Set.of(VisualizationDemoSupport.Scenario.CHART));

        assertThat(VisualizationChartDemoResponseService.CHART_EXAMPLE_PROMPT)
                .contains("临时性的可视化图表");
        assertThat(ReflectionTestUtils.getField(support, "CHART_AMIS_CONFIG"))
                .isEqualTo(chart);
        JsonNode config = JacksonConfig.OBJECT_MAPPER.readTree(chart);
        assertThat(config.path("type").asText()).isEqualTo("page");
        assertThat(config.path("title").asText()).isEqualTo("用户事件数据趋势");
        assertThat(config.toString()).contains("__QUERY_REQUEST__",
                "entity/aggregate/query", "echarts");
    }

    @Test
    void appDemoLoadsOnlyApplicationAndMenuResources() throws Exception {
        String page = resource(APP_ROOT, "user-event-page.json");
        String html = resource(APP_ROOT, "user-event-page.html");
        String site = resource(APP_ROOT, "user-event-app-site.json");
        String home = resource(APP_ROOT, "user-event-app-home.json");
        String trend = resource(APP_ROOT, "user-event-app-trend.json");
        VisualizationDemoSupport support = new VisualizationDemoSupport(
                null, Set.of(VisualizationDemoSupport.Scenario.PAGE,
                        VisualizationDemoSupport.Scenario.SIDEBAR_APP,
                        VisualizationDemoSupport.Scenario.MENU));

        assertThat(VisualizationAppDemoResponseService.PAGE_EXAMPLE_PROMPT)
                .contains("单页面应用");
        assertThat(VisualizationAppDemoResponseService.SIDEBAR_APP_EXAMPLE_PROMPT)
                .contains("侧边栏");
        assertThat(VisualizationAppDemoResponseService.MENU_EXAMPLE_PROMPT)
                .contains("应用菜单");
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_PAGE_CONFIG"))
                .isEqualTo(page);
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_PAGE_HTML"))
                .isEqualTo(html);
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_APP_SITE_CONFIG"))
                .isEqualTo(site);
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_APP_HOME_CONFIG"))
                .isEqualTo(home);
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_APP_TREND_CONFIG"))
                .isEqualTo(trend);
        assertThat(JacksonConfig.OBJECT_MAPPER.readTree(page).path("title").asText())
                .isEqualTo("用户事件数据管理");
        assertThat(JacksonConfig.OBJECT_MAPPER.readTree(site)
                .path("data").path("pages").size()).isGreaterThan(0);
        assertThat(html).contains("用户事件数据管理", "user_event", "entity/overview/query");
        assertThat(home).contains("user_event");
        assertThat(trend).contains("user_event");
    }

    @Test
    void dashboardDemoLoadsOnlyDashboardResources() throws Exception {
        String lowCode = resource(DASHBOARD_ROOT, "user-event-dashboard.json");
        String html = resource(DASHBOARD_ROOT, "user-event-dashboard.html");
        VisualizationDemoSupport support = new VisualizationDemoSupport(
                null, Set.of(VisualizationDemoSupport.Scenario.DASHBOARD));

        assertThat(VisualizationDashboardDemoResponseService.DASHBOARD_EXAMPLE_PROMPT)
                .contains("数据看板");
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_DASHBOARD_CONFIG"))
                .isEqualTo(lowCode);
        assertThat(ReflectionTestUtils.getField(support, "USER_EVENT_DASHBOARD_HTML"))
                .isEqualTo(html);
        assertThat(JacksonConfig.OBJECT_MAPPER.readTree(lowCode).path("title").asText())
                .isEqualTo("用户事件数据看板");
        assertThat(lowCode).contains("user_event", "entity/overview/query");
        assertThat(html).contains("用户事件数据态势看板", "entity/overview/query");
    }

    private String resource(String root, String fileName) throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(root + fileName)) {
            assertThat(stream).as(fileName).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
