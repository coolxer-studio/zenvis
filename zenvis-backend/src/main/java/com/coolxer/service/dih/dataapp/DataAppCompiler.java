package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic AMIS 6.7 compiler. All API paths and adaptors are owned by the
 * platform; model input is limited to DataAppSpec.
 */
@Service
public class DataAppCompiler {

    private static final String API_PREFIX = "/zenvis/api/v1";
    private static final String CHART_ADAPTOR =
            "var d=payload&&payload.data?payload.data:{};var o=d.echarts&&d.echarts.option?d.echarts.option:{};"
                    + "return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},"
                    + "chart_series:o.series||[]}};";
    private static final String RECORD_RESPONSE_ADAPTOR =
            "var d=payload&&payload.data?payload.data:(payload||{});var rows=d.datalist||d.rows||[];"
                    + "return {status:0,msg:'',data:{items:rows,total:Number(d.total||rows.length||0)}};";

    private final MetaDataService metaDataService;

    public DataAppCompiler(MetaDataService metaDataService) {
        this.metaDataService = metaDataService;
    }

    public CompiledFiles compile(DataAppSpec spec, boolean singlePage, boolean preview) {
        return compile(spec, singlePage, preview, null);
    }

    public CompiledFiles compile(DataAppSpec spec,
                                 boolean singlePage,
                                 boolean preview,
                                 String previewFilesApi) {
        Map<String, String> files = new LinkedHashMap<>();
        if (singlePage) {
            files.put("index.json", JacksonUtil.toJson(page(spec, spec.getPages().get(0), preview)));
        } else {
            for (DataAppSpec.DataAppPage page : spec.getPages()) {
                files.put("pages/" + page.getPageId() + ".json",
                        JacksonUtil.toJson(page(spec, page, preview)));
            }
        }
        if (!singlePage || preview) {
            files.put("site.json", JacksonUtil.toJson(site(spec, singlePage, previewFilesApi)));
        }
        return new CompiledFiles(files, singlePage ? "LOW_CODE_PAGE" : "LOW_CODE_APP");
    }

    private Map<String, Object> site(DataAppSpec spec,
                                     boolean singlePage,
                                     String previewFilesApi) {
        List<Map<String, Object>> pages = new ArrayList<>();
        if (singlePage) {
            pages.add(pageLink(spec, spec.getPages().get(0), "index.json", previewFilesApi));
        } else {
            for (DataAppSpec.DataAppPage page : spec.getPages()) {
                pages.add(pageLink(spec, page,
                        "pages/" + page.getPageId() + ".json", previewFilesApi));
            }
        }
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("label", "Home");
        home.put("url", "/");
        home.put("redirect", "/" + spec.getHomePageId());
        return Map.of(
                "status", 0,
                "msg", "",
                "data", Map.of("pages", List.of(home, Map.of(
                        "label", spec.getName(),
                        "children", pages)))
        );
    }

