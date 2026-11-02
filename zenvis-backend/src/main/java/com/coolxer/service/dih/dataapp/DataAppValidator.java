package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.dih.workflow.VisualizationQueryNormalizer;
import com.coolxer.service.dih.workflow.VisualizationQueryValidator;
import com.coolxer.service.retrieval.MetaDataService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DataAppValidator {

    private static final Set<String> METRIC_TOOLS = Set.of("entity_overview", "entity_summary");
    private static final Set<String> CHART_TOOLS = Set.of(
            "entity_trend", "entity_distribution", "entity_aggregate",
            "entity_histogram", "entity_scatter");
    private static final Set<String> CHART_HINTS = Set.of(
            "LINE", "BAR", "AREA", "PIE", "SCATTER", "BUBBLE", "HISTOGRAM");
    private static final Set<String> WRITE_UNSUPPORTED_TYPES = Set.of("JSON", "Array");

    private final MetaDataService metaDataService;

    public DataAppValidator(MetaDataService metaDataService) {
        this.metaDataService = metaDataService;
    }

    public Result validate(DataAppSpec input) {
        List<DataAppDiagnostic> diagnostics = new ArrayList<>();
        DataAppSpec spec = normalize(input, diagnostics);
        if (spec == null) {
            return new Result(null, diagnostics);
        }

        if (!DataAppSupport.validAppId(spec.getAppId())) {
            error(diagnostics, "APP_ID_INVALID", "app_id", "app_id 必须是 3-64 位小写字母、数字或短横线，并以字母开头");
        }
        if (StringUtils.isBlank(spec.getName())) {
            error(diagnostics, "APP_NAME_REQUIRED", "name", "应用名称不能为空");
        } else if (spec.getName().length() > 80) {
            error(diagnostics, "APP_NAME_TOO_LONG", "name", "应用名称不能超过 80 个字符");
        }
        List<DataAppSpec.DataAppPage> pages = spec.getPages() == null ? List.of() : spec.getPages();
        if (pages.isEmpty() || pages.size() > 6) {
            error(diagnostics, "PAGE_COUNT_INVALID", "pages", "应用必须包含 1-6 个页面");
        }
        Set<String> pageIds = new HashSet<>();
        int totalBlocks = 0;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            DataAppSpec.DataAppPage page = pages.get(pageIndex);
            String pagePath = "pages[" + pageIndex + "]";
            if (!DataAppSupport.validPageId(page.getPageId())) {
                error(diagnostics, "PAGE_ID_INVALID", pagePath + ".page_id", "page_id 必须是稳定的小写短横线标识");
            } else if (!pageIds.add(page.getPageId())) {
                error(diagnostics, "PAGE_ID_DUPLICATE", pagePath + ".page_id", "page_id 不能重复");
            }
            if (StringUtils.isBlank(page.getTitle())) {
                error(diagnostics, "PAGE_TITLE_REQUIRED", pagePath + ".title", "页面标题不能为空");
            }
            List<DataAppSpec.DataAppBlock> blocks = page.getBlocks() == null ? List.of() : page.getBlocks();
            if (blocks.size() > 8) {
                error(diagnostics, "PAGE_BLOCK_LIMIT", pagePath + ".blocks", "每页最多 8 个区块");
            }
            totalBlocks += blocks.size();
            Set<String> blockIds = new HashSet<>();
            for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                validateBlock(blocks.get(blockIndex), pagePath + ".blocks[" + blockIndex + "]",
                        blockIds, diagnostics);
            }
        }
        if (totalBlocks > 24) {
            error(diagnostics, "APP_BLOCK_LIMIT", "pages", "应用总计最多 24 个区块");
        }
        if (!pageIds.contains(spec.getHomePageId())) {
            error(diagnostics, "HOME_PAGE_INVALID", "home_page_id", "home_page_id 必须指向当前应用页面");
        }
        if (spec.getMenu() != null && StringUtils.isBlank(spec.getMenu().getName())) {
            spec.getMenu().setName(spec.getName());
        }
        return new Result(spec, diagnostics);
    }

    private DataAppSpec normalize(DataAppSpec input, List<DataAppDiagnostic> diagnostics) {
        if (input == null) {
            error(diagnostics, "APP_SPEC_REQUIRED", "spec", "应用规格不能为空");
            return null;
        }
        input.setSchemaVersion(DataAppSpec.SCHEMA_VERSION);
        input.setAppId(DataAppSupport.normalizeAppId(input.getAppId()));
        input.setName(StringUtils.trimToEmpty(input.getName()));
        input.setDescription(StringUtils.trimToEmpty(input.getDescription()));
        input.setHomePageId(DataAppSupport.normalizePageId(input.getHomePageId()));
        if (input.getPages() == null) {
            input.setPages(new ArrayList<>());
        }
        for (DataAppSpec.DataAppPage page : input.getPages()) {
            page.setPageId(DataAppSupport.normalizePageId(page.getPageId()));
            page.setTitle(StringUtils.trimToEmpty(page.getTitle()));
            page.setIcon(StringUtils.defaultIfBlank(page.getIcon(), "fa-solid fa-chart-line"));
            if (page.getBlocks() == null) {
                page.setBlocks(new ArrayList<>());
            }
            for (DataAppSpec.DataAppBlock block : page.getBlocks()) {
                block.setBlockId(StringUtils.defaultIfBlank(block.getBlockId(), "block-" + (page.getBlocks().indexOf(block) + 1)));
                block.setType(StringUtils.lowerCase(StringUtils.trimToEmpty(block.getType()), Locale.ROOT));
                block.setTitle(StringUtils.trimToEmpty(block.getTitle()));
                if (block.getWidth() == null || block.getWidth() < 3 || block.getWidth() > 12) {
                    block.setWidth(12);
                }
                normalizeQuery(block.getQuery());
                normalizeRecords(block.getRecords());
            }
        }
        return input;
    }

    private void normalizeQuery(DataAppSpec.DataAppQuery query) {
        if (query == null) {
            return;
        }
        query.setTool(StringUtils.lowerCase(StringUtils.trimToEmpty(query.getTool()), Locale.ROOT));
        query.setEntity(StringUtils.trimToEmpty(query.getEntity()));
        if (query.getRequest() == null) {
            query.setRequest(new LinkedHashMap<>());
        }
        query.setRequest(VisualizationQueryNormalizer.normalizeRequest(query.getRequest()));
        if (StringUtils.isNotBlank(query.getEntity()) && !query.getRequest().containsKey("entity")) {
            query.getRequest().put("entity", query.getEntity());
        }
    }

    private void normalizeRecords(DataAppSpec.DataAppRecords records) {
        if (records == null) {
            return;
        }
        records.setEntity(StringUtils.trimToEmpty(records.getEntity()));
        if (records.getColumns() == null) {
            records.setColumns(new ArrayList<>());
        }
        if (records.getFilters() == null) {
            records.setFilters(new ArrayList<>());
        }
        records.setPageSize(records.getPageSize() == null ? 20 : Math.max(5, Math.min(50, records.getPageSize())));
        records.setDefaultSortDirection("asc".equalsIgnoreCase(records.getDefaultSortDirection()) ? "asc" : "desc");
        for (DataAppSpec.DataAppRecordFilter filter : records.getFilters()) {
            filter.setOperator(StringUtils.defaultIfBlank(filter.getOperator(), "equal"));
            filter.setInputType(StringUtils.defaultIfBlank(filter.getInputType(), "input-text"));
        }
    }

    private void validateBlock(DataAppSpec.DataAppBlock block,
                               String path,
                               Set<String> blockIds,
                               List<DataAppDiagnostic> diagnostics) {
        if (StringUtils.isBlank(block.getBlockId())) {
            error(diagnostics, "BLOCK_ID_REQUIRED", path + ".block_id", "区块 id 不能为空");
        } else if (!blockIds.add(block.getBlockId())) {
            error(diagnostics, "BLOCK_ID_DUPLICATE", path + ".block_id", "同一页面内区块 id 不能重复");
        }
        if (StringUtils.isBlank(block.getTitle())) {
            error(diagnostics, "BLOCK_TITLE_REQUIRED", path + ".title", "区块标题不能为空");
        }
        switch (block.getType()) {
            case "metrics" -> validateMetrics(block, path, diagnostics);
            case "chart" -> validateChart(block, path, diagnostics);
            case "records" -> validateRecords(block, path, diagnostics);
            default -> error(diagnostics, "BLOCK_TYPE_UNSUPPORTED", path + ".type",
                    "仅支持 metrics、chart 或 records 区块");
        }
    }

    private void validateMetrics(DataAppSpec.DataAppBlock block,
                                 String path,
                                 List<DataAppDiagnostic> diagnostics) {
        DataAppSpec.DataAppQuery query = block.getQuery();
        if (query == null || !METRIC_TOOLS.contains(query.getTool())) {
            error(diagnostics, "METRIC_TOOL_UNSUPPORTED", path + ".query.tool",
                    "指标区块仅支持 entity_overview 或 entity_summary");
            return;
        }
        validateEntity(query.getEntity(), path, diagnostics);
        validateQuery(path, query, diagnostics);
        List<DataAppSpec.DataAppMetricCard> cards = block.getMetricCards() == null
                ? List.of() : block.getMetricCards();
        if (cards.isEmpty() || cards.size() > 8) {
            error(diagnostics, "METRIC_CARD_COUNT", path + ".metric_cards", "指标卡数量必须为 1-8");
        }
        for (int i = 0; i < cards.size(); i++) {
            DataAppSpec.DataAppMetricCard card = cards.get(i);
            if (StringUtils.isBlank(card.getLabel()) || StringUtils.isBlank(card.getField())) {
                error(diagnostics, "METRIC_CARD_INVALID", path + ".metric_cards[" + i + "]",
                        "指标卡 label 和 field 不能为空");
            }
        }
    }

    private void validateChart(DataAppSpec.DataAppBlock block,
                               String path,
                               List<DataAppDiagnostic> diagnostics) {
        DataAppSpec.DataAppQuery query = block.getQuery();
        if (query == null || !CHART_TOOLS.contains(query.getTool())) {
            error(diagnostics, "CHART_TOOL_UNSUPPORTED", path + ".query.tool",
                    "图表区块仅支持 trend、distribution、aggregate、histogram 或 scatter");
            return;
        }
        validateEntity(query.getEntity(), path, diagnostics);
        validateQuery(path, query, diagnostics);
        Object hint = query.getRequest().get("chart_hint");
        if (hint != null && !CHART_HINTS.contains(hint.toString().toUpperCase(Locale.ROOT))) {
            error(diagnostics, "CHART_HINT_UNSUPPORTED", path + ".query.request.chart_hint",
                    "chart_hint 不在受支持范围内");
        }
    }

    private void validateRecords(DataAppSpec.DataAppBlock block,
                                 String path,
                                 List<DataAppDiagnostic> diagnostics) {
        DataAppSpec.DataAppRecords records = block.getRecords();
        if (records == null || StringUtils.isBlank(records.getEntity())) {
            error(diagnostics, "RECORDS_ENTITY_REQUIRED", path + ".records.entity", "列表区块必须指定实体");
            return;
        }
        EntityContext entity = validateEntity(records.getEntity(), path, diagnostics);
        if (entity == null) {
            return;
        }
        if (records.getColumns().size() > 20) {
            error(diagnostics, "RECORD_COLUMN_LIMIT", path + ".records.columns", "列表最多 20 列");
        }
        if (records.getFilters().size() > 8) {
            error(diagnostics, "RECORD_FILTER_LIMIT", path + ".records.filters", "列表最多 8 个筛选字段");
        }
        if (records.getColumns().isEmpty()) {
            error(diagnostics, "RECORD_COLUMN_REQUIRED", path + ".records.columns", "列表至少需要一列");
        }
        Set<String> columnFields = new HashSet<>();
        for (int i = 0; i < records.getColumns().size(); i++) {
            DataAppSpec.DataAppRecordColumn column = records.getColumns().get(i);
            String columnPath = path + ".records.columns[" + i + "]";
            if (!entity.attributes.containsKey(column.getField())) {
                error(diagnostics, "RECORD_FIELD_UNKNOWN", columnPath + ".field", "字段未在已发布 Meta 中定义");
            } else if (!columnFields.add(column.getField())) {
                error(diagnostics, "RECORD_FIELD_DUPLICATE", columnPath + ".field", "列表字段不能重复");
            }
        }
        for (int i = 0; i < records.getFilters().size(); i++) {
            DataAppSpec.DataAppRecordFilter filter = records.getFilters().get(i);
            String filterPath = path + ".records.filters[" + i + "]";
            DataAttribute attribute = entity.attributes.get(filter.getField());
            if (attribute == null) {
                error(diagnostics, "FILTER_FIELD_UNKNOWN", filterPath + ".field", "筛选字段未在已发布 Meta 中定义");
                continue;
            }
            List<String> operators = attribute.getOperators() == null ? List.of() : attribute.getOperators();
            if (!operators.contains(filter.getOperator())) {
                error(diagnostics, "FILTER_OPERATOR_UNSUPPORTED", filterPath + ".operator",
                        "字段不支持操作符 " + filter.getOperator());
            }
        }
        if (records.getDefaultSortField() != null
                && !entity.attributes.containsKey(records.getDefaultSortField())) {
            error(diagnostics, "SORT_FIELD_UNKNOWN", path + ".records.default_sort_field", "排序字段未在已发布 Meta 中定义");
        }
        if (Boolean.TRUE.equals(records.getCreate()) || Boolean.TRUE.equals(records.getUpdate())) {
            for (DataAttribute attribute : entity.attributes.values()) {
                if (isWriteUnsupported(attribute) && Boolean.TRUE.equals(records.getCreate())) {
                    // These fields can still be displayed. They are simply omitted from write forms.
                }
            }
        }
    }

    private void validateQuery(String path,
                               DataAppSpec.DataAppQuery query,
                               List<DataAppDiagnostic> diagnostics) {
        String error = VisualizationQueryValidator.validate(query.getTool(), query.getRequest());
        if (StringUtils.isNotBlank(error)) {
            error(diagnostics, "QUERY_INVALID", path + ".query", error);
        }
        validateFields(query, path, diagnostics);
    }

    private void validateFields(DataAppSpec.DataAppQuery query,
                                String path,
                                List<DataAppDiagnostic> diagnostics) {
        EntityContext entity = validateEntity(query.getEntity(), path, diagnostics);
        if (entity == null) {
            return;
        }
        Map<String, Object> request = query.getRequest();
        checkField(entity, text(request.get("time_field")), path + ".query.request.time_field", diagnostics);
        checkField(entity, text(request.get("dimension")), path + ".query.request.dimension", diagnostics);
        checkField(entity, text(request.get("field")), path + ".query.request.field", diagnostics);
        checkField(entity, text(request.get("x_field")), path + ".query.request.x_field", diagnostics);
        checkField(entity, text(request.get("y_field")), path + ".query.request.y_field", diagnostics);
        checkField(entity, text(request.get("size_field")), path + ".query.request.size_field", diagnostics);
        checkField(entity, text(request.get("category_field")), path + ".query.request.category_field", diagnostics);
        for (Object raw : list(request.get("dimensions"))) {
            if (raw instanceof Map<?, ?> map) {
                checkField(entity, text(map.get("field")), path + ".query.request.dimensions", diagnostics);
            }
        }
        List<?> metrics = list(request.get("metrics"));
        if (metrics.size() > 8) {
            error(diagnostics, "QUERY_METRIC_LIMIT", path + ".query.request.metrics", "指标最多 8 个");
        }
        for (Object raw : metrics) {
            if (raw instanceof Map<?, ?> map) {
                checkField(entity, text(map.get("field")), path + ".query.request.metrics", diagnostics);
            }
        }
        Object series = request.get("series");
        for (Object raw : list(series)) {
            if (raw instanceof Map<?, ?> map) {
                checkField(entity, text(map.get("time_field")), path + ".query.request.series", diagnostics);
                Object metric = map.get("metric");
                if (metric instanceof Map<?, ?> metricMap) {
                    checkField(entity, text(metricMap.get("field")), path + ".query.request.series.metric", diagnostics);
                }
            }
        }
    }

    private EntityContext validateEntity(String name,
                                         String path,
                                         List<DataAppDiagnostic> diagnostics) {
        if (StringUtils.isBlank(name)) {
            error(diagnostics, "ENTITY_REQUIRED", path, "实体不能为空");
            return null;
        }
        DataEntity entity = metaDataService.getDataEntityByName(name);
        if (entity == null) {
            error(diagnostics, "ENTITY_UNKNOWN", path, "实体未在已发布 Meta 中定义：" + name);
            return null;
        }
        Map<String, DataAttribute> attributes = new HashMap<>();
        for (DataAttribute attribute : metaDataService.getAllDataAttributeByEntity(entity)) {
            attributes.put(attribute.getName(), attribute);
        }
        return new EntityContext(entity, attributes);
    }

    private void checkField(EntityContext entity,
                            String field,
                            String path,
                            List<DataAppDiagnostic> diagnostics) {
        if (StringUtils.isNotBlank(field) && !entity.attributes.containsKey(field)) {
            error(diagnostics, "FIELD_UNKNOWN", path, "字段未在已发布 Meta 中定义：" + field);
        }
    }

    private boolean isWriteUnsupported(DataAttribute attribute) {
        String type = StringUtils.defaultString(attribute.getColumnType());
        return WRITE_UNSUPPORTED_TYPES.stream().anyMatch(type::startsWith);
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void error(List<DataAppDiagnostic> diagnostics, String code, String path, String message) {
        diagnostics.add(new DataAppDiagnostic(code, path, message, "error"));
    }

    public boolean isWriteUnsupportedForForm(DataAttribute attribute) {
        return isWriteUnsupported(attribute) || MetaDataSystemFields.isSystem(attribute);
    }

    public record Result(DataAppSpec spec, List<DataAppDiagnostic> diagnostics) {
        public boolean passed() {
            return spec != null && diagnostics.stream().noneMatch(item -> "error".equals(item.getSeverity()));
        }
    }

    private record EntityContext(DataEntity entity, Map<String, DataAttribute> attributes) {
    }

    private static final class MetaDataSystemFields {
        private static boolean isSystem(DataAttribute attribute) {
            return attribute.getColumnName() != null
                    && (attribute.getColumnName().equals("zenvis_id")
                    || attribute.getColumnName().equals("zenvis_insert_time")
                    || attribute.getColumnName().equals("zenvis_update_time"));
        }
    }
}
