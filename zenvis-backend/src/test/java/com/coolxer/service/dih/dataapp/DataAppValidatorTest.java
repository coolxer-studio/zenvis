package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppSpec;
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

class DataAppValidatorTest {

    private DataAppValidator validator;
    private DataEntity entity;

    @BeforeEach
    void setUp() {
        MetaDataService metaDataService = mock(MetaDataService.class);
        validator = new DataAppValidator(metaDataService);
        entity = new DataEntity();
        entity.setName("user_event");
        entity.setTableName("user_event");
        List<DataAttribute> attributes = List.of(
                attribute("zenvis_id", "zenvis_id", "String", List.of()),
                attribute("server_time", "server_time", "DateTime64(3)", List.of("between")),
                attribute("event_type", "event_type", "String", List.of("equal", "in")),
                attribute("event_name", "event_name", "String", List.of("like")),
                attribute("duration", "duration", "UInt32", List.of("between", "gte"))
        );
        when(metaDataService.getDataEntityByName("user_event")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(attributes);
    }

    @Test
    void acceptsACompletePublishedMetaBackedSpec() {
        DataAppSpec spec = validSpec();
        spec.setPages(new ArrayList<>(List.of(page("overview", List.of(metricsBlock())))));

        DataAppValidator.Result result = validator.validate(spec);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.passed()).isTrue();
        assertThat(result.spec().getSchemaVersion()).isEqualTo("1.0");
    }

    @Test
    void rejectsUnknownEntitiesFieldsAndOperators() {
        DataAppSpec spec = validSpec();
        DataAppSpec.DataAppBlock block = metricsBlock();
        block.getQuery().setEntity("missing_entity");
        spec.setPages(new ArrayList<>(List.of(page("overview", List.of(block)))));
        assertThat(codes(validator.validate(spec))).contains("ENTITY_UNKNOWN");

        DataAppSpec fieldSpec = validSpec();
        DataAppSpec.DataAppBlock fieldBlock = metricsBlock();
        fieldBlock.getQuery().getRequest().put("time_field", "missing_time");
        fieldSpec.setPages(new ArrayList<>(List.of(page("overview", List.of(fieldBlock)))));
        assertThat(codes(validator.validate(fieldSpec))).contains("FIELD_UNKNOWN");

        DataAppSpec operatorSpec = validSpec();
        DataAppSpec.DataAppBlock records = recordsBlock();
        records.getRecords().getFilters().get(0).setOperator("like");
        operatorSpec.setPages(new ArrayList<>(List.of(page("overview", List.of(records)))));
        assertThat(codes(validator.validate(operatorSpec))).contains("FILTER_OPERATOR_UNSUPPORTED");
    }

    @Test
    void enforcesPageBlockColumnFilterAndMetricLimits() {
        DataAppSpec manyPages = validSpec();
        List<DataAppSpec.DataAppPage> pages = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            pages.add(page("page-" + i, List.of(metricsBlock())));
        }
        manyPages.setPages(pages);
        manyPages.setHomePageId("page-0");
        assertThat(codes(validator.validate(manyPages))).contains("PAGE_COUNT_INVALID");

        DataAppSpec manyBlocks = validSpec();
        manyBlocks.setPages(new ArrayList<>(List.of(page("overview", blocks(9)))));
        assertThat(codes(validator.validate(manyBlocks))).contains("PAGE_BLOCK_LIMIT");

