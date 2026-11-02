package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardCompilerTest {

    private final DashboardCompiler compiler = new DashboardCompiler();

    @Test
    void compilesTheSameSpecToDeterministicHtmlAndAmis() {
        DashboardSpec spec = spec();
        DashboardCompiler.CompiledFiles first = compiler.compile(spec, false);
        DashboardCompiler.CompiledFiles second = compiler.compile(spec, false);

        assertThat(first.dashboardType()).isEqualTo("HTML_PAGE");
        assertThat(first.files()).containsOnlyKeys("user-event-dashboard.html");
        assertThat(first.files()).isEqualTo(second.files());
        assertThat(first.files().get("user-event-dashboard.html"))
                .contains("DARK_TECH", "dashboard-row", "data-chart", "data-record-rows")
                .contains("rowsByMetric", "metricRow.value", "item.metric ?? item.name")
                .contains("/echarts/echarts.min.js")
                .doesNotContain("cdn.", "https://")
                .doesNotContain("/entity/user_event/add", "/entity/user_event/update", "/entity/user_event/delete",
                        "config_apply", "dashboard_create", "menu_create");

        spec.setMode(DashboardSpec.MODE_LOW_CODE);
        DashboardCompiler.CompiledFiles amis = compiler.compile(spec, false);
        assertThat(amis.dashboardType()).isEqualTo("LOW_CODE_PAGE");
        assertThat(amis.files()).containsOnlyKeys("index.json");
        assertThat(amis.files().get("index.json"))
                .contains("\"type\":\"page\"", "/entity/overview/query", "/retrieval/do")
                .doesNotContain("/entity/user_event/add", "/entity/user_event/update", "/entity/user_event/delete",
                        "config_apply", "dashboard_create", "menu_create");
    }

    @Test
    void escapesLabelsAndJsonPayloadWithoutScriptBreakout() {
        DashboardSpec spec = spec();
        spec.setName("<script>alert(1)</script>");
        spec.setDescription("\"><img src=x onerror=alert(2)>");
        spec.getBlocks().get(0).setTitle("</script><script>alert(3)</script>");

        String html = compiler.compile(spec, false).files().get("user-event-dashboard.html");

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&lt;/script&gt;&lt;script&gt;alert(3)&lt;/script&gt;")
                .contains("\\u003cscript\\u003ealert(1)\\u003c/script\\u003e")
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("</script><script>alert(3)</script>");
    }

    @Test
    void previewUsesOnlyScopedFilesForLowCodeMode() {
        DashboardSpec spec = spec();
        spec.setMode(DashboardSpec.MODE_LOW_CODE);
        String api = "/api/v1/dih/chat/workflows/wf-1/dashboard-preview/files?chat_id=chat-1&token=t-1";

        Map<String, String> files = compiler.previewFiles(spec, api);
        String contentPath = compiler.previewContentPath(spec);

        assertThat(files).containsOnlyKeys("preview.html", "site.json", "index.json");
        assertThat(contentPath).isEqualTo("preview.html");
        assertThat(files.get("site.json")).contains(api + "&path=index.json");
        assertThat(files.get("preview.html"))
                .contains(api.substring(0, api.indexOf('&')))
                .contains("\\u0026path=site.json");
        assertThat(files.get("index.json")).doesNotContain(api);
    }

    private DashboardSpec spec() {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        spec.setDescription("趋势与明细");
        spec.setMode(DashboardSpec.MODE_HTML);
        spec.setTheme(DashboardSpec.THEME_DARK_TECH);
        spec.setTimeField("server_time");
        spec.setDefaultTimeRange("LAST_7_DAYS");
        spec.setBlocks(new ArrayList<>(List.of(metrics(), chart(), records())));
        DashboardSpec.DashboardLayout layout = new DashboardSpec.DashboardLayout();
        layout.setColumns(12);
        layout.setRows(new ArrayList<>(List.of(
                row("kpi-row", "metrics", 12),
                row("chart-row", "chart", 8),
                row("detail-row", "records", 12))));
        spec.setLayout(layout);
        return spec;
    }

    private DashboardSpec.DashboardRow row(String rowId, String blockId, int span) {
        DashboardSpec.DashboardRow row = new DashboardSpec.DashboardRow();
        row.setRowId(rowId);
        DashboardSpec.DashboardLayoutItem item = new DashboardSpec.DashboardLayoutItem();
        item.setBlockId(blockId);
        item.setSpan(span);
        row.setBlocks(new ArrayList<>(List.of(item)));
        return row;
    }

    private DashboardSpec.DashboardBlock metrics() {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId("metrics");
        block.setType("metrics");
        block.setTitle("关键指标");
        DashboardSpec.DashboardQuery query = new DashboardSpec.DashboardQuery();
        query.setTool("entity_overview");
        query.setEntity("user_event");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entities", List.of("user_event"));
        request.put("time_range", Map.of("preset", "LAST_7_DAYS"));
        query.setRequest(request);
        block.setQuery(query);
        DashboardSpec.DashboardMetricCard card = new DashboardSpec.DashboardMetricCard();
        card.setLabel("事件数");
        card.setField("events");
        block.setMetricCards(new ArrayList<>(List.of(card)));
        return block;
    }

    private DashboardSpec.DashboardBlock chart() {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId("chart");
        block.setType("chart");
        block.setTitle("事件趋势");
        DashboardSpec.DashboardQuery query = new DashboardSpec.DashboardQuery();
        query.setTool("entity_trend");
        query.setEntity("user_event");
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", "事件数");
        series.put("metric", Map.of("field", "zenvis_id", "operation", "COUNT"));
        series.put("time_field", "server_time");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("series", List.of(series));
        request.put("time_range", Map.of("preset", "LAST_7_DAYS"));
        query.setRequest(request);
        block.setQuery(query);
        return block;
    }

    private DashboardSpec.DashboardBlock records() {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId("records");
        block.setType("records");
        block.setTitle("最近事件");
        DashboardSpec.DashboardRecords records = new DashboardSpec.DashboardRecords();
        records.setEntity("user_event");
        DashboardSpec.DashboardRecordColumn column = new DashboardSpec.DashboardRecordColumn();
        column.setField("event_type");
        column.setLabel("事件类型");
        records.setColumns(new ArrayList<>(List.of(column)));
        block.setRecords(records);
        return block;
    }
}
