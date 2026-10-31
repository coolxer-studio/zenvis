package com.coolxer.service.config;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands a concise entity/field draft into a complete, schema-shaped Meta object.
 */
@Service
public class MetaConfigDraftService {

    private static final Pattern CAMEL_BOUNDARY =
            Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9._-]+\\.json");
    private static final Set<String> RESERVED = Set.of("zenvis_id", "zenvis_insert_time");
    private static final Map<String, String> OPERATOR_LABELS = Map.ofEntries(
            Map.entry("equal", "等于"),
            Map.entry("notequal", "不等于"),
            Map.entry("isnull", "为空"),
            Map.entry("isnotnull", "不为空"),
            Map.entry("match", "模糊匹配"),
            Map.entry("greatthan", "大于"),
            Map.entry("greatequalthan", "大于等于"),
            Map.entry("lessthan", "小于"),
            Map.entry("lessequalthan", "小于等于"),
            Map.entry("between", "之间"),
            Map.entry("in", "包含")
    );

    private final MetaConfigContractService contractService;

    public MetaConfigDraftService(MetaConfigContractService contractService) {
        this.contractService = contractService;
    }

    public DraftResult draft(String fileName, String draftText) {
        List<MetaConfigValidationService.Diagnostic> diagnostics = new ArrayList<>();
        if (!StringUtils.hasText(draftText)) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_EMPTY", "ERROR", "$",
                    "精简草稿不能为空。", "提供 entityName、entityLabel、description 和 fields。"));
            return empty(fileName, diagnostics);
        }
        JsonNode draft;
        try {
            draft = JacksonConfig.OBJECT_MAPPER.readTree(draftText);
        } catch (Exception e) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_JSON", "ERROR", "$",
                    "精简草稿不是合法 JSON：" + e.getMessage(),
                    "传入单个 JSON 对象，不要包含 Markdown。"));
            return empty(fileName, diagnostics);
        }
        if (draft == null || !draft.isObject()) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_OBJECT_REQUIRED", "ERROR", "$",
                    "精简草稿必须是 JSON 对象。", "使用 entityName、entityLabel、description、fields。"));
            return empty(fileName, diagnostics);
        }

        String requestedName = firstText(draft, "entityName", "entity", "name", "tableName");
        String entityName = snake(requestedName);
        if (!StringUtils.hasText(entityName)) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_ENTITY_REQUIRED", "ERROR", "$.entityName",
                    "必须提供实体英文名 entityName。", "使用稳定的 snake_case 实体名。"));
            return empty(fileName, diagnostics);
        }
        String targetFile = resolveFileName(fileName, entityName);
        if (!SAFE_FILE.matcher(targetFile).matches()) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_FILE_INVALID", "ERROR", "$fileName",
                    "目标文件名必须是以 .json 结尾的安全文件名：" + targetFile,
                    "使用 snake_case，例如 user_event.json。"));
        }

        String entityLabel = firstText(draft, "entityLabel", "label");
        if (!StringUtils.hasText(entityLabel)) {
            entityLabel = entityName;
        }
        String description = firstText(draft, "description", "entityDescription", "summary");
        if (!StringUtils.hasText(description)) {
            description = entityLabel;
        }
        String tableName = firstText(draft, "tableName", "table_name");
        if (!StringUtils.hasText(tableName)) {
            tableName = "zenvis." + entityName;
        } else if (tableName.contains(".")) {
            String physicalName = tableName.substring(tableName.lastIndexOf('.') + 1);
            tableName = "zenvis." + snake(physicalName);
        } else {
            tableName = "zenvis." + snake(tableName);
        }

        ObjectNode root = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        ObjectNode entity = root.putArray("entity").addObject();
        entity.put("name", entityName);
        entity.put("label", entityLabel);
        entity.put("description", description);
        entity.put("table_name", tableName);
        entity.put("data_source", "clickhouse");
        ArrayNode attributes = root.putArray("attribute");
        JsonNode fields = draft.get("fields");
        if (fields == null || !fields.isArray() || fields.isEmpty()) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_FIELDS_REQUIRED", "ERROR", "$.fields",
                    "至少提供一个业务字段。", "fields 使用 [{name,label,description,type}]。"));
        } else {
            for (int i = 0; i < fields.size(); i++) {
                appendField(attributes, entityName, fields.get(i), i, diagnostics);
            }
        }
        if (attributes.isEmpty()) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_NO_USABLE_FIELD", "ERROR", "$.fields",
                    "没有可用于生成 Meta 的业务字段。", "移除保留字段并补充至少一个业务字段。"));
        }

        String orderBy = "zenvis_insert_time";
        for (JsonNode attribute : attributes) {
            String columnType = attribute.path("column_type").asText("");
            if (isNonNullableTemporalColumn(columnType)) {
                orderBy = attribute.path("column_name").asText(orderBy);
                break;
            }
        }
        ObjectNode autoCreate = entity.putObject("auto_create");
        autoCreate.put("engine", "MergeTree");
        autoCreate.putArray("order_by").add(orderBy);

        ObjectNode operators = root.putArray("operator").addObject();
        operators.put("name", "equal");
        operators.put("label", OPERATOR_LABELS.get("equal"));
        for (JsonNode attribute : attributes) {
            for (JsonNode operator : attribute.path("operators")) {
                ensureOperator(root, operator.asText());
            }
        }

        String normalized = root.toPrettyString();
        MetaConfigContractService.Snapshot contract = contractService.snapshot();
        return new DraftResult(
                targetFile,
                entityName,
                entityLabel,
                tableName,
                normalized,
                MetaConfigValidationService.digest(normalized),
                contract.schemaVersion(),
                contract.schemaHash(),
                List.copyOf(diagnostics)
        );
    }

    private void appendField(ArrayNode attributes,
                             String entityName,
                             JsonNode field,
                             int index,
                             List<MetaConfigValidationService.Diagnostic> diagnostics) {
        String requestedName;
        String requestedLabel;
        String requestedDescription;
        String requestedType;
        JsonNode explicitOperators = null;
        if (field != null && field.isTextual()) {
            requestedName = field.asText();
            requestedLabel = "";
            requestedDescription = "";
            requestedType = "";
        } else if (field != null && field.isObject()) {
            requestedName = firstText(field, "name", "columnName", "column_name", "field");
            requestedLabel = firstText(field, "label", "displayName", "title");
            requestedDescription = firstText(field, "description", "comment", "summary");
            requestedType = firstText(field, "columnType", "column_type", "type");
            explicitOperators = field.has("operators") ? field.get("operators") : null;
        } else {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_FIELD_INVALID", "ERROR", "$.fields[" + index + "]",
                    "字段定义必须是对象或字符串。", "使用 {name,label,description,type}。"));
            return;
        }
        String name = snake(requestedName);
        if (!StringUtils.hasText(name)) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_FIELD_NAME_REQUIRED", "ERROR", "$.fields[" + index + "].name",
                    "字段缺少逻辑名。", "提供 name，例如 event_type。"));
            return;
        }
        if (RESERVED.contains(name)) {
            diagnostics.add(new MetaConfigValidationService.Diagnostic(
                    "META_DRAFT_RESERVED_FIELD", "WARNING", "$.fields[" + index + "]",
                    "已忽略平台保留字段 " + name + "。", "平台会自动注入该字段。"));
            return;
        }
        String label = StringUtils.hasText(requestedLabel) ? requestedLabel : name;
        String description = StringUtils.hasText(requestedDescription)
                ? requestedDescription : label;
        String columnType = clickHouseType(requestedType, field);
        ObjectNode attribute = attributes.addObject();
        attribute.put("entity", entityName);
        attribute.put("name", name);
        attribute.put("label", label);
        attribute.put("description", description);
        attribute.put("column_name", name);
        attribute.put("column_type", columnType);
        attribute.put("display_selected", true);
        String searchType = searchType(columnType);
        if (StringUtils.hasText(searchType)) {
            attribute.put("search_type", searchType);
        }
        String displayType = displayType(columnType);
        if (StringUtils.hasText(displayType)) {
            attribute.put("display_type", displayType);
        }
        ArrayNode operators = attribute.putArray("operators");
        if (explicitOperators != null && explicitOperators.isArray()) {
            explicitOperators.forEach(item -> {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    operators.add(item.asText());
                }
            });
        } else {
            defaultOperators(columnType).forEach(operators::add);
        }
    }

    private void ensureOperator(ObjectNode root, String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        for (JsonNode operator : root.get("operator")) {
            if (name.equals(operator.path("name").asText())) {
                return;
            }
        }
        ObjectNode added = ((ArrayNode) root.get("operator")).addObject();
        added.put("name", name);
        added.put("label", OPERATOR_LABELS.getOrDefault(name, name));
    }

    private String clickHouseType(String requested, JsonNode field) {
        String value = requested == null ? "" : requested.trim();
        String lower = value.toLowerCase(Locale.ROOT).replace(" ", "");
        if (lower.contains("(")) {
            return value;
        }
        JsonNode sample = field == null ? null : field.get("sample");
        if (sample == null && field != null) {
            sample = field.get("example");
        }
        if (sample != null) {
            if (sample.isIntegralNumber()) {
                return "Int64";
            }
            if (sample.isFloatingPointNumber()) {
                return "Float64";
            }
            if (sample.isBoolean()) {
                return "UInt8";
            }
            if (sample.isArray()) {
                return "Array(String)";
            }
        }
        return switch (lower) {
            case "int", "integer", "bigint", "number", "numeric" -> "Int64";
            case "float", "double", "decimal" -> "Float64";
            case "bool", "boolean" -> "UInt8";
            case "date" -> "Date";
            case "datetime", "timestamp", "time" -> "DateTime64(3)";
            case "array", "array(string)" -> "Array(String)";
            case "object", "json", "map" -> "JSON";
            case "int8" -> "Int8";
            case "int16" -> "Int16";
            case "int32" -> "Int32";
            case "int64" -> "Int64";
            case "uint8" -> "UInt8";
            case "uint16" -> "UInt16";
            case "uint32" -> "UInt32";
            case "uint64" -> "UInt64";
            case "float32" -> "Float32";
            case "float64" -> "Float64";
            default -> "String";
        };
    }

    private List<String> defaultOperators(String columnType) {
        String type = columnType.toLowerCase(Locale.ROOT).replace(" ", "");
        if (type.startsWith("array")) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in", "match");
        }
        if (type.startsWith("date")) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "greatthan",
                    "greatequalthan", "lessthan", "lessequalthan", "between");
        }
        if (type.startsWith("int") || type.startsWith("uint")
                || type.startsWith("float") || type.startsWith("decimal")) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in",
                    "greatthan", "greatequalthan", "lessthan", "lessequalthan", "between");
        }
        return List.of("equal", "notequal", "isnull", "isnotnull", "in", "match");
    }

    private String searchType(String columnType) {
        String type = columnType.toLowerCase(Locale.ROOT).replace(" ", "");
        if (type.startsWith("date")) {
            return "datetime";
        }
        if (type.startsWith("int") || type.startsWith("uint")
                || type.startsWith("float") || type.startsWith("decimal")) {
            return "number";
        }
        return "";
    }

    private String displayType(String columnType) {
        String type = columnType.toLowerCase(Locale.ROOT).replace(" ", "");
        if (type.startsWith("array")) {
            return "array";
        }
        if (type.equals("json") || type.equals("object") || type.startsWith("map")
                || type.startsWith("tuple")) {
            return "json";
        }
        return "";
    }

    private boolean isNonNullableTemporalColumn(String columnType) {
        String type = columnType.toLowerCase(Locale.ROOT).replace(" ", "");
        return !type.startsWith("nullable")
                && (type.startsWith("date") || type.startsWith("datetime"));
    }

    private String resolveFileName(String requested, String entityName) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return entityName + ".json";
    }

    private String snake(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = CAMEL_BOUNDARY.matcher(value.trim()).replaceAll("$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (StringUtils.hasText(normalized) && Character.isDigit(normalized.charAt(0))) {
            normalized = "f_" + normalized;
        }
        return normalized;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private DraftResult empty(String fileName,
                              List<MetaConfigValidationService.Diagnostic> diagnostics) {
        MetaConfigContractService.Snapshot contract = contractService.snapshot();
        return new DraftResult(fileName == null ? "" : fileName, "", "", "", "",
                "", contract.schemaVersion(), contract.schemaHash(), List.copyOf(diagnostics));
    }

    public record DraftResult(String fileName,
                              String entityName,
                              String entityLabel,
                              String tableName,
                              String text,
                              String candidateDigest,
                              String schemaVersion,
                              String schemaHash,
                              List<MetaConfigValidationService.Diagnostic> diagnostics) {

        public boolean passed() {
            return diagnostics.stream().noneMatch(item -> "ERROR".equals(item.severity()));
        }
    }
}
