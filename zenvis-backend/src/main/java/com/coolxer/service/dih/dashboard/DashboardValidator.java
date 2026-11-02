package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardSpec;
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
public class DashboardValidator {

    private static final Set<String> MODES = Set.of(
            DashboardSpec.MODE_HTML, DashboardSpec.MODE_LOW_CODE);
    private static final Set<String> METRIC_TOOLS = Set.of("entity_overview", "entity_summary");
    private static final Set<String> CHART_TOOLS = Set.of(
            "entity_trend", "entity_distribution", "entity_aggregate",
            "entity_histogram", "entity_scatter");
    private static final Set<String> CHART_HINTS = Set.of(
            "LINE", "BAR", "AREA", "PIE", "SCATTER", "BUBBLE", "HISTOGRAM");
    private static final Set<String> BLOCK_TYPES = Set.of("metrics", "chart", "records");
    private static final Set<String> TIME_PRESETS = Set.copyOf(DashboardSpec.TIME_PRESETS);

    private final MetaDataService metaDataService;

    public DashboardValidator(MetaDataService metaDataService) {
        this.metaDataService = metaDataService;
    }

    public Result validate(DashboardSpec input) {
        List<DashboardDiagnostic> diagnostics = new ArrayList<>();
        DashboardSpec spec = normalize(input, diagnostics);
        if (spec == null) {
            return new Result(null, diagnostics);
        }

        if (!DashboardSupport.validDashboardId(spec.getDashboardId())) {
            error(diagnostics, "DASHBOARD_ID_INVALID", "dashboard_id",
                    "dashboard_id 必须是 3-64 位小写字母、数字或短横线，并以字母开头");
        }
        if (StringUtils.isBlank(spec.getName())) {
            error(diagnostics, "DASHBOARD_NAME_REQUIRED", "name", "大屏名称不能为空");
        } else if (spec.getName().length() > 80) {
            error(diagnostics, "DASHBOARD_NAME_TOO_LONG", "name", "大屏名称不能超过 80 个字符");
        }
        if (spec.getDescription().length() > 500) {
            error(diagnostics, "DASHBOARD_DESCRIPTION_TOO_LONG", "description", "大屏说明不能超过 500 个字符");
        }
        if (!MODES.contains(spec.getMode())) {
            error(diagnostics, "DASHBOARD_MODE_UNSUPPORTED", "mode", "mode 仅支持 HTML_PAGE 或 LOW_CODE_PAGE");
        }
        if (!DashboardSpec.THEME_DARK_TECH.equals(spec.getTheme())) {
            error(diagnostics, "DASHBOARD_THEME_UNSUPPORTED", "theme", "首版仅支持 DARK_TECH 深色科技主题");
        }
        if (spec.getRefreshIntervalSeconds() == null
                || spec.getRefreshIntervalSeconds() < 30
                || spec.getRefreshIntervalSeconds() > 3600) {
            error(diagnostics, "REFRESH_INTERVAL_INVALID", "refresh_interval_seconds", "刷新间隔必须在 30-3600 秒之间");
        }
        if (StringUtils.isBlank(spec.getTimeField())) {
            error(diagnostics, "TIME_FIELD_REQUIRED", "time_field", "必须指定统一时间字段");
        }
        validateTimePresets(spec, diagnostics);

        List<DashboardSpec.DashboardBlock> blocks = spec.getBlocks() == null
                ? List.of() : spec.getBlocks();
        if (blocks.isEmpty() || blocks.size() > 16) {
            error(diagnostics, "BLOCK_COUNT_INVALID", "blocks", "大屏必须包含 1-16 个区块");
        }
        Set<String> blockIds = new HashSet<>();
        Map<String, DashboardSpec.DashboardBlock> blockMap = new LinkedHashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            DashboardSpec.DashboardBlock block = blocks.get(index);
            String path = "blocks[" + index + "]";
            if (!DashboardSupport.validSuffixIdentifier(block.getBlockId())) {
                error(diagnostics, "BLOCK_ID_INVALID", path + ".block_id", "block_id 必须是稳定的小写短横线标识");
            } else if (!blockIds.add(block.getBlockId())) {
                error(diagnostics, "BLOCK_ID_DUPLICATE", path + ".block_id", "block_id 不能重复");
            } else {
                blockMap.put(block.getBlockId(), block);
            }
            if (StringUtils.isBlank(block.getTitle())) {
                error(diagnostics, "BLOCK_TITLE_REQUIRED", path + ".title", "区块标题不能为空");
            }
            if (!BLOCK_TYPES.contains(block.getType())) {
                error(diagnostics, "BLOCK_TYPE_UNSUPPORTED", path + ".type", "区块仅支持 metrics、chart 或 records");
                continue;
            }
            switch (block.getType()) {
                case "metrics" -> validateMetrics(block, path, spec, diagnostics);
                case "chart" -> validateChart(block, path, spec, diagnostics);
                case "records" -> validateRecords(block, path, spec, diagnostics);
                default -> {
                }
            }
        }
        validateLayout(spec, blockMap, diagnostics);
        return new Result(spec, diagnostics);
    }

    private DashboardSpec normalize(DashboardSpec input, List<DashboardDiagnostic> diagnostics) {
        if (input == null) {
            error(diagnostics, "DASHBOARD_SPEC_REQUIRED", "spec", "大屏规格不能为空");
            return null;
        }
        input.setSchemaVersion(DashboardSpec.SCHEMA_VERSION);
        input.setDashboardId(DashboardSupport.normalizeDashboardId(input.getDashboardId()));
        input.setName(StringUtils.trimToEmpty(input.getName()));
        input.setDescription(StringUtils.trimToEmpty(input.getDescription()));
        input.setMode(StringUtils.defaultIfBlank(input.getMode(), DashboardSpec.MODE_HTML).trim().toUpperCase(Locale.ROOT));
        input.setTheme(StringUtils.defaultIfBlank(input.getTheme(), DashboardSpec.THEME_DARK_TECH).trim().toUpperCase(Locale.ROOT));
        if (input.getRefreshIntervalSeconds() == null) {
            input.setRefreshIntervalSeconds(60);
        }
        input.setTimeField(StringUtils.trimToEmpty(input.getTimeField()));
        input.setDefaultTimeRange(StringUtils.defaultIfBlank(input.getDefaultTimeRange(), "LAST_7_DAYS")
                .trim().toUpperCase(Locale.ROOT));
        if (input.getAllowedTimeRanges() == null) {
            input.setAllowedTimeRanges(new ArrayList<>(DashboardSpec.TIME_PRESETS));
        } else {
            input.setAllowedTimeRanges(input.getAllowedTimeRanges().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .toList());
        }
        if (input.getLayout() == null) {
            input.setLayout(new DashboardSpec.DashboardLayout());
        }
        if (input.getLayout().getColumns() == null) {
            input.getLayout().setColumns(12);
        }
        if (input.getLayout().getRows() == null) {
            input.getLayout().setRows(new ArrayList<>());
        }
        if (input.getBlocks() == null) {
            input.setBlocks(new ArrayList<>());
        }
        for (DashboardSpec.DashboardBlock block : input.getBlocks()) {
            if (block == null) {
                continue;
            }
            block.setBlockId(DashboardSupport.normalizeDashboardId(block.getBlockId()));
            block.setType(StringUtils.trimToEmpty(block.getType()).toLowerCase(Locale.ROOT));
            block.setTitle(StringUtils.trimToEmpty(block.getTitle()));
            block.setDescription(StringUtils.trimToEmpty(block.getDescription()));
            if (block.getMetricCards() == null) {
                block.setMetricCards(new ArrayList<>());
            }
            if (block.getQuery() != null) {
                block.getQuery().setTool(StringUtils.trimToEmpty(block.getQuery().getTool()).toLowerCase(Locale.ROOT));
                block.getQuery().setEntity(StringUtils.trimToEmpty(block.getQuery().getEntity()));
                if (block.getQuery().getRequest() == null) {
                    block.getQuery().setRequest(new LinkedHashMap<>());
                } else {
                    block.getQuery().setRequest(new LinkedHashMap<>(
                            VisualizationQueryNormalizer.normalizeRequest(block.getQuery().getRequest())));
                }
            }
            if (block.getRecords() != null) {
                if (block.getRecords().getColumns() == null) {
                    block.getRecords().setColumns(new ArrayList<>());
                }
                if (block.getRecords().getFilters() == null) {
                    block.getRecords().setFilters(new ArrayList<>());
                }
                if (block.getRecords().getPageSize() == null) {
                    block.getRecords().setPageSize(12);
                }
                block.getRecords().setDefaultSortDirection(StringUtils.defaultIfBlank(
                        block.getRecords().getDefaultSortDirection(), "desc").toLowerCase(Locale.ROOT));
            }
        }
        return input;
    }

    private void validateTimePresets(DashboardSpec spec, List<DashboardDiagnostic> diagnostics) {
        if (spec.getAllowedTimeRanges().isEmpty()) {
            error(diagnostics, "TIME_RANGES_EMPTY", "allowed_time_ranges", "至少开放一个时间范围");
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < spec.getAllowedTimeRanges().size(); index++) {
            String preset = spec.getAllowedTimeRanges().get(index);
            if (!TIME_PRESETS.contains(preset)) {
                error(diagnostics, "TIME_RANGE_UNSUPPORTED", "allowed_time_ranges[" + index + "]", "不支持的时间范围：" + preset);
            } else if (!unique.add(preset)) {
                error(diagnostics, "TIME_RANGE_DUPLICATE", "allowed_time_ranges[" + index + "]", "时间范围不能重复");
            }
        }
        if (!unique.contains(spec.getDefaultTimeRange())) {
            error(diagnostics, "DEFAULT_TIME_RANGE_INVALID", "default_time_range", "默认时间范围必须在 allowed_time_ranges 中");
        }
    }

    private void validateLayout(DashboardSpec spec,
                                Map<String, DashboardSpec.DashboardBlock> blockMap,
                                List<DashboardDiagnostic> diagnostics) {
        if (spec.getLayout().getColumns() == null || spec.getLayout().getColumns() != 12) {
            error(diagnostics, "LAYOUT_COLUMNS_INVALID", "layout.columns", "响应式布局必须使用 12 列网格");
        }
        List<DashboardSpec.DashboardRow> rows = spec.getLayout().getRows();
        if (rows.isEmpty() || rows.size() > 12) {
            error(diagnostics, "LAYOUT_ROW_COUNT_INVALID", "layout.rows", "布局必须包含 1-12 行");
        }
        Set<String> rowIds = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            DashboardSpec.DashboardRow row = rows.get(rowIndex);
            String path = "layout.rows[" + rowIndex + "]";
            if (row == null) {
                error(diagnostics, "LAYOUT_ROW_REQUIRED", path, "布局行不能为空");
                continue;
            }
            if (!DashboardSupport.validSuffixIdentifier(row.getRowId())) {
                error(diagnostics, "LAYOUT_ROW_ID_INVALID", path + ".row_id", "row_id 必须是稳定的小写短横线标识");
            } else if (!rowIds.add(row.getRowId())) {
                error(diagnostics, "LAYOUT_ROW_ID_DUPLICATE", path + ".row_id", "row_id 不能重复");
            }
            List<DashboardSpec.DashboardLayoutItem> items = row.getBlocks() == null ? List.of() : row.getBlocks();
            if (items.isEmpty()) {
                error(diagnostics, "LAYOUT_ROW_EMPTY", path + ".blocks", "布局行至少引用一个区块");
            }
            int total = 0;
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                DashboardSpec.DashboardLayoutItem item = items.get(itemIndex);
                String itemPath = path + ".blocks[" + itemIndex + "]";
                int span = item == null || item.getSpan() == null ? 0 : item.getSpan();
                if (span < 1 || span > 12) {
                    error(diagnostics, "LAYOUT_SPAN_INVALID", itemPath + ".span", "span 必须在 1-12 之间");
                }
                total += Math.max(0, span);
                String blockId = item == null ? null : item.getBlockId();
                if (!StringUtils.isNotBlank(blockId) || !blockMap.containsKey(blockId)) {
                    error(diagnostics, "LAYOUT_BLOCK_UNKNOWN", itemPath + ".block_id", "布局引用了不存在的 block_id");
                } else if (!referenced.add(blockId)) {
                    error(diagnostics, "LAYOUT_BLOCK_DUPLICATE", itemPath + ".block_id", "同一区块不能重复布局");
                }
            }
            if (total > 12) {
                error(diagnostics, "LAYOUT_ROW_OVERFLOW", path + ".blocks", "单行 span 合计不能超过 12，否则会产生横向滚动");
            }
        }
        for (String blockId : blockMap.keySet()) {
            if (!referenced.contains(blockId)) {
                error(diagnostics, "LAYOUT_BLOCK_MISSING", "layout.rows", "区块未纳入布局：" + blockId);
            }
        }
    }

    private void validateMetrics(DashboardSpec.DashboardBlock block,
                                 String path,
                                 DashboardSpec spec,
                                 List<DashboardDiagnostic> diagnostics) {
        DashboardSpec.DashboardQuery query = block.getQuery();
        if (query == null || !METRIC_TOOLS.contains(query.getTool())) {
            error(diagnostics, "METRIC_TOOL_UNSUPPORTED", path + ".query.tool", "指标区块仅支持 entity_overview 或 entity_summary");
            return;
        }
        EntityContext primary = validateQuery(query, path, spec, diagnostics);
        if (block.getMetricCards().isEmpty() || block.getMetricCards().size() > 8) {
            error(diagnostics, "METRIC_CARD_COUNT_INVALID", path + ".metric_cards", "指标区块必须包含 1-8 个指标卡");
        }
        Set<String> labels = new HashSet<>();
        for (int index = 0; index < block.getMetricCards().size(); index++) {
            DashboardSpec.DashboardMetricCard card = block.getMetricCards().get(index);
            String cardPath = path + ".metric_cards[" + index + "]";
            if (StringUtils.isBlank(card.getLabel())) {
                error(diagnostics, "METRIC_LABEL_REQUIRED", cardPath + ".label", "指标名称不能为空");
            } else if (!labels.add(card.getLabel())) {
                error(diagnostics, "METRIC_LABEL_DUPLICATE", cardPath + ".label", "指标名称不能重复");
            }
            if (StringUtils.isBlank(card.getField())) {
                error(diagnostics, "METRIC_FIELD_REQUIRED", cardPath + ".field", "指标取值字段不能为空");
            } else if ("entity_summary".equals(query.getTool()) && primary != null) {
                checkSummaryMetric(primary, query.getRequest(), card.getField(),
                        cardPath + ".field", diagnostics);
            }
            if (card.getPrecision() != null && (card.getPrecision() < 0 || card.getPrecision() > 4)) {
                error(diagnostics, "METRIC_PRECISION_INVALID", cardPath + ".precision", "指标精度必须在 0-4 之间");
            }
        }
    }

    private void validateChart(DashboardSpec.DashboardBlock block,
                               String path,
                               DashboardSpec spec,
                               List<DashboardDiagnostic> diagnostics) {
        DashboardSpec.DashboardQuery query = block.getQuery();
        if (query == null || !CHART_TOOLS.contains(query.getTool())) {
            error(diagnostics, "CHART_TOOL_UNSUPPORTED", path + ".query.tool", "图表区块仅支持 trend、distribution、aggregate、histogram 或 scatter");
            return;
        }
        validateQuery(query, path, spec, diagnostics);
        Object hint = query.getRequest().get("chart_hint");
        if (hint != null && !CHART_HINTS.contains(hint.toString().toUpperCase(Locale.ROOT))) {
            error(diagnostics, "CHART_HINT_UNSUPPORTED", path + ".query.request.chart_hint", "chart_hint 不在受支持范围内");
        }
    }

    private void validateRecords(DashboardSpec.DashboardBlock block,
                                 String path,
                                 DashboardSpec spec,
                                 List<DashboardDiagnostic> diagnostics) {
        DashboardSpec.DashboardRecords records = block.getRecords();
        if (records == null || StringUtils.isBlank(records.getEntity())) {
            error(diagnostics, "RECORDS_ENTITY_REQUIRED", path + ".records.entity", "明细区块必须指定实体");
            return;
        }
        EntityContext entity = entity(records.getEntity(), path, diagnostics);
        if (entity == null) {
            return;
        }
        checkTimeField(entity, spec, path + ".records", diagnostics);
        if (records.getColumns().isEmpty() || records.getColumns().size() > 10) {
            error(diagnostics, "RECORD_COLUMN_COUNT_INVALID", path + ".records.columns", "明细表必须包含 1-10 列");
        }
        if (records.getFilters().size() > 6) {
            error(diagnostics, "RECORD_FILTER_LIMIT", path + ".records.filters", "明细表最多 6 个筛选字段");
        }
        if (records.getPageSize() < 5 || records.getPageSize() > 50) {
            error(diagnostics, "RECORD_PAGE_SIZE_INVALID", path + ".records.page_size", "明细表每页数量必须在 5-50 之间");
        }
        Set<String> fields = new HashSet<>();
        for (int index = 0; index < records.getColumns().size(); index++) {
            DashboardSpec.DashboardRecordColumn column = records.getColumns().get(index);
            String columnPath = path + ".records.columns[" + index + "]";
            if (!entity.attributes.containsKey(column.getField())) {
                error(diagnostics, "RECORD_FIELD_UNKNOWN", columnPath + ".field", "字段未在已发布 Meta 中定义");
            } else if (!fields.add(column.getField())) {
                error(diagnostics, "RECORD_FIELD_DUPLICATE", columnPath + ".field", "明细字段不能重复");
            }
        }
        for (int index = 0; index < records.getFilters().size(); index++) {
            DashboardSpec.DashboardRecordFilter filter = records.getFilters().get(index);
            String filterPath = path + ".records.filters[" + index + "]";
            DataAttribute attribute = entity.attributes.get(filter.getField());
            if (attribute == null) {
                error(diagnostics, "FILTER_FIELD_UNKNOWN", filterPath + ".field", "筛选字段未在已发布 Meta 中定义");
            } else if (attribute.getOperators() == null
                    || !attribute.getOperators().contains(filter.getOperator())) {
                error(diagnostics, "FILTER_OPERATOR_UNSUPPORTED", filterPath + ".operator", "字段不支持操作符 " + filter.getOperator());
            }
        }
        if (StringUtils.isNotBlank(records.getDefaultSortField())
                && !entity.attributes.containsKey(records.getDefaultSortField())) {
            error(diagnostics, "SORT_FIELD_UNKNOWN", path + ".records.default_sort_field", "排序字段未在已发布 Meta 中定义");
        }
        if (!Set.of("asc", "desc").contains(records.getDefaultSortDirection())) {
            error(diagnostics, "SORT_DIRECTION_INVALID", path + ".records.default_sort_direction", "排序方向仅支持 asc 或 desc");
        }
    }

    private EntityContext validateQuery(DashboardSpec.DashboardQuery query,
                                        String path,
                                        DashboardSpec spec,
                                        List<DashboardDiagnostic> diagnostics) {
        EntityContext primary = entity(query.getEntity(), path + ".query.entity", diagnostics);
        if (primary != null) {
            checkTimeField(primary, spec, path + ".query", diagnostics);
        }
        String queryError = VisualizationQueryValidator.validate(query.getTool(), query.getRequest());
        if (StringUtils.isNotBlank(queryError)) {
            error(diagnostics, "QUERY_INVALID", path + ".query", queryError);
        }
        validateRequestFields(query, path, spec, diagnostics);
        return primary;
    }

    private void validateRequestFields(DashboardSpec.DashboardQuery query,
                                       String path,
                                       DashboardSpec spec,
                                       List<DashboardDiagnostic> diagnostics) {
        Map<String, Object> request = query.getRequest();
        Set<String> entities = new HashSet<>();
        if (StringUtils.isNotBlank(query.getEntity())) {
            entities.add(query.getEntity());
        }
        for (Object value : list(request.get("entities"))) {
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                entities.add(value.toString().trim());
            }
        }
        for (Object raw : list(request.get("series"))) {
            Map<?, ?> series = map(raw);
            if (StringUtils.isNotBlank(text(series.get("entity")))) {
                entities.add(text(series.get("entity")));
            }
        }
        for (Object raw : list(request.get("mappings"))) {
            Map<?, ?> mapping = map(raw);
            if (StringUtils.isNotBlank(text(mapping.get("entity")))) {
                entities.add(text(mapping.get("entity")));
            }
        }
        for (String entityName : entities) {
            EntityContext entity = entity(entityName, path + ".query.entity", diagnostics);
            if (entity != null) {
                checkTimeField(entity, spec, path + ".query", diagnostics);
            }
        }
        EntityContext primary = entity(query.getEntity(), path + ".query.entity", diagnostics);
        if (primary == null) {
            return;
        }
        for (String fieldName : List.of("time_field", "dimension", "field", "x_field", "y_field",
                "size_field", "category_field", "sort_by")) {
            checkField(primary, text(request.get(fieldName)), path + ".query.request." + fieldName, diagnostics);
        }
        for (Object raw : list(request.get("dimensions"))) {
            Map<?, ?> dimension = map(raw);
            checkField(primary, text(dimension.get("field")), path + ".query.request.dimensions", diagnostics);
            if ("TIME".equalsIgnoreCase(text(dimension.get("kind")))) {
                checkTimeField(primary, spec, path + ".query.request.dimensions", diagnostics);
            }
        }
        for (Object raw : list(request.get("metrics"))) {
            Map<?, ?> metric = map(raw);
            checkField(primary, text(metric.get("field")), path + ".query.request.metrics", diagnostics);
        }
        for (Object raw : list(request.get("series"))) {
            Map<?, ?> series = map(raw);
            checkField(primary, text(series.get("time_field")), path + ".query.request.series.time_field", diagnostics);
            Map<?, ?> metric = map(series.get("metric"));
            checkField(primary, text(metric.get("field")), path + ".query.request.series.metric", diagnostics);
        }
    }

    private EntityContext entity(String name,
                                 String path,
                                 List<DashboardDiagnostic> diagnostics) {
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

    private void checkTimeField(EntityContext entity,
                                DashboardSpec spec,
                                String path,
                                List<DashboardDiagnostic> diagnostics) {
        if (entity != null && StringUtils.isNotBlank(spec.getTimeField())
                && !entity.attributes.containsKey(spec.getTimeField())) {
            error(diagnostics, "TIME_FIELD_UNKNOWN", path + ".time_field",
                    "统一时间字段 " + spec.getTimeField() + " 未在实体 " + entity.entity.getName() + " 的已发布 Meta 中定义");
        }
    }

    private void checkField(EntityContext entity,
                            String field,
                            String path,
                            List<DashboardDiagnostic> diagnostics) {
        if (StringUtils.isNotBlank(field) && !entity.attributes.containsKey(field)) {
            error(diagnostics, "FIELD_UNKNOWN", path, "字段未在已发布 Meta 中定义：" + field);
        }
    }

    private void checkSummaryMetric(EntityContext entity,
                                    Map<String, Object> request,
                                    String metricName,
                                    String path,
                                    List<DashboardDiagnostic> diagnostics) {
        if (entity.attributes.containsKey(metricName)
                || metricOutputNameExists(request, metricName)) {
            return;
        }
        error(diagnostics, "METRIC_UNKNOWN", path,
                "汇总指标必须引用已发布字段或查询中声明的指标名称：" + metricName);
    }

    private boolean metricOutputNameExists(Map<String, Object> request, String metricName) {
        for (Object raw : list(request.get("metrics"))) {
            Map<?, ?> metric = map(raw);
            for (String key : List.of("name", "label", "alias", "output")) {
                if (metricName.equals(text(metric.get(key)))) {
                    return true;
                }
            }
        }
        return false;
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

    private void error(List<DashboardDiagnostic> diagnostics,
                       String code,
                       String path,
                       String message) {
        diagnostics.add(new DashboardDiagnostic(code, path, message, "error"));
    }

    public record Result(DashboardSpec spec, List<DashboardDiagnostic> diagnostics) {
        public boolean passed() {
            return spec != null && diagnostics.stream()
                    .noneMatch(item -> "error".equals(item.getSeverity()));
        }
    }

    private record EntityContext(DataEntity entity, Map<String, DataAttribute> attributes) {
    }
}
