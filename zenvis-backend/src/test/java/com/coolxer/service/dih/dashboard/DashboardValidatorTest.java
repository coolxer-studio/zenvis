package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.retrieval.MetaDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardValidatorTest {

    private DashboardValidator validator;
    private DataEntity entity;

    @BeforeEach
    void setUp() {
        MetaDataService metaDataService = mock(MetaDataService.class);
        validator = new DashboardValidator(metaDataService);
        entity = new DataEntity();
        entity.setName("user_event");
        entity.setTableName("user_event");
        List<DataAttribute> attributes = List.of(
                attribute("zenvis_id", "String", List.of("equal")),
                attribute("server_time", "DateTime64(3)", List.of("between")),
                attribute("event_type", "String", List.of("equal", "in")),
                attribute("event_name", "String", List.of("like")),
                attribute("duration", "UInt32", List.of("between", "gte")));
        when(metaDataService.getDataEntityByName("user_event")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(attributes);
    }

    @Test
    void acceptsACompletePublishedMetaBackedDashboardSpec() {
        DashboardValidator.Result result = validator.validate(validSpec());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.passed()).isTrue();
        assertThat(result.spec().getSchemaVersion()).isEqualTo("1.0");
        assertThat(result.spec().getMode()).isEqualTo("HTML_PAGE");
        assertThat(result.spec().getRefreshIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void rejectsUnknownEntitiesAndFields() {
        DashboardSpec unknownEntity = validSpec();
        unknownEntity.getBlocks().get(0).getQuery().setEntity("missing_entity");
        assertThat(codes(validator.validate(unknownEntity))).contains("ENTITY_UNKNOWN");

        DashboardSpec unknownField = validSpec();
        unknownField.getBlocks().get(1).getQuery().getRequest().put("dimension", "missing_field");
        assertThat(codes(validator.validate(unknownField))).contains("FIELD_UNKNOWN");

        DashboardSpec unknownTime = validSpec();
        unknownTime.setTimeField("missing_time");
        assertThat(codes(validator.validate(unknownTime))).contains("TIME_FIELD_UNKNOWN");
    }

    @Test
    void rejectsIllegalTimeRangesAndDefaults() {
        DashboardSpec spec = validSpec();
        spec.setAllowedTimeRanges(new ArrayList<>(List.of("TODAY", "LAST_7_DAYS")));
        spec.setDefaultTimeRange("LAST_30_DAYS");

        assertThat(codes(validator.validate(spec)))
                .contains("TIME_RANGE_UNSUPPORTED", "DEFAULT_TIME_RANGE_INVALID");
    }

    @Test
    void rejectsChartAndMetricToolMismatches() {
        DashboardSpec metrics = validSpec();
        metrics.getBlocks().get(0).getQuery().setTool("entity_trend");
        assertThat(codes(validator.validate(metrics))).contains("METRIC_TOOL_UNSUPPORTED");

        DashboardSpec chart = validSpec();
        chart.getBlocks().get(1).getQuery().setTool("entity_summary");
        assertThat(codes(validator.validate(chart))).contains("CHART_TOOL_UNSUPPORTED");
    }

    @Test
    void rejectsLayoutOverflowDuplicateBlocksAndMissingReferences() {
        DashboardSpec overflow = validSpec();
        overflow.getLayout().getRows().get(1).setBlocks(new ArrayList<>(List.of(
                item("trend", 8), item("records", 8))));
        overflow.getLayout().getRows().remove(2);
        assertThat(codes(validator.validate(overflow))).contains("LAYOUT_ROW_OVERFLOW");

        DashboardSpec duplicate = validSpec();
        duplicate.getBlocks().add(metricsBlock("metrics", "重复指标", "event_count"));
        assertThat(codes(validator.validate(duplicate))).contains("BLOCK_ID_DUPLICATE");

        DashboardSpec missing = validSpec();
        missing.getLayout().getRows().remove(2);
        assertThat(codes(validator.validate(missing))).contains("LAYOUT_BLOCK_MISSING");
    }

    private DashboardSpec validSpec() {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        spec.setDescription("趋势、分布与只读明细");
        spec.setTimeField("server_time");
        spec.setDefaultTimeRange("LAST_7_DAYS");
        spec.setAllowedTimeRanges(new ArrayList<>(DashboardSpec.TIME_PRESETS));
        spec.setBlocks(new ArrayList<>(List.of(
                metricsBlock("metrics", "关键指标", "event_count"),
                chartBlock("trend", "趋势"),
                recordsBlock("records", "事件明细"))));
        DashboardSpec.DashboardLayout layout = new DashboardSpec.DashboardLayout();
        layout.setColumns(12);
        layout.setRows(new ArrayList<>(List.of(
                row("kpi-row", item("metrics", 12)),
                row("chart-row", item("trend", 12)),
                row("detail-row", item("records", 12)))));
        spec.setLayout(layout);
        return spec;
    }

    private DashboardSpec.DashboardBlock metricsBlock(String id, String title, String field) {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId(id);
        block.setType("metrics");
        block.setTitle(title);
        block.setQuery(query("entity_summary", Map.of(
                "entity", "user_event",
                "metrics", List.of(Map.of(
                        "name", "event_count", "field", "zenvis_id", "operation", "COUNT")),
                "time_range", Map.of("preset", "LAST_7_DAYS"))));
        DashboardSpec.DashboardMetricCard card = new DashboardSpec.DashboardMetricCard();
        card.setLabel("事件数");
        card.setField(field);
        block.setMetricCards(new ArrayList<>(List.of(card)));
        return block;
    }

    private DashboardSpec.DashboardBlock chartBlock(String id, String title) {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId(id);
        block.setType("chart");
        block.setTitle(title);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("dimension", "event_type");
        request.put("time_range", Map.of("preset", "LAST_7_DAYS"));
        request.put("chart_hint", "PIE");
        block.setQuery(query("entity_distribution", request));
        return block;
    }

    private DashboardSpec.DashboardBlock recordsBlock(String id, String title) {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId(id);
        block.setType("records");
        block.setTitle(title);
        DashboardSpec.DashboardRecords records = new DashboardSpec.DashboardRecords();
        records.setEntity("user_event");
        DashboardSpec.DashboardRecordColumn column = new DashboardSpec.DashboardRecordColumn();
        column.setField("event_type");
        records.setColumns(new ArrayList<>(List.of(column)));
        DashboardSpec.DashboardRecordFilter filter = new DashboardSpec.DashboardRecordFilter();
        filter.setField("event_type");
        filter.setOperator("equal");
        records.setFilters(new ArrayList<>(List.of(filter)));
        records.setDefaultSortField("server_time");
        block.setRecords(records);
        return block;
    }

    private DashboardSpec.DashboardQuery query(String tool, Map<String, Object> request) {
        DashboardSpec.DashboardQuery query = new DashboardSpec.DashboardQuery();
        query.setTool(tool);
        query.setEntity("user_event");
        query.setRequest(new LinkedHashMap<>(request));
        return query;
    }

    private DashboardSpec.DashboardRow row(String id, DashboardSpec.DashboardLayoutItem item) {
        DashboardSpec.DashboardRow row = new DashboardSpec.DashboardRow();
        row.setRowId(id);
        row.setBlocks(new ArrayList<>(List.of(item)));
        return row;
    }

    private DashboardSpec.DashboardLayoutItem item(String blockId, int span) {
        DashboardSpec.DashboardLayoutItem item = new DashboardSpec.DashboardLayoutItem();
        item.setBlockId(blockId);
        item.setSpan(span);
        return item;
    }

    private DataAttribute attribute(String name, String type, List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("user_event");
        attribute.setName(name);
        attribute.setLabel(name);
        attribute.setColumnName(name);
        attribute.setColumnType(type);
        attribute.setOperators(operators);
        return attribute;
    }

    private List<String> codes(DashboardValidator.Result result) {
        return result.diagnostics().stream().map(DashboardDiagnostic::getCode).toList();
    }
}