    private Map<String, Object> pageLink(DataAppSpec spec,
                                         DataAppSpec.DataAppPage page,
                                         String fileName,
                                         String previewFilesApi) {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("label", page.getTitle());
        link.put("url", page.getPageId());
        link.put("icon", page.getIcon());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(previewFilesApi)) {
            String separator = previewFilesApi.contains("?") ? "&" : "?";
            link.put("schemaApi", "get:" + previewFilesApi + separator + "path=" + encoded);
        } else {
            link.put("schemaApi", "get:" + API_PREFIX + "/config/" + spec.getAppId()
                    + "/get?file_name=" + encoded);
        }
        return link;
    }

    private Map<String, Object> page(DataAppSpec spec,
                                     DataAppSpec.DataAppPage page,
                                     boolean preview) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (DataAppSpec.DataAppBlock block : page.getBlocks()) {
            body.add(block(spec, block, preview));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "page");
        result.put("title", page.getTitle());
        result.put("body", body);
        return result;
    }

    private Map<String, Object> block(DataAppSpec spec,
                                      DataAppSpec.DataAppBlock block,
                                      boolean preview) {
        return switch (block.getType()) {
            case "metrics" -> metricsBlock(block);
            case "chart" -> chartBlock(block);
            case "records" -> recordsBlock(block, preview);
            default -> throw new IllegalArgumentException("不支持的区块类型：" + block.getType());
        };
    }

    private Map<String, Object> metricsBlock(DataAppSpec.DataAppBlock block) {
        String tool = block.getQuery().getTool();
        Map<String, Object> request = new LinkedHashMap<>(block.getQuery().getRequest());
        String url = API_PREFIX + ("entity_overview".equals(tool)
                ? "/entity/overview/query" : "/entity/summary/query");
        List<Map<String, Object>> cards = new ArrayList<>();
        for (DataAppSpec.DataAppMetricCard card : block.getMetricCards()) {
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("md", Math.max(3, Math.min(12, 12 / Math.max(1, block.getMetricCards().size()))));
            column.put("body", Map.of(
                    "type", "panel",
                    "title", StringUtils.defaultString(card.getLabel()),
                    "body", "<div style='font-size:30px;font-weight:700;color:#2468f2'>"
                            + valueExpression(card) + "</div>"));
            cards.add(column);
        }
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", url);
        api.put("data", request);
        api.put("adaptor", metricsAdaptor(block.getMetricCards()));
        return Map.of(
                "type", "panel",
                "title", StringUtils.defaultString(block.getTitle()),
                "body", Map.of(
                        "type", "service",
                        "api", api,
                        "body", Map.of("type", "grid", "columns", cards)));
    }

    private String valueExpression(DataAppSpec.DataAppMetricCard card) {
        String field = safeJsName(card.getField());
        String expression = "${" + field + "}";
        if (StringUtils.isNotBlank(card.getPrefix())) {
            expression = card.getPrefix() + expression;
        }
        if (StringUtils.isNotBlank(card.getSuffix())) {
            expression = expression + card.getSuffix();
        }
        return expression;
    }

    private String metricsAdaptor(List<DataAppSpec.DataAppMetricCard> cards) {
        StringBuilder script = new StringBuilder(
                "var d=payload&&payload.data?payload.data:{};var r=d.result&&d.result.rows&&d.result.rows[0]?d.result.rows[0]:{};"
                        + "var out={};");
        for (DataAppSpec.DataAppMetricCard card : cards) {
            String field = safeJsName(card.getField());
            script.append("out['").append(field).append("']=r['").append(field)
                    .append("']!==undefined?r['").append(field).append("']:'';");
        }
        return script.append("return {status:0,msg:'',data:out};").toString();
    }

    private Map<String, Object> chartBlock(DataAppSpec.DataAppBlock block) {
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", chartUrl(block.getQuery().getTool()));
        api.put("data", new LinkedHashMap<>(block.getQuery().getRequest()));
        api.put("adaptor", CHART_ADAPTOR);
        return Map.of(
                "type", "panel",
                "title", StringUtils.defaultString(block.getTitle()),
                "body", Map.of("type", "chart", "api", api,
                        "config", chartConfig(block), "height", 360));
    }

    private Map<String, Object> chartConfig(DataAppSpec.DataAppBlock block) {
        String tool = block.getQuery().getTool();
        String hint = StringUtils.upperCase(StringUtils.defaultString(
                Objects.toString(block.getQuery().getRequest().get("chart_hint"), "")));
        boolean pie = "entity_distribution".equals(tool) || "PIE".equals(hint);
        boolean scatter = "entity_scatter".equals(tool)
                || "SCATTER".equals(hint) || "BUBBLE".equals(hint);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", Map.of("text", StringUtils.defaultString(block.getTitle())));
        config.put("tooltip", Map.of("trigger", pie || scatter ? "item" : "axis"));
        config.put("legend", Map.of("top", 28));
        if (scatter) {
            config.put("xAxis", Map.of("type", "value"));
            config.put("yAxis", Map.of("type", "value"));
        } else if (!pie) {
            config.put("grid", Map.of("top", 70, "left", 48, "right", 24, "bottom", 42));
            Map<String, Object> xAxis = new LinkedHashMap<>();
            xAxis.put("type", "category");
            if ("entity_histogram".equals(tool)) {
                xAxis.put("axisLabel", Map.of("rotate", 30));
            }
            config.put("xAxis", xAxis);
            config.put("yAxis", Map.of("type", "value", "minInterval", 1));
        }
        if (!scatter) {
            config.put("dataset", "${chart_dataset}");
        }
        config.put("series", "${chart_series}");
        return config;
    }

    private String chartUrl(String tool) {
        return API_PREFIX + switch (tool) {
            case "entity_trend" -> "/entity/trend/query";
            case "entity_distribution" -> "/entity/distribution/query";
            case "entity_aggregate" -> "/entity/aggregate/query";
            case "entity_histogram" -> "/entity/histogram/query";
            case "entity_scatter" -> "/entity/scatter/query";
            default -> throw new IllegalArgumentException("不支持的图表查询：" + tool);
        };
    }

    private Map<String, Object> recordsBlock(DataAppSpec.DataAppBlock block, boolean preview) {
        DataAppSpec.DataAppRecords records = block.getRecords();
        DataEntity entity = metaDataService.getDataEntityByName(records.getEntity());
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在：" + records.getEntity());
        }
        Map<String, DataAttribute> attributes = new LinkedHashMap<>();
        for (DataAttribute attribute : metaDataService.getAllDataAttributeByEntity(entity)) {
            attributes.put(attribute.getName(), attribute);
        }
        String crudName = "records-" + block.getBlockId();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (DataAppSpec.DataAppRecordColumn column : records.getColumns()) {
            columns.add(recordColumn(column, attributes.get(column.getField())));
        }
        boolean hasRecordIdColumn = records.getColumns().stream()
                .anyMatch(column -> "zenvis_id".equals(column.getField()));
        if (!hasRecordIdColumn) {
            columns.add(Map.of("name", "zenvis_id", "type", "hidden"));
        }
        boolean mutationAllowed = !preview;
        if (Boolean.TRUE.equals(records.getDetail())
                || (mutationAllowed && (Boolean.TRUE.equals(records.getUpdate())
                || Boolean.TRUE.equals(records.getDelete())))) {
            columns.add(operationColumn(records, entity, attributes, crudName, mutationAllowed));
        }
        Map<String, Object> crud = new LinkedHashMap<>();
        crud.put("type", "crud");
        crud.put("name", crudName);
        crud.put("title", StringUtils.defaultString(block.getTitle()));
        crud.put("api", recordApi(records, block.getBlockId()));
        crud.put("columns", columns);
        crud.put("filter", filterForm(records, attributes));
        crud.put("perPage", records.getPageSize());
        crud.put("pageField", "page");
        crud.put("perPageField", "perPage");
        crud.put("placeholder", "当前实体暂无数据");
        if (!preview && Boolean.TRUE.equals(records.getCreate())) {
            crud.put("headerToolbar", List.of(createButton(records, entity, attributes, crudName)));
        }
        return crud;
    }

    private Map<String, Object> recordColumn(DataAppSpec.DataAppRecordColumn column, DataAttribute attribute) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", column.getField());
        result.put("label", StringUtils.defaultIfBlank(column.getLabel(),
                attribute == null ? column.getField() : attribute.getLabel()));
        result.put("sortable", !Boolean.FALSE.equals(column.getSortable()));
        if (column.getWidth() != null) {
            result.put("width", column.getWidth());
        }
        if (attribute != null) {
            applyDisplayType(result, attribute);
        }
        return result;
    }

    private void applyDisplayType(Map<String, Object> column, DataAttribute attribute) {
        String type = StringUtils.defaultString(attribute.getDisplayType()).toLowerCase(Locale.ROOT);
        String columnType = StringUtils.defaultString(attribute.getColumnType());
        if ("json".equals(type) || columnType.equalsIgnoreCase("JSON")) {
            column.put("type", "json");
        } else if ("array".equals(type) || columnType.startsWith("Array")) {
            column.put("type", "tpl");
            column.put("tpl", "${" + attribute.getName() + "}");
        } else if (columnType.startsWith("DateTime")) {
            column.put("type", "datetime");
        }
        if (attribute.getMapping() != null && !attribute.getMapping().isEmpty()) {
            column.put("type", "mapping");
            column.put("map", attribute.getMapping());
        }
        if (attribute.isCopyable()) {
            column.put("copyable", true);
        }
    }

    private Map<String, Object> filterForm(DataAppSpec.DataAppRecords records,
                                           Map<String, DataAttribute> attributes) {
        if (records.getFilters().isEmpty()) {
            return Map.of("type", "form", "wrapWithPanel", false, "body", List.of(),
                    "submitText", "查询");
        }
        List<Map<String, Object>> body = new ArrayList<>();
        for (DataAppSpec.DataAppRecordFilter filter : records.getFilters()) {
            body.add(filterControl(filter, attributes.get(filter.getField())));
        }
        return Map.of("type", "form", "wrapWithPanel", false, "body", body,
                "submitText", "查询", "resetAfterSubmit", false,
                "actions", List.of(
                        Map.of("type", "reset", "label", "重置"),
                        Map.of("type", "submit", "label", "查询", "level", "primary")));
    }

    private Map<String, Object> filterControl(DataAppSpec.DataAppRecordFilter filter, DataAttribute attribute) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", filter.getField());
        result.put("label", StringUtils.defaultIfBlank(filter.getLabel(),
                attribute == null ? filter.getField() : attribute.getLabel()));
        String inputType = StringUtils.defaultIfBlank(filter.getInputType(), inferInputType(attribute, filter));
        result.put("type", inputType);
        if (attribute != null) {
            applyInputSource(result, attribute);
        }
        return result;
    }

    private String inferInputType(DataAttribute attribute, DataAppSpec.DataAppRecordFilter filter) {
        if (attribute == null) {
            return "input-text";
        }
        String columnType = StringUtils.defaultString(attribute.getColumnType());
        String searchType = StringUtils.defaultString(attribute.getSearchType()).toLowerCase(Locale.ROOT);
        if ("between".equals(filter.getOperator())) {
            if (columnType.startsWith("DateTime")) {
                return "input-datetime-range";
            }
            if (columnType.startsWith("UInt") || columnType.startsWith("Int")
                    || columnType.startsWith("Float") || columnType.startsWith("Decimal")) {
                return "input-number-range";
            }
        }
        if (hasOptionSource(attribute)) {
            return "select";
        }
        if ("datetime".equals(searchType) || columnType.startsWith("DateTime")) {
            return "input-datetime";
        }
        if ("date".equals(searchType) || columnType.startsWith("Date")) {
            return "input-date";
        }
        if ("number".equals(searchType) || columnType.startsWith("UInt") || columnType.startsWith("Int")
                || columnType.startsWith("Float") || columnType.startsWith("Decimal")) {
            return "input-number";
        }
        return "input-text";
    }

    private void applyInputSource(Map<String, Object> control, DataAttribute attribute) {
        String entity = attribute.getEntity();
        String field = attribute.getName();
        if (attribute.getMapping() != null && !attribute.getMapping().isEmpty()) {
            control.put("source", API_PREFIX + "/entity/" + entity + "/" + field + "/mapping");
        } else if (attribute.getEnums() != null && !attribute.getEnums().isEmpty()) {
            List<Map<String, String>> options = new ArrayList<>();
            for (String value : attribute.getEnums()) {
                options.add(Map.of("label", value, "value", value));
            }
            control.put("options", options);
        } else if (attribute.isMustCandidate()) {
            control.put("source", API_PREFIX + "/entity/" + entity + "/" + field + "/list");
        } else if (attribute.isAutoComplete()) {
            control.put("autoComplete", API_PREFIX + "/entity/" + entity + "/" + field
                    + "/auto-complete?term=$term");
        }
    }

    private Map<String, Object> recordApi(DataAppSpec.DataAppRecords records, String blockId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "normal");
        data.put("entity", records.getEntity());
        data.put("criteria_logic", "and");
        data.put("criteria_list", List.of());
        data.put("display_list", List.of(Map.of(
                "entity", records.getEntity(),
                "attribute_list", displayFields(records))));
        data.put("page", "${page}");
        data.put("size", "${perPage}");
        data.put("sortBy", StringUtils.defaultIfBlank(records.getDefaultSortField(), ""));
        data.put("order", records.getDefaultSortDirection());
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", API_PREFIX + "/retrieval/do");
        api.put("data", data);
        api.put("requestAdaptor", requestAdaptor(records));
        api.put("adaptor", RECORD_RESPONSE_ADAPTOR);
        return api;
    }

    private List<String> displayFields(DataAppSpec.DataAppRecords records) {
        List<String> fields = new ArrayList<>();
        for (DataAppSpec.DataAppRecordColumn column : records.getColumns()) {
            fields.add(column.getField());
        }
        if (!fields.contains("zenvis_id")) {
            fields.add("zenvis_id");
        }
        return fields;
    }

    private String requestAdaptor(DataAppSpec.DataAppRecords records) {
        StringBuilder script = new StringBuilder(
                "var values=(context&&typeof context==='object')?context:{};var criteria=[];"
                + "function add(f,o,v){"
                + "if(v===undefined||v===null||v===''||(Array.isArray(v)&&v.length===0))return;"
                + "criteria.push({attribute:f,operator:o,value_list:Array.isArray(v)?v.map(String):[String(v)]});}");
        for (DataAppSpec.DataAppRecordFilter filter : records.getFilters()) {
            script.append("add('").append(js(filter.getField())).append("','")
                    .append(js(filter.getOperator())).append("',values['")
                    .append(js(filter.getField())).append("']);");
        }
        script.append("api.data.criteria_list=criteria;")
                .append("api.data.page=Number(values.page||1);")
                .append("api.data.size=Number(values.perPage||")
                .append(records.getPageSize()).append(");")
                .append("if(values.orderBy){api.data.sortBy=String(values.orderBy);}")
                .append("if(values.orderDir){api.data.order=String(values.orderDir);}")
                .append("return api;");
        return script.toString();
    }

    private Map<String, Object> operationColumn(DataAppSpec.DataAppRecords records,
                                                DataEntity entity,
                                                Map<String, DataAttribute> attributes,
                                                String crudName,
                                                boolean mutationAllowed) {
        List<Map<String, Object>> buttons = new ArrayList<>();
        if (Boolean.TRUE.equals(records.getDetail())) {
            buttons.add(Map.of(
                    "type", "button",
                    "label", "详情",
                    "level", "link",
                    "actionType", "dialog",
                    "dialog", detailDialog(entity, attributes)));
        }
        if (mutationAllowed && Boolean.TRUE.equals(records.getUpdate())) {
            buttons.add(updateButton(entity, attributes, crudName));
        }
        if (mutationAllowed && Boolean.TRUE.equals(records.getDelete())) {
            buttons.add(deleteButton(entity, crudName));
        }
        return Map.of("type", "operation", "label", "操作", "width", 220, "buttons", buttons);
    }

    private Map<String, Object> detailDialog(DataEntity entity, Map<String, DataAttribute> attributes) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (DataAttribute attribute : attributes.values()) {
            items.add(Map.of(
                    "label", StringUtils.defaultIfBlank(attribute.getLabel(), attribute.getName()),
                    "content", "${" + attribute.getName() + "}"));
        }
        return Map.of(
                "title", "记录详情",
                "size", "lg",
                "body", Map.of(
                        "type", "service",
                        "api", "get:" + API_PREFIX + "/entity/" + entity.getName() + "/${zenvis_id}/view",
                        "body", Map.of("type", "property", "column", 2, "items", items)));
    }

    private Map<String, Object> createButton(DataAppSpec.DataAppRecords records,
                                             DataEntity entity,
                                             Map<String, DataAttribute> attributes,
                                             String crudName) {
        Map<String, Object> form = createOrUpdateForm(records, entity, attributes, false, crudName);
        return Map.of(
                "type", "button",
                "label", "新建",
                "level", "primary",
                "actionType", "dialog",
                "dialog", Map.of("title", "新建记录", "size", "lg", "body", form));
    }

    private Map<String, Object> updateButton(DataEntity entity,
                                             Map<String, DataAttribute> attributes,
                                             String crudName) {
        Map<String, Object> form = createOrUpdateForm(null, entity, attributes, true, crudName);
        return Map.of(
                "type", "button",
                "label", "编辑",
                "level", "link",
                "actionType", "dialog",
                "dialog", Map.of("title", "编辑记录", "size", "lg", "body", form));
    }

    private Map<String, Object> createOrUpdateForm(DataAppSpec.DataAppRecords records,
                                                   DataEntity entity,
                                                   Map<String, DataAttribute> attributes,
                                                   boolean update,
                                                   String crudName) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (DataAttribute attribute : attributes.values()) {
            if (isSystem(attribute)) {
                continue;
            }
            body.add(formControl(attribute));
        }
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "form");
        form.put("body", body);
        form.put("submitText", update ? "保存" : "创建");
        String entityPath = API_PREFIX + "/entity/" + entity.getName();
        if (update) {
            form.put("initApi", "get:" + entityPath + "/${zenvis_id}/view");
            form.put("api", "post:" + entityPath + "/${zenvis_id}/update");
        } else {
            form.put("api", "post:" + entityPath + "/add");
        }
        if (StringUtils.isNotBlank(crudName)) {
            form.put("reload", crudName);
        }
        return form;
    }

    private Map<String, Object> formControl(DataAttribute attribute) {
        Map<String, Object> control = new LinkedHashMap<>();
        control.put("name", attribute.getName());
        control.put("label", StringUtils.defaultIfBlank(attribute.getLabel(), attribute.getName()));
        control.put("required", attribute.isRequired());
        applyInputType(control, attribute);
        applyInputSource(control, attribute);
        return control;
    }

    private void applyInputType(Map<String, Object> control, DataAttribute attribute) {
        String type = StringUtils.defaultString(attribute.getColumnType());
        String display = StringUtils.defaultString(attribute.getDisplayType()).toLowerCase(Locale.ROOT);
        if (hasOptionSource(attribute)) {
            control.put("type", "select");
            if (attribute.isMustCandidate() || attribute.isAutoComplete()) {
                control.put("searchable", true);
            }
        } else if ("json".equals(display) || type.equalsIgnoreCase("JSON")) {
            control.put("type", "json-editor");
        } else if ("array".equals(display) || type.startsWith("Array")) {
            control.put("type", "input-tag");
            control.put("clearable", true);
        } else if (type.startsWith("DateTime")) {
            control.put("type", "input-datetime");
            control.put("format", "YYYY-MM-DD HH:mm:ss");
        } else if (type.startsWith("Date")) {
            control.put("type", "input-date");
            control.put("format", "YYYY-MM-DD");
        } else if (type.startsWith("UInt") || type.startsWith("Int")
                || type.startsWith("Float") || type.startsWith("Decimal")) {
            control.put("type", "input-number");
        } else {
            control.put("type", "input-text");
        }
    }

    private boolean hasOptionSource(DataAttribute attribute) {
        return (attribute.getMapping() != null && !attribute.getMapping().isEmpty())
                || (attribute.getEnums() != null && !attribute.getEnums().isEmpty())
                || attribute.isMustCandidate()
                || attribute.isAutoComplete();
    }

    private Map<String, Object> deleteButton(DataEntity entity, String crudName) {
        return Map.of(
                "type", "button",
                "label", "删除",
                "level", "link",
                "className", "text-danger",
                "actionType", "ajax",
                "confirmText", "确认删除该记录？此操作不可撤销。",
                "api", "delete:" + API_PREFIX + "/entity/" + entity.getName() + "/${zenvis_id}",
                "reload", crudName);
    }

    private boolean isSystem(DataAttribute attribute) {
        String column = StringUtils.defaultString(attribute.getColumnName());
        return column.equals("zenvis_id") || column.equals("zenvis_insert_time")
                || column.equals("zenvis_update_time");
    }

    private String safeJsName(String value) {
        return js(value);
    }

    private String js(String value) {
        return StringUtils.defaultString(value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "")
                .replace("\r", "");
    }

    public record CompiledFiles(Map<String, String> files, String menuType) {
    }
}