        DataAppSpec totalBlocks = validSpec();
        List<DataAppSpec.DataAppPage> pagesWithSeven = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pagesWithSeven.add(page("page-" + i, blocks(7)));
        }
        totalBlocks.setPages(pagesWithSeven);
        totalBlocks.setHomePageId("page-0");
        assertThat(codes(validator.validate(totalBlocks))).contains("APP_BLOCK_LIMIT");

        DataAppSpec manyColumns = validSpec();
        DataAppSpec.DataAppBlock columnBlock = recordsBlock();
        List<DataAppSpec.DataAppRecordColumn> columns = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            DataAppSpec.DataAppRecordColumn column = new DataAppSpec.DataAppRecordColumn();
            column.setField("event_type");
            columns.add(column);
        }
        columnBlock.getRecords().setColumns(columns);
        manyColumns.setPages(new ArrayList<>(List.of(page("overview", List.of(columnBlock)))));
        assertThat(codes(validator.validate(manyColumns))).contains("RECORD_COLUMN_LIMIT");

        DataAppSpec manyFilters = validSpec();
        DataAppSpec.DataAppBlock filterBlock = recordsBlock();
        List<DataAppSpec.DataAppRecordFilter> filters = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            DataAppSpec.DataAppRecordFilter filter = new DataAppSpec.DataAppRecordFilter();
            filter.setField("event_type");
            filter.setOperator("equal");
            filters.add(filter);
        }
        filterBlock.getRecords().setFilters(filters);
        manyFilters.setPages(new ArrayList<>(List.of(page("overview", List.of(filterBlock)))));
        assertThat(codes(validator.validate(manyFilters))).contains("RECORD_FILTER_LIMIT");

        DataAppSpec tooManyMetricCards = validSpec();
        DataAppSpec.DataAppBlock metricBlock = metricsBlock();
        List<DataAppSpec.DataAppMetricCard> cards = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            DataAppSpec.DataAppMetricCard card = new DataAppSpec.DataAppMetricCard();
            card.setLabel("指标" + i);
            card.setField("duration");
            cards.add(card);
        }
        metricBlock.setMetricCards(cards);
        tooManyMetricCards.setPages(new ArrayList<>(List.of(page("overview", List.of(metricBlock)))));
        assertThat(codes(validator.validate(tooManyMetricCards))).contains("METRIC_CARD_COUNT");
    }

    @Test
    void rejectsIllegalIdentifiersDuplicateIdsAndUnsupportedToolShapes() {
        DataAppSpec illegalIds = validSpec();
        illegalIds.setAppId("Bad App");
        illegalIds.getPages().get(0).setPageId("Bad Page");
        assertThat(codes(validator.validate(illegalIds)))
                .contains("APP_ID_INVALID", "PAGE_ID_INVALID");

        DataAppSpec duplicatePages = validSpec();
        duplicatePages.setPages(new ArrayList<>(List.of(
                page("overview", List.of(metricsBlock())),
                page("overview", List.of(metricsBlock())))));
        assertThat(codes(validator.validate(duplicatePages))).contains("PAGE_ID_DUPLICATE");

        DataAppSpec duplicateBlocks = validSpec();
        DataAppSpec.DataAppBlock first = metricsBlock();
        DataAppSpec.DataAppBlock second = metricsBlock();
        duplicateBlocks.setPages(new ArrayList<>(List.of(page("overview", List.of(first, second)))));
        assertThat(codes(validator.validate(duplicateBlocks))).contains("BLOCK_ID_DUPLICATE");

        DataAppSpec invalidHome = validSpec();
        invalidHome.setHomePageId("details");
        assertThat(codes(validator.validate(invalidHome))).contains("HOME_PAGE_INVALID");

        DataAppSpec badMetricTool = validSpec();
        DataAppSpec.DataAppBlock metric = metricsBlock();
        metric.getQuery().setTool("entity_trend");
        badMetricTool.setPages(new ArrayList<>(List.of(page("overview", List.of(metric)))));
        assertThat(codes(validator.validate(badMetricTool))).contains("METRIC_TOOL_UNSUPPORTED");

        DataAppSpec badChartHint = validSpec();
        DataAppSpec.DataAppBlock chart = chartBlock();
        chart.getQuery().getRequest().put("chart_hint", "RADAR");
        badChartHint.setPages(new ArrayList<>(List.of(page("overview", List.of(chart)))));
        assertThat(codes(validator.validate(badChartHint))).contains("CHART_HINT_UNSUPPORTED");
    }

    private DataAppSpec validSpec() {
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId("user-event-analysis");
        spec.setName("用户事件分析");
        spec.setDescription("分析用户事件");
        spec.setHomePageId("overview");
        spec.setPages(new ArrayList<>(List.of(page("overview", List.of(
                metricsBlock(), chartBlock(), recordsBlock())))));
        return spec;
    }

    private DataAppSpec.DataAppPage page(String id, List<DataAppSpec.DataAppBlock> blocks) {
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId(id);
        page.setTitle(id);
        page.setBlocks(new ArrayList<>(blocks));
        return page;
    }

    private List<DataAppSpec.DataAppBlock> blocks(int count) {
        List<DataAppSpec.DataAppBlock> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DataAppSpec.DataAppBlock block = metricsBlock();
            block.setBlockId("metrics-" + i);
            blocks.add(block);
        }
        return blocks;
    }

    private DataAppSpec.DataAppBlock metricsBlock() {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId("metrics");
        block.setType("metrics");
        block.setTitle("关键指标");
        DataAppSpec.DataAppQuery query = query("entity_overview", Map.of(
                "entity", "user_event", "time_range", Map.of("preset", "TODAY")));
        block.setQuery(query);
        DataAppSpec.DataAppMetricCard card = new DataAppSpec.DataAppMetricCard();
        card.setLabel("事件数");
        card.setField("duration");
        block.setMetricCards(new ArrayList<>(List.of(card)));
        return block;
    }

    private DataAppSpec.DataAppBlock chartBlock() {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId("trend");
        block.setType("chart");
        block.setTitle("趋势");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", "user_event");
        request.put("chart_hint", "LINE");
        request.put("time_range", Map.of("preset", "LAST_7_DAYS"));
        request.put("series", List.of(Map.of(
                "entity", "user_event",
                "label", "事件数",
                "metric", Map.of("operation", "COUNT"),
                "time_field", "server_time")));
        block.setQuery(query("entity_trend", request));
        return block;
    }

    private DataAppSpec.DataAppBlock recordsBlock() {
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId("records");
        block.setType("records");
        block.setTitle("事件明细");
        DataAppSpec.DataAppRecords records = new DataAppSpec.DataAppRecords();
        records.setEntity("user_event");
        DataAppSpec.DataAppRecordColumn column = new DataAppSpec.DataAppRecordColumn();
        column.setField("event_type");
        records.setColumns(new ArrayList<>(List.of(column)));
        DataAppSpec.DataAppRecordFilter filter = new DataAppSpec.DataAppRecordFilter();
        filter.setField("event_type");
        filter.setOperator("equal");
        records.setFilters(new ArrayList<>(List.of(filter)));
        records.setDefaultSortField("server_time");
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

    private DataAttribute attribute(String name, String column, String type, List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("user_event");
        attribute.setName(name);
        attribute.setLabel(name);
        attribute.setColumnName(column);
        attribute.setColumnType(type);
        attribute.setOperators(operators);
        return attribute;
    }

    private List<String> codes(DataAppValidator.Result result) {
        return result.diagnostics().stream().map(DataAppDiagnostic::getCode).toList();
    }
}
