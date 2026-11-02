package com.coolxer.service.dih.dataapp;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.retrieval.MetaDataService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataAppCompilerTest {

    private MetaDataService metaDataService;
    private DataAppCompiler compiler;
    private DataEntity entity;
    private List<DataAttribute> attributes;

    @BeforeEach
    void setUp() {
        metaDataService = mock(MetaDataService.class);
        compiler = new DataAppCompiler(metaDataService);
        entity = new DataEntity();
        entity.setName("user_event");
        entity.setLabel("用户事件");
        entity.setTableName("user_event");
        attributes = List.of(
                attribute("zenvis_id", "记录 ID", "zenvis_id", "String", null, List.of()),
                attribute("server_time", "服务端时间", "server_time", "DateTime64(3)", "datetime", List.of("between")),
                attribute("event_type", "事件类型", "event_type", "String", null, List.of("equal", "in")),
                attribute("event_name", "事件名称", "event_name", "String", null, List.of("like")),
                attribute("duration", "耗时", "duration", "UInt32", null, List.of("between", "gte")),
                attribute("payload", "载荷", "payload", "JSON", "json", List.of("equal")),
                attribute("tags", "标签", "tags", "Array(String)", "array", List.of("in")),
                attribute("region", "区域", "region", "String", null, List.of("equal")),
                attribute("owner", "负责人", "owner", "String", null, List.of("equal")),
                attribute("trace_id", "链路 ID", "trace_id", "String", null, List.of("equal"))
        );
        when(metaDataService.getDataEntityByName("user_event")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(attributes);
    }

    @Test
    void compilesMetricCardsAgainstOverviewApi() throws Exception {
        DataAppSpec spec = singlePage(metricsBlock());

        Map<String, String> files = compiler.compile(spec, true, false).files();
        JsonNode index = json(files.get("index.json"));
        JsonNode service = index.at("/body/0/body");

        assertThat(service.path("type").asText()).isEqualTo("service");
        assertThat(service.at("/api/url").asText()).endsWith("/entity/overview/query");
        assertThat(service.at("/api/adaptor").asText())
                .contains("d.result&&d.result.rows&&d.result.rows[0]")
                .contains("out['events']");
        assertThat(service.at("/body/columns/0/body/title").asText()).isEqualTo("事件总数");
    }

    @Test
    void compilesTrendAndAggregateChartsWithServerOwnedUrls() throws Exception {
        DataAppSpec trend = singlePage(chartBlock("trend", "entity_trend", Map.of(
                "chart_hint", "LINE",
                "time_range", Map.of("preset", "LAST_7_DAYS"),
                "granularity", "DAY",
                "series", List.of(Map.of(
                        "entity", "user_event",
                        "label", "事件数",
                        "metric", Map.of("operation", "COUNT"),
                        "time_field", "server_time")))));
        JsonNode chart = json(compiler.compile(trend, true, false).files().get("index.json"))
                .at("/body/0/body");
        assertThat(chart.at("/api/url").asText()).endsWith("/entity/trend/query");
        assertThat(chart.at("/api/adaptor").asText())
                .contains("chart_dataset")
                .contains("chart_series");
        assertThat(chart.at("/config/dataset").asText()).isEqualTo("${chart_dataset}");
        assertThat(chart.at("/config/series").asText()).isEqualTo("${chart_series}");
        assertThat(chart.at("/config/xAxis/type").asText()).isEqualTo("category");

        DataAppSpec aggregate = singlePage(chartBlock("aggregate", "entity_aggregate", Map.of(
                "chart_hint", "BAR",
                "dimensions", List.of(Map.of("field", "event_type", "kind", "CATEGORY")),
                "metrics", List.of(Map.of("field", "duration", "operation", "AVG")))));
        JsonNode aggregateChart = json(compiler.compile(aggregate, true, false).files().get("index.json"))
                .at("/body/0/body");
        assertThat(aggregateChart.at("/api/url").asText()).endsWith("/entity/aggregate/query");
        assertThat(aggregateChart.at("/config/xAxis/type").asText()).isEqualTo("category");
    }

    @Test
    void compilesDistributionHistogramAndScatterCharts() throws Exception {
        DataAppSpec distribution = singlePage(chartBlock("distribution", "entity_distribution", Map.of(
                "dimension", "event_type", "chart_hint", "PIE")));
        JsonNode pie = json(compiler.compile(distribution, true, false).files().get("index.json"))
                .at("/body/0/body");
        assertThat(pie.at("/api/url").asText()).endsWith("/entity/distribution/query");
        assertThat(pie.at("/config/xAxis").isMissingNode()).isTrue();
        assertThat(pie.at("/config/tooltip/trigger").asText()).isEqualTo("item");

        DataAppSpec histogram = singlePage(chartBlock("histogram", "entity_histogram", Map.of(
                "field", "duration", "bins", 20, "chart_hint", "HISTOGRAM")));
        JsonNode histogramChart = json(compiler.compile(histogram, true, false).files().get("index.json"))
                .at("/body/0/body");
        assertThat(histogramChart.at("/api/url").asText()).endsWith("/entity/histogram/query");
        assertThat(histogramChart.at("/config/xAxis/axisLabel/rotate").asInt()).isEqualTo(30);

        DataAppSpec scatter = singlePage(chartBlock("scatter", "entity_scatter", Map.of(
                "x_field", "duration", "y_field", "duration", "chart_hint", "SCATTER")));
        JsonNode scatterChart = json(compiler.compile(scatter, true, false).files().get("index.json"))
                .at("/body/0/body");
        assertThat(scatterChart.at("/api/url").asText()).endsWith("/entity/scatter/query");
        assertThat(scatterChart.at("/config/xAxis/type").asText()).isEqualTo("value");
        assertThat(scatterChart.at("/config/dataset").isMissingNode()).isTrue();
    }

    @Test
    void compilesRecordListFiltersDetailCrudAndConfirmation() throws Exception {
        DataAppSpec spec = singlePage(recordsBlock(true));

        JsonNode crud = json(compiler.compile(spec, true, false).files().get("index.json"))
                .at("/body/0");

        assertThat(crud.path("type").asText()).isEqualTo("crud");
        assertThat(crud.at("/api/url").asText()).endsWith("/retrieval/do");
        assertThat(crud.at("/api/data/display_list/0/attribute_list").toString())
                .contains("event_type", "duration", "zenvis_id");
        assertThat(crud.at("/api/requestAdaptor").asText())
                .contains("add('event_type','equal',values['event_type'])")
                .contains("api.data.criteria_list=criteria");

        JsonNode filterBody = crud.at("/filter/body");
        assertThat(filterBody.findValuesAsText("name")).contains("event_type", "duration");
        assertThat(filterBody.get(0).path("type").asText()).isEqualTo("select");
        assertThat(filterBody.get(1).path("type").asText()).isEqualTo("input-number-range");

        JsonNode operation = crud.at("/columns/3");
        assertThat(operation.path("type").asText()).isEqualTo("operation");
        assertThat(operation.findValuesAsText("label")).contains("详情", "编辑", "删除");
        assertThat(operation.at("/buttons/0/dialog/body/api").asText())
                .isEqualTo("get:/zenvis/api/v1/entity/user_event/${zenvis_id}/view");
        assertThat(operation.at("/buttons/1/dialog/body/api").asText())
                .isEqualTo("post:/zenvis/api/v1/entity/user_event/${zenvis_id}/update");
        assertThat(operation.at("/buttons/2/confirmText").asText()).contains("确认删除");
        assertThat(operation.at("/buttons/2/api").asText())
                .isEqualTo("delete:/zenvis/api/v1/entity/user_event/${zenvis_id}");

        JsonNode createForm = crud.at("/headerToolbar/0/dialog/body");
        assertThat(createForm.at("/api").asText()).isEqualTo("post:/zenvis/api/v1/entity/user_event/add");
        assertThat(createForm.findValuesAsText("name"))
                .contains("event_type", "duration", "payload", "tags")
                .doesNotContain("zenvis_id");
        assertThat(createForm.findValuesAsText("type"))
                .contains("select", "input-number", "json-editor", "input-tag");
        JsonNode durationControl = findControl(createForm.path("body"), "duration");
        assertThat(durationControl.path("required").asBoolean()).isTrue();
    }

    @Test
    void previewModeRemovesEveryWriteAction() throws Exception {
        DataAppSpec spec = singlePage(recordsBlock(true));
        JsonNode crud = json(compiler.compile(spec, true, true).files().get("index.json"))
                .at("/body/0");

        assertThat(crud.has("headerToolbar")).isFalse();
        assertThat(crud.findValuesAsText("label")).contains("详情").doesNotContain("新建", "编辑", "删除");
        assertThat(crud.at("/columns/3/buttons")).hasSize(1);
    }

    @Test
    void compilesMappingCandidateAndAutocompleteFieldsAsSelectSources() throws Exception {
        DataAppSpec.DataAppBlock block = recordsBlock(true);
        block.getRecords().setFilters(new ArrayList<>(List.of(
                filter("region", "equal"),
                filter("owner", "equal"),
                filter("trace_id", "equal"))));
        DataAppSpec spec = singlePage(block);

        JsonNode crud = json(compiler.compile(spec, true, false).files().get("index.json"))
                .at("/body/0");
        assertThat(crud.at("/filter/actions/1/type").asText()).isEqualTo("submit");
        assertThat(crud.at("/filter/actions/1/label").asText()).isEqualTo("查询");
        assertThat(crud.at("/api/requestAdaptor").asText())
                .contains("var values=(context&&typeof context==='object')?context:{}")
                .contains("values['region']")
                .doesNotContain("data['event_type']");
        JsonNode createForm = crud.at("/headerToolbar/0/dialog/body");

        JsonNode region = findControl(createForm.path("body"), "region");
        assertThat(region.path("type").asText()).isEqualTo("select");
        assertThat(region.path("source").asText()).endsWith("/entity/user_event/region/mapping");

        JsonNode owner = findControl(createForm.path("body"), "owner");
        assertThat(owner.path("type").asText()).isEqualTo("select");
        assertThat(owner.path("source").asText()).endsWith("/entity/user_event/owner/list");
        assertThat(owner.path("searchable").asBoolean()).isTrue();

        JsonNode traceId = findControl(createForm.path("body"), "trace_id");
        assertThat(traceId.path("type").asText()).isEqualTo("select");
        assertThat(traceId.path("autoComplete").asText())
                .endsWith("/entity/user_event/trace_id/auto-complete?term=$term");
        assertThat(traceId.path("searchable").asBoolean()).isTrue();

        JsonNode filters = crud.at("/filter/body");
        assertThat(findControl(filters, "region").path("type").asText()).isEqualTo("select");
        assertThat(findControl(filters, "owner").path("source").asText()).endsWith("/owner/list");
        assertThat(findControl(filters, "trace_id").path("autoComplete").asText())
                .contains("/trace_id/auto-complete");
    }

    @Test
    void singlePageAndMultiPagePackagesUseManagedLayouts() throws Exception {
        DataAppSpec single = singlePage(metricsBlock());
        DataAppCompiler.CompiledFiles singleFiles = compiler.compile(single, true, false);
        assertThat(singleFiles.menuType()).isEqualTo("LOW_CODE_PAGE");
        assertThat(singleFiles.files()).containsOnlyKeys("index.json");

        DataAppSpec.DataAppPage second = page("details", "明细", recordsBlock(false));
        single.getPages().add(second);
        DataAppCompiler.CompiledFiles multiFiles = compiler.compile(single, false, false);
        assertThat(multiFiles.menuType()).isEqualTo("LOW_CODE_APP");
        assertThat(multiFiles.files()).containsOnlyKeys(
                "pages/overview.json", "pages/details.json", "site.json");

        JsonNode site = json(multiFiles.files().get("site.json"));
        assertThat(site.at("/data/pages/1/children/0/schemaApi").asText())
                .isEqualTo("get:/zenvis/api/v1/config/user-event-analysis/get?file_name=pages%2Foverview.json");
        assertThat(site.at("/data/pages/1/children/1/url").asText()).isEqualTo("details");
    }

    @Test
    void previewSiteUsesOnlyTheScopedPreviewApi() throws Exception {
        DataAppSpec spec = singlePage(metricsBlock());
        String previewApi = "/api/v1/dih/chat/workflows/wf-1/app-preview/files?chat_id=chat-1&token=t-1";
        Map<String, String> files = compiler.compile(spec, true, true, previewApi).files();

        JsonNode site = json(files.get("site.json"));
        assertThat(site.at("/data/pages/1/children/0/schemaApi").asText())
                .isEqualTo("get:" + previewApi + "&path=index.json");
        assertThat(files.get("index.json")).contains("/entity/overview/query");
    }

    private DataAppSpec singlePage(DataAppSpec.DataAppBlock block) {
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId("user-event-analysis");
        spec.setName("用户事件分析");
        spec.setHomePageId("overview");
        spec.setPages(new ArrayList<>(List.of(page("overview", "总览", block))));
        return spec;
    }

    private DataAppSpec.DataAppPage page(String id, String title, DataAppSpec.DataAppBlock block) {
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId(id);
        page.setTitle(title);
        page.setIcon("fa-solid fa-chart-line");
        page.setBlocks(new ArrayList<>(List.of(block)));
        return page;
    }

    private DataAppSpec.DataAppBlock metricsBlock() {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId("metrics-summary");
        block.setType("metrics");
        block.setTitle("关键指标");
        block.setQuery(query("entity_overview", Map.of(
                "entity", "user_event", "time_range", Map.of("preset", "TODAY"))));
        DataAppSpec.DataAppMetricCard card = new DataAppSpec.DataAppMetricCard();
        card.setLabel("事件总数");
        card.setField("events");
        block.setMetricCards(new ArrayList<>(List.of(card)));
        return block;
    }

    private DataAppSpec.DataAppBlock chartBlock(String id, String tool, Map<String, Object> request) {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId(id);
        block.setType("chart");
        block.setTitle(id);
        Map<String, Object> data = new LinkedHashMap<>(request);
        data.putIfAbsent("entity", "user_event");
        block.setQuery(query(tool, data));
        return block;
    }

    private DataAppSpec.DataAppBlock recordsBlock(boolean writable) {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId("records");
        block.setType("records");
        block.setTitle("事件明细");
        DataAppSpec.DataAppRecords records = new DataAppSpec.DataAppRecords();
        records.setEntity("user_event");
        records.setColumns(new ArrayList<>(List.of(
                column("event_type", "事件类型"),
                column("duration", "耗时"))));
        records.setFilters(new ArrayList<>(List.of(
                filter("event_type", "equal"),
                filter("duration", "between"))));
        records.setDetail(true);
        records.setCreate(writable);
        records.setUpdate(writable);
        records.setDelete(writable);
        records.setPageSize(20);
        block.setRecords(records);
        return block;
    }

    private DataAppSpec.DataAppQuery query(String tool, Map<String, Object> request) {
        DataAppSpec.DataAppQuery query = new DataAppSpec.DataAppQuery();
        query.setTool(tool);
        query.setEntity("user_event");
        query.setRequest(new LinkedHashMap<>(request));
        return query;
    }

    private DataAppSpec.DataAppRecordColumn column(String field, String label) {
        DataAppSpec.DataAppRecordColumn column = new DataAppSpec.DataAppRecordColumn();
        column.setField(field);
        column.setLabel(label);
        return column;
    }

    private DataAppSpec.DataAppRecordFilter filter(String field, String operator) {
        DataAppSpec.DataAppRecordFilter filter = new DataAppSpec.DataAppRecordFilter();
        filter.setField(field);
        filter.setOperator(operator);
        return filter;
    }

    private DataAttribute attribute(String name,
                                    String label,
                                    String column,
                                    String columnType,
                                    String displayType,
                                    List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("user_event");
        attribute.setName(name);
        attribute.setLabel(label);
        attribute.setColumnName(column);
        attribute.setColumnType(columnType);
        attribute.setDisplayType(displayType);
        attribute.setOperators(operators);
        if ("duration".equals(name)) {
            attribute.setRequired(true);
        }
        if ("event_type".equals(name)) {
            attribute.setEnums(List.of("login", "logout"));
        }
        if ("region".equals(name)) {
            attribute.setMapping(Map.of("cn", "中国", "us", "美国"));
        }
        if ("owner".equals(name)) {
            attribute.setMustCandidate(true);
        }
        if ("trace_id".equals(name)) {
            attribute.setAutoComplete(true);
        }
        return attribute;
    }

    private JsonNode json(String value) throws Exception {
        return JacksonConfig.OBJECT_MAPPER.readTree(value);
    }

    private JsonNode findControl(JsonNode body, String name) {
        for (JsonNode control : body) {
            if (name.equals(control.path("name").asText())) {
                return control;
            }
        }
        throw new AssertionError("未找到表单控件：" + name);
    }
}
