package com.coolxer.service.config;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.service.retrieval.MetaDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 元数据候选的确定性规范化与平台语义预检。
 *
 * <p>该服务只读现有 Meta，不修改配置文件。校验时把候选与除目标文件之外的
 * 已发布 Meta 放在临时目录中，复用运行时加载规则检查跨文件冲突。</p>
 */
@Slf4j
@Service
public class MetaConfigValidationService {

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern PHYSICAL_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[A-Za-z0-9._-]+\\.json");
    private static final Pattern NUMBER_TYPE =
            Pattern.compile("(?i)(?:u?int(?:8|16|32|64|128|256)|float(?:32|64)|decimal\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\))");
    private static final Pattern TEMPORAL_TYPE =
            Pattern.compile("(?i)(?:date32?|datetime(?:64)?(?:\\(\\s*\\d+\\s*\\))?)");
    private static final Pattern SAFE_TYPE_TOKEN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\([^;\\r\\n]*\\))?");
    private static final Pattern LINK_TEMPLATE_PLACEHOLDER =
            Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern URI_SCHEME =
            Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

    private static final Set<String> TOP_LEVEL_FIELDS =
            Set.of("entity", "attribute", "operator");
    private static final Set<String> ENTITY_FIELDS = Set.of(
            "id", "name", "label", "description", "table_name", "data_source",
            "sort_column", "auto_create");
    private static final Set<String> AUTO_CREATE_FIELDS = Set.of(
            "engine", "order_by", "partition_by", "ttl");
    private static final Set<String> TTL_FIELDS = Set.of(
            "column", "expire_after", "unit");
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of(
            "id", "entity", "name", "description", "label", "retrieval_type",
            "search_type", "column_name", "column_type", "display_selected",
            "display_name", "display_type", "operators", "must_candidate",
            "auto_complete", "copyable", "required", "mapping", "enums",
            "link_template", "link_condition");
    private static final Set<String> LINK_CONDITION_FIELDS =
            Set.of("field", "operator", "value");
    private static final Set<String> OPERATOR_FIELDS =
            Set.of("id", "name", "label");
    private static final Set<String> RESERVED_ATTRIBUTES =
            Set.of("zenvis_id", "zenvis_insert_time");
    private static final Set<String> DEFAULT_OPERATORS =
            Set.of("equal", "notequal", "isnull", "isnotnull", "match",
                    "greatthan", "greatequalthan", "lessthan", "lessequalthan",
                    "between", "in");
    private static final Set<String> LINK_CONDITION_OPERATORS = Set.of(
            "equal", "notequal", "isnull", "isnotnull",
            "greatthan", "greatequalthan", "lessthan", "lessequalthan");
    private static final Set<String> LINK_CONDITION_VALUELESS_OPERATORS =
            Set.of("isnull", "isnotnull");
    private static final Set<String> SEARCH_TYPES =
            Set.of("string", "date", "datetime", "number");
    private static final Set<String> COLUMN_BASE_TYPES = Set.of(
            "int8", "int16", "int32", "int64", "int128", "int256",
            "uint8", "uint16", "uint32", "uint64", "uint128", "uint256",
            "float32", "float64", "string", "fixedstring", "bool", "boolean",
            "date", "date32", "datetime", "datetime64", "uuid", "json", "object",
            "ipv4", "ipv6", "nothing", "point", "ring", "polygon", "linestring",
            "multipolygon", "multilinestring", "interval");
    private static final Map<String, String> DEFAULT_OPERATOR_LABELS = Map.ofEntries(
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
    private static final Map<String, String> ROOT_ALIASES = Map.of(
            "entities", "entity",
            "attributes", "attribute",
            "operators", "operator"
    );
    private static final Map<String, String> ENTITY_ALIASES = Map.ofEntries(
            Map.entry("tableName", "table_name"),
            Map.entry("dataSource", "data_source"),
            Map.entry("sortColumn", "sort_column"),
            Map.entry("autoCreate", "auto_create")
    );
    private static final Map<String, String> AUTO_CREATE_ALIASES = Map.of(
            "orderBy", "order_by",
            "partitionBy", "partition_by"
    );
    private static final Map<String, String> TTL_ALIASES =
            Map.of("expireAfter", "expire_after");
    private static final Map<String, String> ATTRIBUTE_ALIASES = Map.ofEntries(
            Map.entry("columnName", "column_name"),
            Map.entry("columnType", "column_type"),
            Map.entry("displaySelected", "display_selected"),
            Map.entry("displayName", "display_name"),
            Map.entry("displayType", "display_type"),
            Map.entry("operatorList", "operators"),
            Map.entry("mustCandidate", "must_candidate"),
            Map.entry("autoComplete", "auto_complete"),
            Map.entry("retrievalType", "retrieval_type"),
            Map.entry("searchType", "search_type"),
            Map.entry("linkTemplate", "link_template"),
            Map.entry("linkCondition", "link_condition")
    );
    private static final Map<String, String> OPERATOR_ALIASES =
            Map.of("description", "label");

    private final ConfigService configService;
    private final MetaDataService metaDataService;

    @Autowired
    public MetaConfigValidationService(ConfigService configService,
                                       MetaDataService metaDataService) {
        this.configService = configService;
        this.metaDataService = metaDataService;
    }

    public ValidationResult validate(String fileName, String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Repair> repairs = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            diagnostics.add(new Diagnostic(
                    "META_EMPTY", "ERROR", "$",
                    "Meta 配置不能为空。", "生成完整的 entity/attribute/operator JSON。"));
            return result("", diagnostics, repairs);
        }
        if (!StringUtils.hasText(fileName) || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            diagnostics.add(new Diagnostic(
                    "META_FILE_NAME_INVALID", "ERROR", "$fileName",
                    "Meta 文件名必须是安全的 .json 文件名。",
                    "使用 snake_case，例如 user_event.json。"));
        }

        JsonNode parsed;
        try {
            parsed = JacksonConfig.OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            diagnostics.add(new Diagnostic(
                    "META_JSON_SYNTAX", "ERROR", "$",
                    "JSON 解析失败：" + e.getMessage(),
                    "输出单个完整 JSON 对象，不要包含 Markdown 或注释。"));
            return result(text, diagnostics, repairs);
        }
        if (parsed == null || !parsed.isObject()) {
            diagnostics.add(new Diagnostic(
                    "META_OBJECT_REQUIRED", "ERROR", "$",
                    "Meta 配置顶层必须是 JSON 对象。",
                    "顶层使用 entity、attribute、operator 三个数组。"));
            return result(text, diagnostics, repairs);
        }

        ObjectNode root = (ObjectNode) parsed;
        normalize(root, diagnostics, repairs);
        strictCheck(root, diagnostics);
        validateCandidateSemantics(root, diagnostics);
        validateCrossFileConflicts(fileName, root, diagnostics);
        validateWithPlatform(fileName, root, diagnostics);

        String normalized = root.toPrettyString();
        return result(normalized, diagnostics, repairs);
    }

    private ValidationResult result(String normalizedText,
                                    List<Diagnostic> diagnostics,
                                    List<Repair> repairs) {
        List<String> errors = diagnostics.stream()
                .filter(item -> "ERROR".equals(item.severity()))
                .map(Diagnostic::message)
                .toList();
        List<String> warnings = diagnostics.stream()
                .filter(item -> "WARNING".equals(item.severity()))
                .map(Diagnostic::message)
                .toList();
        return new ValidationResult(
                errors.isEmpty(),
                normalizedText,
                digest(normalizedText),
                List.copyOf(diagnostics),
                List.copyOf(repairs),
                errors,
                warnings
        );
    }

    private void normalize(ObjectNode root,
                           List<Diagnostic> diagnostics,
                           List<Repair> repairs) {
        repairAliases(root, "$", ROOT_ALIASES, repairs);
        ensureArray(root, "entity", diagnostics, repairs);
        ensureArray(root, "attribute", diagnostics, repairs);
        ensureArray(root, "operator", diagnostics, repairs);

        ArrayNode entities = (ArrayNode) root.get("entity");
        ArrayNode attributes = (ArrayNode) root.get("attribute");
        ArrayNode operators = (ArrayNode) root.get("operator");
        for (int i = 0; i < entities.size(); i++) {
            if (!entities.get(i).isObject()) {
                continue;
            }
            ObjectNode entity = (ObjectNode) entities.get(i);
            String path = "$.entity[" + i + "]";
            repairAliases(entity, path, ENTITY_ALIASES, repairs);
            normalizeEntity(entity, path, operators, repairs);
        }
        for (int i = 0; i < attributes.size(); i++) {
            if (!attributes.get(i).isObject()) {
                continue;
            }
            ObjectNode attribute = (ObjectNode) attributes.get(i);
            String path = "$.attribute[" + i + "]";
            repairAliases(attribute, path, ATTRIBUTE_ALIASES, repairs);
            normalizeAttribute(attribute, path, repairs);
        }
        for (int i = 0; i < operators.size(); i++) {
            if (!operators.get(i).isObject()) {
                continue;
            }
            ObjectNode operator = (ObjectNode) operators.get(i);
            String path = "$.operator[" + i + "]";
            repairAliases(operator, path, OPERATOR_ALIASES, repairs);
            String name = text(operator.get("name"));
            if (StringUtils.hasText(name) && !operator.hasNonNull("label")) {
                String label = DEFAULT_OPERATOR_LABELS.getOrDefault(name, name);
                operator.put("label", label);
                repairs.add(new Repair(path + ".label", "", label,
                        "补齐操作符展示名称"));
            }
        }
        ensureReferencedOperators(root, repairs);
    }

    private void normalizeEntity(ObjectNode entity,
                                 String path,
                                 ArrayNode operators,
                                 List<Repair> repairs) {
        if (entity.has("auto_create") && entity.get("auto_create").isObject()) {
            ObjectNode autoCreate = (ObjectNode) entity.get("auto_create");
            repairAliases(autoCreate, path + ".auto_create", AUTO_CREATE_ALIASES, repairs);
            if (autoCreate.has("ttl") && autoCreate.get("ttl").isObject()) {
                repairAliases((ObjectNode) autoCreate.get("ttl"),
                        path + ".auto_create.ttl", TTL_ALIASES, repairs);
            }
        }
        String dataSource = text(entity.get("data_source"));
        if (!StringUtils.hasText(dataSource) || !"clickhouse".equalsIgnoreCase(dataSource)) {
            entity.put("data_source", "clickhouse");
            repairs.add(new Repair(path + ".data_source", dataSource, "clickhouse",
                    "平台 Meta 数据源固定为 clickhouse"));
        }
        String entityName = text(entity.get("name"));
        String tableName = text(entity.get("table_name"));
        if (!StringUtils.hasText(tableName) && StringUtils.hasText(entityName)) {
            tableName = "zenvis." + entityName;
            entity.put("table_name", tableName);
            repairs.add(new Repair(path + ".table_name", "", tableName,
                    "按 zenvis.<实体名> 补齐目标表"));
        } else if (StringUtils.hasText(tableName) && !tableName.contains(".")) {
            String qualified = "zenvis." + tableName;
            entity.put("table_name", qualified);
            repairs.add(new Repair(path + ".table_name", tableName, qualified,
                    "目标表统一使用 zenvis 数据库前缀"));
        } else if (StringUtils.hasText(tableName)) {
            int separator = tableName.lastIndexOf('.');
            String table = tableName.substring(separator + 1);
            String qualified = "zenvis." + table;
            if (StringUtils.hasText(table) && !qualified.equals(tableName)) {
                entity.put("table_name", qualified);
                repairs.add(new Repair(path + ".table_name", tableName, qualified,
                        "目标表统一使用 zenvis 数据库前缀"));
            }
        }
        if (!entity.hasNonNull("auto_create")) {
            ObjectNode autoCreate = JacksonConfig.OBJECT_MAPPER.createObjectNode();
            autoCreate.put("engine", "MergeTree");
            ArrayNode orderBy = autoCreate.putArray("order_by");
            orderBy.add(StringUtils.hasText(text(entity.get("sort_column")))
                    ? text(entity.get("sort_column")) : "zenvis_insert_time");
            entity.set("auto_create", autoCreate);
            repairs.add(new Repair(path + ".auto_create", "", autoCreate.toString(),
                    "补齐可自动建表的 MergeTree 定义"));
        } else if (entity.get("auto_create").isObject()) {
            ObjectNode autoCreate = (ObjectNode) entity.get("auto_create");
            if (!autoCreate.hasNonNull("engine")) {
                autoCreate.put("engine", "MergeTree");
                repairs.add(new Repair(path + ".auto_create.engine", "", "MergeTree",
                        "缺少引擎时使用 MergeTree"));
            }
            if (!autoCreate.has("order_by") || !autoCreate.get("order_by").isArray()
                    || autoCreate.get("order_by").isEmpty()) {
                ArrayNode orderBy = autoCreate.putArray("order_by");
                orderBy.add(StringUtils.hasText(text(entity.get("sort_column")))
                        ? text(entity.get("sort_column")) : "zenvis_insert_time");
                repairs.add(new Repair(path + ".auto_create.order_by", "",
                        orderBy.toString(), "补齐排序键"));
            }
        }
    }

    private void normalizeAttribute(ObjectNode attribute,
                                    String path,
                                    List<Repair> repairs) {
        String name = text(attribute.get("name"));
        String columnName = text(attribute.get("column_name"));
        if (!StringUtils.hasText(columnName) && StringUtils.hasText(name)) {
            attribute.put("column_name", name);
            repairs.add(new Repair(path + ".column_name", "", name,
                    "字段物理列名默认使用逻辑名"));
        }
        if (!attribute.has("display_selected")) {
            attribute.put("display_selected", true);
            repairs.add(new Repair(path + ".display_selected", "", "true",
                    "字段默认展示"));
        }
        if (!attribute.has("operators") || !attribute.get("operators").isArray()) {
            ArrayNode operators = attribute.putArray("operators");
            defaultOperators(text(attribute.get("column_type")))
                    .forEach(operators::add);
            repairs.add(new Repair(path + ".operators", "", operators.toString(),
                    "按字段类型补齐默认检索操作符"));
        }
        String columnType = text(attribute.get("column_type"));
        String displayType = text(attribute.get("display_type"));
        if (!StringUtils.hasText(displayType)) {
            String inferred = inferDisplayType(columnType);
            if (StringUtils.hasText(inferred)) {
                attribute.put("display_type", inferred);
                repairs.add(new Repair(path + ".display_type", "", inferred,
                        "按键类型补齐展示类型"));
            }
        }
        if (!attribute.hasNonNull("search_type")) {
            String inferred = inferSearchType(columnType);
            if (StringUtils.hasText(inferred)) {
                attribute.put("search_type", inferred);
                repairs.add(new Repair(path + ".search_type", "", inferred,
                        "按键类型补齐检索输入类型"));
            }
        }
        if (attribute.has("link_condition") && attribute.get("link_condition").isObject()) {
            repairAliases((ObjectNode) attribute.get("link_condition"),
                    path + ".link_condition", Map.of(), repairs);
        }
    }

    private void ensureReferencedOperators(ObjectNode root, List<Repair> repairs) {
        ArrayNode operators = (ArrayNode) root.get("operator");
        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode operator : operators) {
            declared.add(text(operator.get("name")));
        }
        for (JsonNode attribute : (ArrayNode) root.get("attribute")) {
            JsonNode list = attribute.get("operators");
            if (list == null || !list.isArray()) {
                continue;
            }
            for (JsonNode item : list) {
                String name = text(item);
                if (!StringUtils.hasText(name) || declared.contains(name)
                        || !DEFAULT_OPERATORS.contains(name)) {
                    continue;
                }
                ObjectNode operator = operators.addObject();
                operator.put("name", name);
                operator.put("label", DEFAULT_OPERATOR_LABELS.getOrDefault(name, name));
                declared.add(name);
                repairs.add(new Repair("$.operator", "", name,
                        "补齐被字段引用的默认操作符定义"));
            }
        }
    }

    private void strictCheck(ObjectNode root, List<Diagnostic> diagnostics) {
        unknownFields(root, TOP_LEVEL_FIELDS, "$", diagnostics);
        ensureArrayType(root, "entity", "$.entity", diagnostics);
        ensureArrayType(root, "attribute", "$.attribute", diagnostics);
        ensureArrayType(root, "operator", "$.operator", diagnostics);
        checkObjects((ArrayNode) root.get("entity"), "$.entity", ENTITY_FIELDS, diagnostics);
        checkObjects((ArrayNode) root.get("attribute"), "$.attribute", ATTRIBUTE_FIELDS, diagnostics);
        checkObjects((ArrayNode) root.get("operator"), "$.operator", OPERATOR_FIELDS, diagnostics);
        validateFieldTypes(root, diagnostics);
        for (int i = 0; i < root.get("entity").size(); i++) {
            JsonNode entity = root.get("entity").get(i);
            if (entity == null || !entity.isObject()) {
                continue;
            }
            JsonNode autoCreate = entity.get("auto_create");
            if (autoCreate != null && autoCreate.isObject()) {
                unknownFields(autoCreate, AUTO_CREATE_FIELDS,
                        "$.entity[" + i + "].auto_create", diagnostics);
                JsonNode ttl = autoCreate.get("ttl");
                if (ttl != null && ttl.isObject()) {
                    unknownFields(ttl, TTL_FIELDS,
                            "$.entity[" + i + "].auto_create.ttl", diagnostics);
                }
            }
        }
        for (int i = 0; i < root.get("attribute").size(); i++) {
            JsonNode attribute = root.get("attribute").get(i);
            if (attribute == null || !attribute.isObject()) {
                continue;
            }
            JsonNode condition = attribute.get("link_condition");
            if (condition != null && condition.isObject()) {
                unknownFields(condition, LINK_CONDITION_FIELDS,
                        "$.attribute[" + i + "].link_condition", diagnostics);
            }
        }
    }

    private void validateFieldTypes(ObjectNode root, List<Diagnostic> diagnostics) {
        for (int i = 0; i < root.get("entity").size(); i++) {
            JsonNode entity = root.get("entity").get(i);
            if (entity == null || !entity.isObject()) {
                continue;
            }
            String path = "$.entity[" + i + "]";
            integerField(entity, "id", path, diagnostics);
            textField(entity, "name", path, diagnostics);
            textField(entity, "label", path, diagnostics);
            textField(entity, "description", path, diagnostics);
            textField(entity, "table_name", path, diagnostics);
            textField(entity, "data_source", path, diagnostics);
            textField(entity, "sort_column", path, diagnostics);
            objectField(entity, "auto_create", path, diagnostics);
            JsonNode autoCreate = entity.get("auto_create");
            if (autoCreate != null && autoCreate.isObject()) {
                String autoPath = path + ".auto_create";
                textField(autoCreate, "engine", autoPath, diagnostics);
                stringArrayField(autoCreate, "order_by", autoPath, diagnostics);
                textField(autoCreate, "partition_by", autoPath, diagnostics);
                objectField(autoCreate, "ttl", autoPath, diagnostics);
                JsonNode ttl = autoCreate.get("ttl");
                if (ttl != null && ttl.isObject()) {
                    String ttlPath = autoPath + ".ttl";
                    textField(ttl, "column", ttlPath, diagnostics);
                    integerField(ttl, "expire_after", ttlPath, diagnostics);
                    textField(ttl, "unit", ttlPath, diagnostics);
                }
            }
        }
        for (int i = 0; i < root.get("operator").size(); i++) {
            JsonNode operator = root.get("operator").get(i);
            if (operator == null || !operator.isObject()) {
                continue;
            }
            String path = "$.operator[" + i + "]";
            integerField(operator, "id", path, diagnostics);
            textField(operator, "name", path, diagnostics);
            textField(operator, "label", path, diagnostics);
        }
        for (int i = 0; i < root.get("attribute").size(); i++) {
            JsonNode attribute = root.get("attribute").get(i);
            if (attribute == null || !attribute.isObject()) {
                continue;
            }
            String path = "$.attribute[" + i + "]";
            integerField(attribute, "id", path, diagnostics);
            for (String field : List.of(
                    "entity", "name", "description", "label", "retrieval_type",
                    "search_type", "column_name", "column_type", "display_name",
                    "display_type", "link_template")) {
                textField(attribute, field, path, diagnostics);
            }
            for (String field : List.of(
                    "display_selected", "must_candidate", "auto_complete",
                    "copyable", "required")) {
                booleanField(attribute, field, path, diagnostics);
            }
            stringArrayField(attribute, "operators", path, diagnostics);
            stringArrayField(attribute, "enums", path, diagnostics);
            objectField(attribute, "mapping", path, diagnostics);
            objectField(attribute, "link_condition", path, diagnostics);
            JsonNode condition = attribute.get("link_condition");
            if (condition != null && condition.isObject()) {
                String conditionPath = path + ".link_condition";
                textField(condition, "field", conditionPath, diagnostics);
                textField(condition, "operator", conditionPath, diagnostics);
                textField(condition, "value", conditionPath, diagnostics);
            }
        }
    }

    private void textField(JsonNode node, String field, String path,
                           List<Diagnostic> diagnostics) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isTextual()) {
            invalidFieldType(path, field, "字符串", diagnostics);
        }
    }

    private void booleanField(JsonNode node, String field, String path,
                              List<Diagnostic> diagnostics) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isBoolean()) {
            invalidFieldType(path, field, "布尔值", diagnostics);
        }
    }

    private void integerField(JsonNode node, String field, String path,
                              List<Diagnostic> diagnostics) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isIntegralNumber()) {
            invalidFieldType(path, field, "整数", diagnostics);
        }
    }

    private void objectField(JsonNode node, String field, String path,
                             List<Diagnostic> diagnostics) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull() && !value.isObject()) {
            invalidFieldType(path, field, "对象", diagnostics);
        }
    }

    private void stringArrayField(JsonNode node, String field, String path,
                                  List<Diagnostic> diagnostics) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            invalidFieldType(path, field, "字符串数组", diagnostics);
            return;
        }
        for (int i = 0; i < value.size(); i++) {
            JsonNode item = value.get(i);
            if (item == null || !item.isTextual()) {
                diagnostics.add(new Diagnostic(
                        "META_FIELD_ITEM_TYPE_INVALID", "ERROR",
                        path + "." + field + "[" + i + "]",
                        path + "." + field + "[" + i + "] 必须是字符串。",
                        "删除非字符串项或改为字符串值。"));
            }
        }
    }

    private void invalidFieldType(String path, String field, String expected,
                                  List<Diagnostic> diagnostics) {
        diagnostics.add(new Diagnostic(
                "META_FIELD_TYPE_INVALID", "ERROR", path + "." + field,
                path + "." + field + " 必须是" + expected + "。",
                "按 Meta Schema 使用正确的 JSON 值类型。"));
    }

    private void checkObjects(ArrayNode array,
                              String path,
                              Set<String> allowed,
                              List<Diagnostic> diagnostics) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonNode item = array.get(i);
            String itemPath = path + "[" + i + "]";
            if (item == null || !item.isObject()) {
                diagnostics.add(new Diagnostic(
                        "META_OBJECT_ITEM_REQUIRED", "ERROR", itemPath,
                        itemPath + " 必须是 JSON 对象。", "使用对象数组而非字符串或空值。"));
                continue;
            }
            unknownFields(item, allowed, itemPath, diagnostics);
        }
    }

    private void unknownFields(JsonNode node,
                               Set<String> allowed,
                               String path,
                               List<Diagnostic> diagnostics) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                diagnostics.add(new Diagnostic(
                        "META_UNKNOWN_FIELD", "ERROR", path + "." + name,
                        "未知字段 " + path + "." + name + "，平台会忽略该字段。",
                        "删除该字段或改用 snake_case 的合法字段名。"));
            }
        }
    }

    private void validateCandidateSemantics(ObjectNode root,
                                            List<Diagnostic> diagnostics) {
        Set<String> entityNames = new HashSet<>();
        Set<String> entityIds = new HashSet<>();
        Set<String> tableNames = new HashSet<>();
        for (int i = 0; i < root.get("entity").size(); i++) {
            JsonNode node = root.get("entity").get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String path = "$.entity[" + i + "]";
            String name = requiredText(node, "name", path, diagnostics);
            requiredText(node, "label", path, diagnostics);
            requiredText(node, "description", path, diagnostics);
            String tableName = requiredText(node, "table_name", path, diagnostics);
            if (StringUtils.hasText(name) && !IDENTIFIER.matcher(name).matches()) {
                diagnostics.add(new Diagnostic(
                        "META_ENTITY_NAME_INVALID", "ERROR", path + ".name",
                        "实体名不合法：" + name,
                        "使用字母或下划线开头的 snake_case。"));
            }
            if (StringUtils.hasText(tableName)
                    && !PHYSICAL_IDENTIFIER.matcher(tableName).matches()) {
                diagnostics.add(new Diagnostic(
                        "META_TABLE_NAME_INVALID", "ERROR", path + ".table_name",
                        "目标表名不合法：" + tableName,
                        "使用 zenvis.<snake_case_table>。"));
            }
            if (StringUtils.hasText(name) && !entityNames.add(name)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_ENTITY", "ERROR", path + ".name",
                        "重复实体：" + name, "每个实体只定义一次。"));
            }
            if (StringUtils.hasText(tableName) && !tableNames.add(tableName)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_TABLE", "ERROR", path + ".table_name",
                        "重复目标表：" + tableName, "不同实体不能共用同一目标表。"));
            }
            String id = text(node.get("id"));
            validateNonNegativeId(node, path, diagnostics);
            if (StringUtils.hasText(id) && !"0".equals(id) && !entityIds.add(id)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_ENTITY_ID", "ERROR", path + ".id",
                        "重复实体 ID：" + id, "为非零实体 ID 使用唯一值，或省略 id。"));
            }
            validateEntityAutoCreate(node, path, root.get("attribute"), diagnostics);
        }

        Set<String> operatorNames = new HashSet<>();
        for (int i = 0; i < root.get("operator").size(); i++) {
            JsonNode node = root.get("operator").get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String path = "$.operator[" + i + "]";
            String name = requiredText(node, "name", path, diagnostics);
            requiredText(node, "label", path, diagnostics);
            validateNonNegativeId(node, path, diagnostics);
            if (StringUtils.hasText(name) && !IDENTIFIER.matcher(name).matches()) {
                diagnostics.add(new Diagnostic(
                        "META_OPERATOR_NAME_INVALID", "ERROR", path + ".name",
                        "操作符名不合法：" + name, "使用平台操作符名。"));
            }
            if (StringUtils.hasText(name) && !operatorNames.add(name)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_OPERATOR", "ERROR", path + ".name",
                        "重复操作符：" + name, "每个操作符只定义一次。"));
            }
        }

        Set<String> attributeKeys = new HashSet<>();
        Set<String> attributeIds = new HashSet<>();
        Map<String, Set<String>> attributeNamesByEntity = new LinkedHashMap<>();
        for (int i = 0; i < root.get("attribute").size(); i++) {
            JsonNode node = root.get("attribute").get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            String path = "$.attribute[" + i + "]";
            String entity = requiredText(node, "entity", path, diagnostics);
            String name = requiredText(node, "name", path, diagnostics);
            requiredText(node, "label", path, diagnostics);
            requiredText(node, "description", path, diagnostics);
            String columnName = requiredText(node, "column_name", path, diagnostics);
            String columnType = requiredText(node, "column_type", path, diagnostics);
            validateNonNegativeId(node, path, diagnostics);
            if (StringUtils.hasText(entity) && !entityNames.contains(entity)) {
                diagnostics.add(new Diagnostic(
                        "META_UNKNOWN_ENTITY", "ERROR", path + ".entity",
                        "字段引用了不存在的实体：" + entity,
                        "先在同文件 entity 数组中定义该实体。"));
            }
            if (StringUtils.hasText(name) && !IDENTIFIER.matcher(name).matches()) {
                diagnostics.add(new Diagnostic(
                        "META_ATTRIBUTE_NAME_INVALID", "ERROR", path + ".name",
                        "字段逻辑名不合法：" + name, "使用 snake_case。"));
            }
            if (StringUtils.hasText(columnName)
                    && !PHYSICAL_IDENTIFIER.matcher(columnName).matches()) {
                diagnostics.add(new Diagnostic(
                        "META_COLUMN_NAME_INVALID", "ERROR", path + ".column_name",
                        "字段物理列名不合法：" + columnName, "使用 snake_case 物理列名。"));
            }
            if (RESERVED_ATTRIBUTES.contains(name)
                    || RESERVED_ATTRIBUTES.contains(columnName)) {
                diagnostics.add(new Diagnostic(
                        "META_RESERVED_ATTRIBUTE", "ERROR", path,
                        "不得重复定义平台保留字段：" + (RESERVED_ATTRIBUTES.contains(name)
                                ? name : columnName),
                        "删除 zenvis_id 与 zenvis_insert_time，平台会自动注入。"));
            }
            if (StringUtils.hasText(entity) && StringUtils.hasText(name)
                    && !attributeKeys.add(entity + "." + name)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_ATTRIBUTE", "ERROR", path + ".name",
                        "实体 " + entity + " 存在重复字段：" + name,
                        "同一实体内字段逻辑名必须唯一。"));
            }
            if (StringUtils.hasText(entity) && StringUtils.hasText(name)) {
                attributeNamesByEntity.computeIfAbsent(entity, ignored -> new LinkedHashSet<>())
                        .add(name);
            }
            String id = text(node.get("id"));
            if (StringUtils.hasText(id) && !"0".equals(id) && !attributeIds.add(id)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_ATTRIBUTE_ID", "ERROR", path + ".id",
                        "重复字段 ID：" + id, "为非零字段 ID 使用唯一值，或省略 id。"));
            }
            if (StringUtils.hasText(columnType) && !isSupportedColumnType(columnType)) {
                diagnostics.add(new Diagnostic(
                        "META_COLUMN_TYPE_INVALID", "ERROR", path + ".column_type",
                        "不支持的 ClickHouse 字段类型：" + columnType,
                        "使用平台支持的 ClickHouse 类型，例如 String、UInt64、DateTime64(3)、Array(String)。"));
            }
            String searchType = text(node.get("search_type"));
            if (StringUtils.hasText(searchType) && !SEARCH_TYPES.contains(searchType)) {
                diagnostics.add(new Diagnostic(
                        "META_SEARCH_TYPE_INVALID", "ERROR", path + ".search_type",
                        "不支持的 search_type：" + searchType,
                        "使用 string、date、datetime 或 number。"));
            }
            validateAttributeOperators(node, path, operatorNames, diagnostics);
            validateLinkCondition(node, path, entity, root.get("attribute"), diagnostics);
        }
        validateLinkTemplates(root.get("attribute"), attributeNamesByEntity, diagnostics);
    }

    private void validateLinkTemplates(JsonNode attributes,
                                       Map<String, Set<String>> namesByEntity,
                                       List<Diagnostic> diagnostics) {
        if (attributes == null || !attributes.isArray()) {
            return;
        }
        for (int i = 0; i < attributes.size(); i++) {
            JsonNode attribute = attributes.get(i);
            String template = text(attribute.get("link_template"));
            if (!StringUtils.hasText(template)) {
                continue;
            }
            String path = "$.attribute[" + i + "].link_template";
            if (template.chars().anyMatch(Character::isISOControl)) {
                diagnostics.add(new Diagnostic(
                        "META_LINK_TEMPLATE_INVALID", "ERROR", path,
                        "link_template 不能包含控制字符。",
                        "使用单行相对地址或 http/https 地址。"));
                continue;
            }
            if (URI_SCHEME.matcher(template).find()
                    && !template.toLowerCase(Locale.ROOT).startsWith("http://")
                    && !template.toLowerCase(Locale.ROOT).startsWith("https://")) {
                diagnostics.add(new Diagnostic(
                        "META_LINK_TEMPLATE_SCHEME_INVALID", "ERROR", path,
                        "link_template 只允许相对地址或 http/https 地址。",
                        "移除不支持的 URI scheme。"));
            }
            String entity = text(attribute.get("entity"));
            Set<String> names = namesByEntity.getOrDefault(entity, Set.of());
            var matcher = LINK_TEMPLATE_PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                if (!"zenvis_id".equals(placeholder) && !names.contains(placeholder)) {
                    diagnostics.add(new Diagnostic(
                            "META_LINK_TEMPLATE_REFERENCE_INVALID", "ERROR", path,
                            "link_template 引用了当前实体不存在的字段：" + placeholder,
                            "改为当前实体的逻辑字段名，或使用 {zenvis_id}。"));
                }
            }
        }
    }

    private void validateEntityAutoCreate(JsonNode entity,
                                          String path,
                                          JsonNode attributes,
                                          List<Diagnostic> diagnostics) {
        JsonNode autoCreate = entity.get("auto_create");
        if (autoCreate == null || !autoCreate.isObject()) {
            return;
        }
        String engine = requiredText(autoCreate, "engine", path + ".auto_create", diagnostics);
        if (StringUtils.hasText(engine) && !"MergeTree".equalsIgnoreCase(engine)) {
            diagnostics.add(new Diagnostic(
                    "META_ENGINE_UNSUPPORTED", "ERROR", path + ".auto_create.engine",
                    "元数据智能体仅允许使用 MergeTree，当前为 " + engine,
                    "将 engine 改为 MergeTree。"));
        }
        JsonNode orderBy = autoCreate.get("order_by");
        if (orderBy == null || !orderBy.isArray() || orderBy.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    "META_ORDER_BY_REQUIRED", "ERROR", path + ".auto_create.order_by",
                    "auto_create.order_by 至少需要一个字段。",
                    "优先使用业务时间或低基数业务字段，禁止使用 zenvis_id。"));
        } else {
            for (JsonNode item : orderBy) {
                String column = text(item);
                if (!StringUtils.hasText(column)
                        || !PHYSICAL_IDENTIFIER.matcher(column).matches()) {
                    diagnostics.add(new Diagnostic(
                            "META_ORDER_BY_INVALID", "ERROR", path + ".auto_create.order_by",
                            "排序键不合法：" + column, "使用字段物理列名。"));
                }
                if ("zenvis_id".equals(column)) {
                    diagnostics.add(new Diagnostic(
                            "META_ORDER_BY_HIGH_CARDINALITY", "ERROR",
                            path + ".auto_create.order_by",
                            "禁止使用高基数 zenvis_id 作为排序键。",
                            "改用业务时间或低基数业务字段。"));
                }
            }
        }
        JsonNode ttl = autoCreate.get("ttl");
        if (ttl == null || ttl.isNull()) {
            return;
        }
        if (!ttl.isObject()) {
            diagnostics.add(new Diagnostic(
                    "META_TTL_INVALID", "ERROR", path + ".auto_create.ttl",
                    "auto_create.ttl 必须是对象。", "包含 column、expire_after、unit。"));
            return;
        }
        String ttlColumn = requiredText(ttl, "column", path + ".auto_create.ttl", diagnostics);
        JsonNode expireNode = ttl.get("expire_after");
        if (expireNode == null || !expireNode.isNumber() || expireNode.asLong() <= 0) {
            diagnostics.add(new Diagnostic(
                    "META_TTL_EXPIRE_INVALID", "ERROR", path + ".auto_create.ttl.expire_after",
                    "TTL expire_after 必须是大于 0 的整数。", "填写正整数有效期。"));
        }
        String unit = requiredText(ttl, "unit", path + ".auto_create.ttl", diagnostics);
        if (StringUtils.hasText(unit)
                && !Set.of("HOUR", "DAY", "WEEK", "MONTH", "YEAR").contains(unit)) {
            diagnostics.add(new Diagnostic(
                    "META_TTL_UNIT_INVALID", "ERROR", path + ".auto_create.ttl.unit",
                    "TTL unit 不支持：" + unit,
                    "使用 HOUR、DAY、WEEK、MONTH 或 YEAR。"));
        }
        if (StringUtils.hasText(ttlColumn)) {
            JsonNode attribute = findAttribute(attributes, entity, ttlColumn);
            if (attribute == null) {
                diagnostics.add(new Diagnostic(
                        "META_TTL_COLUMN_MISSING", "ERROR", path + ".auto_create.ttl.column",
                        "TTL 列不存在：" + ttlColumn, "引用当前实体中的字段物理列名。"));
            } else if (!isNonNullableTemporalType(text(attribute.get("column_type")))) {
                diagnostics.add(new Diagnostic(
                        "META_TTL_COLUMN_TYPE_INVALID", "ERROR",
                        path + ".auto_create.ttl.column",
                        "TTL 列必须是非 Nullable 的 Date/DateTime 类型：" + ttlColumn,
                        "改用 DateTime64(3) 等非 Nullable 时间字段。"));
            }
        }
    }

    private JsonNode findAttribute(JsonNode attributes,
                                   JsonNode entity,
                                   String columnName) {
        if (attributes == null || !attributes.isArray()) {
            return null;
        }
        String entityName = text(entity.get("name"));
        for (JsonNode attribute : attributes) {
            if (entityName.equals(text(attribute.get("entity")))
                    && columnName.equals(text(attribute.get("column_name")))) {
                return attribute;
            }
        }
        return null;
    }

    private void validateAttributeOperators(JsonNode attribute,
                                            String path,
                                            Set<String> operatorNames,
                                            List<Diagnostic> diagnostics) {
        JsonNode list = attribute.get("operators");
        if (list == null || !list.isArray()) {
            diagnostics.add(new Diagnostic(
                    "META_OPERATORS_REQUIRED", "ERROR", path + ".operators",
                    "字段 operators 必须是数组。", "至少配置一个当前类型支持的操作符。"));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : list) {
            String name = text(item);
            if (!StringUtils.hasText(name)) {
                diagnostics.add(new Diagnostic(
                        "META_OPERATOR_EMPTY", "ERROR", path + ".operators",
                        "operators 不能包含空值。", "填写平台操作符名。"));
                continue;
            }
            if (!seen.add(name)) {
                diagnostics.add(new Diagnostic(
                        "META_DUPLICATE_ATTRIBUTE_OPERATOR", "ERROR", path + ".operators",
                        "字段重复配置操作符：" + name, "每个操作符只保留一次。"));
            }
            if (!operatorNames.contains(name)) {
                diagnostics.add(new Diagnostic(
                        "META_UNKNOWN_OPERATOR", "ERROR", path + ".operators",
                        "引用了未定义的操作符：" + name,
                        "在顶层 operator 数组中定义该操作符，或改用平台默认操作符。"));
            }
        }
    }

    private void validateLinkCondition(JsonNode attribute,
                                       String path,
                                       String entity,
                                       JsonNode attributes,
                                       List<Diagnostic> diagnostics) {
        JsonNode condition = attribute.get("link_condition");
        if (condition == null || condition.isNull()) {
            return;
        }
        JsonNode template = attribute.get("link_template");
        if (template == null || !template.isTextual()
                || !StringUtils.hasText(template.asText())) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_TEMPLATE_REQUIRED", "ERROR", path + ".link_condition",
                    "link_condition 必须与 link_template 配合使用。",
                    "补齐 link_template 或删除 link_condition。"));
        }
        if (!condition.isObject()) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_CONDITION_INVALID", "ERROR", path + ".link_condition",
                    "link_condition 必须是对象。", "包含 field、operator、value。"));
            return;
        }
        String field = requiredText(condition, "field", path + ".link_condition", diagnostics);
        String operator = requiredText(condition, "operator", path + ".link_condition", diagnostics);
        if (StringUtils.hasText(field) && !hasAttribute(attributes, entity, field)) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_CONDITION_FIELD_INVALID", "ERROR",
                    path + ".link_condition.field",
                    "link_condition.field 引用了当前实体不存在的字段：" + field,
                    "改为当前实体的逻辑字段名 name，不要使用物理列名。"));
        }
        if (StringUtils.hasText(operator)
                && !LINK_CONDITION_OPERATORS.contains(operator)) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_OPERATOR_INVALID", "ERROR", path + ".link_condition.operator",
                    "link_condition.operator 不支持：" + operator,
                    "使用 equal、notequal、greatthan、greatequalthan、lessthan、lessequalthan、isnull、isnotnull。"));
        }
        boolean valueless = LINK_CONDITION_VALUELESS_OPERATORS.contains(operator);
        boolean hasValue = condition.hasNonNull("value")
                && StringUtils.hasText(text(condition.get("value")));
        if (valueless && hasValue) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_VALUE_NOT_ALLOWED", "ERROR", path + ".link_condition.value",
                    "操作符 " + operator + " 不需要 value。", "删除 value。"));
        } else if (StringUtils.hasText(operator) && !valueless && !hasValue) {
            diagnostics.add(new Diagnostic(
                    "META_LINK_VALUE_REQUIRED", "ERROR", path + ".link_condition.value",
                    "操作符 " + operator + " 必须设置 value。", "填写比较值。"));
        }
    }

    private boolean hasAttribute(JsonNode attributes, String entity, String field) {
        if (attributes == null || !attributes.isArray()) {
            return false;
        }
        for (JsonNode attribute : attributes) {
            if (entity.equals(text(attribute.get("entity")))
                    && field.equals(text(attribute.get("name")))) {
                return true;
            }
        }
        return false;
    }

    private void validateWithPlatform(String fileName,
                                      ObjectNode root,
                                      List<Diagnostic> diagnostics) {
        if (metaDataService == null) {
            diagnostics.add(new Diagnostic(
                    "META_PLATFORM_CHECK_UNAVAILABLE", "WARNING", "$",
                    "平台语义校验服务不可用，仅完成确定性结构校验。",
                    "检查 MetaDataService 配置。"));
            return;
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("zenvis-meta-validate-");
            copyExistingMeta(tempDir, fileName);
            String candidateName = StringUtils.hasText(fileName)
                    && SAFE_FILE_NAME.matcher(fileName).matches()
                    ? fileName : "candidate.json";
            Path candidate = tempDir.resolve(candidateName);
            Files.writeString(candidate, root.toPrettyString(), StandardCharsets.UTF_8);
            List<Path> files;
            try (Stream<Path> stream = Files.walk(tempDir)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .sorted()
                        .toList();
            }
            metaDataService.validateMetaDataFiles(files);
        } catch (Exception e) {
            diagnostics.add(new Diagnostic(
                    "META_PLATFORM_INVALID", "ERROR", "$",
                    "平台 Meta 加载校验失败：" + rootMessage(e),
                    "根据错误定位实体、字段或跨文件冲突后重新生成。"));
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private void validateCrossFileConflicts(String fileName,
                                            ObjectNode candidate,
                                            List<Diagnostic> diagnostics) {
        Path metaPath = existingMetaPath();
        if (metaPath == null) {
            return;
        }
        Set<String> candidateEntities = new LinkedHashSet<>();
        for (JsonNode entity : candidate.get("entity")) {
            String name = text(entity.get("name"));
            if (StringUtils.hasText(name)) {
                candidateEntities.add(name);
            }
        }
        Set<String> candidateTables = new LinkedHashSet<>();
        for (JsonNode entity : candidate.get("entity")) {
            String table = text(entity.get("table_name"));
            if (StringUtils.hasText(table)) {
                candidateTables.add(table);
            }
        }
        try (Stream<Path> stream = Files.walk(metaPath)) {
            for (Path source : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !StringUtils.hasText(fileName)
                            || !fileName.equals(path.getFileName().toString()))
                    .toList()) {
                JsonNode existing;
                try {
                    existing = JacksonConfig.OBJECT_MAPPER.readTree(source.toFile());
                } catch (Exception ignored) {
                    continue;
                }
                JsonNode entities = existing == null ? null : existing.get("entity");
                if (entities == null || !entities.isArray()) {
                    continue;
                }
                for (int i = 0; i < entities.size(); i++) {
                    JsonNode entity = entities.get(i);
                    String name = text(entity.get("name"));
                    String table = text(entity.get("table_name"));
                    if (candidateEntities.contains(name)) {
                        diagnostics.add(new Diagnostic(
                                "META_CROSS_FILE_ENTITY_CONFLICT", "ERROR", "$.entity",
                                "实体 " + name + " 已在 " + source.getFileName()
                                        + " 中定义。",
                                "复用已有实体或在现有文件中覆盖同名实体。"));
                    }
                    if (candidateTables.contains(table)) {
                        diagnostics.add(new Diagnostic(
                                "META_CROSS_FILE_TABLE_CONFLICT", "ERROR", "$.entity",
                                "目标表 " + table + " 已被其他实体占用："
                                        + source.getFileName() + "。",
                                "为当前实体选择唯一目标表，或覆盖已有实体定义。"));
                    }
                }
            }
        } catch (IOException e) {
            log.debug("检查跨文件 Meta 冲突失败: {}", e.getMessage());
        }
    }

    private Path existingMetaPath() {
        try {
            if (configService == null) {
                return null;
            }
            Path path = Paths.get(configService.configPath("meta"));
            return Files.isDirectory(path) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void copyExistingMeta(Path target, String excludedFileName) {
        Path metaPath = existingMetaPath();
        if (metaPath == null) {
            log.debug("无法读取 Meta 配置目录，跳过跨文件合并校验");
            return;
        }
        try (Stream<Path> stream = Files.walk(metaPath)) {
            for (Path source : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                if (StringUtils.hasText(excludedFileName)
                        && excludedFileName.equals(source.getFileName().toString())) {
                    continue;
                }
                Path relative = metaPath.relativize(source);
                Path destination = target.resolve(relative.toString()).normalize();
                if (!destination.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination);
            }
        } catch (IOException e) {
            log.warn("复制现有 Meta 进行校验失败: {}", e.getMessage());
        }
    }

    private void repairAliases(ObjectNode node,
                               String path,
                               Map<String, String> aliases,
                               List<Repair> repairs) {
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (!node.has(alias.getKey())) {
                continue;
            }
            JsonNode value = node.get(alias.getKey());
            if (!node.has(alias.getValue())) {
                node.set(alias.getValue(), value);
                node.remove(alias.getKey());
                repairs.add(new Repair(path + "." + alias.getKey(),
                        alias.getKey(), alias.getValue(),
                        "将常见驼峰字段名规范为平台 snake_case"));
            } else {
                node.remove(alias.getKey());
                repairs.add(new Repair(path + "." + alias.getKey(),
                        alias.getKey(), alias.getValue(),
                        "删除与平台字段重复的旧别名"));
            }
        }
    }

    private void ensureArray(ObjectNode root,
                             String field,
                             List<Diagnostic> diagnostics,
                             List<Repair> repairs) {
        if (!root.has(field)) {
            root.putArray(field);
            repairs.add(new Repair("$." + field, "", "[]",
                    "补齐 Meta 顶层数组结构"));
            diagnostics.add(new Diagnostic(
                    "META_TOP_LEVEL_FIELD_MISSING", "ERROR", "$." + field,
                    "缺少必需数组字段 " + field + "。",
                    "补齐 entity、attribute、operator 三个数组。"));
        } else if (!root.get(field).isArray()) {
            String before = root.has(field) ? root.get(field).toString() : "";
            root.putArray(field);
            repairs.add(new Repair("$." + field, before, "[]",
                    "补齐 Meta 顶层数组结构"));
            diagnostics.add(new Diagnostic(
                    "META_TOP_LEVEL_FIELD_INVALID", "ERROR", "$." + field,
                    "字段 " + field + " 必须是数组。",
                    "改为对象数组，不要输出字符串或单个对象。"));
        }
    }

    private void ensureArrayType(ObjectNode root,
                                 String field,
                                 String path,
                                 List<Diagnostic> diagnostics) {
        if (!root.get(field).isArray()) {
            diagnostics.add(new Diagnostic(
                    "META_ARRAY_REQUIRED", "ERROR", path,
                    path + " 必须是数组。", "将该字段改为对象数组。"));
        }
    }

    private String requiredText(JsonNode node,
                                String field,
                                String path,
                                List<Diagnostic> diagnostics) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            diagnostics.add(new Diagnostic(
                    "META_REQUIRED_FIELD", "ERROR", path + "." + field,
                    path + "." + field + " 不能为空。",
                    "补齐该必填字段。"));
            return "";
        }
        return value.asText().trim();
    }

    private void validateNonNegativeId(JsonNode node,
                                       String path,
                                       List<Diagnostic> diagnostics) {
        JsonNode value = node.get("id");
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            return;
        }
        if (!value.canConvertToInt() || value.asInt() < 0) {
            diagnostics.add(new Diagnostic(
                    "META_ID_INVALID", "ERROR", path + ".id",
                    "id 必须是 0 到 " + Integer.MAX_VALUE + " 之间的整数。",
                    "省略 id 或使用非负整数。"));
        }
    }

    private List<String> defaultOperators(String columnType) {
        String type = unwrapType((StringUtils.hasText(columnType) ? columnType : "")
                .toLowerCase(Locale.ROOT).replace(" ", ""));
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
        if (type.contains("string") || type.isEmpty()) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in", "match");
        }
        return List.of("equal", "notequal", "isnull", "isnotnull", "in");
    }

    private String inferDisplayType(String columnType) {
        String type = unwrapType((StringUtils.hasText(columnType) ? columnType : "")
                .toLowerCase(Locale.ROOT).replace(" ", ""));
        if (type.startsWith("array")) {
            return "array";
        }
        if (type.equals("json") || type.equals("object")) {
            return "json";
        }
        return "";
    }

    private String inferSearchType(String columnType) {
        String type = unwrapType((StringUtils.hasText(columnType) ? columnType : "")
                .toLowerCase(Locale.ROOT).replace(" ", ""));
        if (type.startsWith("date")) {
            return "datetime";
        }
        if (NUMBER_TYPE.matcher(type).matches()) {
            return "number";
        }
        return "";
    }

    private String unwrapType(String type) {
        String current = type;
        while (current.endsWith(")")
                && (current.startsWith("nullable(")
                || current.startsWith("lowcardinality("))) {
            current = current.substring(current.indexOf('(') + 1,
                    current.length() - 1);
        }
        return current;
    }

    private boolean isSupportedColumnType(String columnType) {
        if (!StringUtils.hasText(columnType)
                || columnType.contains(";") || columnType.contains("--")
                || columnType.contains("/*") || columnType.contains("*/")
                || columnType.contains("\n") || columnType.contains("\r")) {
            return false;
        }
        return isSupportedTypeExpression(columnType.trim());
    }

    private boolean isSupportedTypeExpression(String value) {
        String normalized = value.replaceAll("\\s+", "");
        String lower = normalized.toLowerCase(Locale.ROOT);
        int open = lower.indexOf('(');
        String base = open < 0 ? lower : lower.substring(0, open);
        if (Set.of("nullable", "lowcardinality", "array").contains(base)) {
            return normalized.endsWith(")") && isSupportedTypeExpression(
                    normalized.substring(open + 1, normalized.length() - 1));
        }
        if (Set.of("datetime64", "fixedstring", "decimal", "decimal32", "decimal64",
                "decimal128", "decimal256", "enum8", "enum16", "datetime").contains(base)) {
            return lower.startsWith(base + "(") && normalized.endsWith(")");
        }
        if (Set.of("map", "tuple", "nested").contains(base)) {
            return normalized.endsWith(")") && SAFE_TYPE_TOKEN.matcher(normalized).matches();
        }
        return open < 0 && COLUMN_BASE_TYPES.contains(base);
    }

    private boolean isNonNullableTemporalType(String columnType) {
        String normalized = (columnType == null ? "" : columnType)
                .replace(" ", "").toLowerCase(Locale.ROOT);
        return !normalized.startsWith("nullable")
                && TEMPORAL_TYPE.matcher(normalized).matches();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Collections.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    log.debug("清理 Meta 校验临时文件失败: {}", item);
                }
            });
        } catch (IOException e) {
            log.debug("清理 Meta 校验临时目录失败: {}", path);
        }
    }

    public static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.hasText(text)
                            ? text.getBytes(StandardCharsets.UTF_8)
                            : new byte[0]);
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText().trim();
    }

    public record Diagnostic(String code,
                             String severity,
                             String path,
                             String message,
                             String repairHint) {
    }

    public record Repair(String path,
                         String before,
                         String after,
                         String reason) {
    }

    public record ValidationResult(boolean passed,
                                   String normalizedText,
                                   String candidateDigest,
                                   List<Diagnostic> diagnostics,
                                   List<Repair> repairs,
                                   List<String> errors,
                                   List<String> warnings) {

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("passed", passed);
            result.put("candidateDigest", candidateDigest);
            result.put("diagnostics", diagnostics);
            result.put("repairs", repairs);
            result.put("errors", errors);
            result.put("warnings", warnings);
            return result;
        }
    }
}
