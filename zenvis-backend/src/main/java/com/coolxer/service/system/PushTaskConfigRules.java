package com.coolxer.service.system;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据推送配置的平台静态规则：拓扑、Meta 契约、密钥和测试门槛。
 */
@Service
public class PushTaskConfigRules {

    private static final Pattern SOURCE_FIELD_ASSIGNMENT = Pattern.compile(
            "(?m)\\.([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern RESERVED_FIELD_MAPPING = Pattern.compile(
            "(?m)(?:^|[\\.\"'])zenvis_(?:id|insert_time)\\s*[\"']?\\s*[:=]");
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)^(password|passwd|token|secret|api[_-]?key|access[_-]?key)$");
    private static final Pattern ENV_EXPRESSION = Pattern.compile(
            "^\\$\\{[A-Za-z_][A-Za-z0-9_]*(?::-([^}]*))?}$");
    private static final Pattern NORMAL_TEST = Pattern.compile(
            "(?i)(正常|合法|有效|成功|valid|success|normal)");
    private static final Pattern ERROR_TEST = Pattern.compile(
            "(?i)(异常|非法|错误|失败|死信|invalid|error|failed|dead|drop)");

    /**
     * 执行静态规则并返回是否需要 Vector 单元测试。
     *
     * @param config 配置文本
     * @param requestedFormat 可选格式
     * @param metaContent 可选 Meta 原文
     * @return 静态校验结果
     */
    public StaticValidation validate(String config,
                                     String requestedFormat,
                                     String metaContent) {
        List<PushTaskDiagnosticVo> diagnostics = new ArrayList<>();
        PushTaskConfigFormatDetector.Detection detection =
                PushTaskConfigFormatDetector.detect(requestedFormat, config);
        String format = detection.format();
        if (!StringUtils.hasText(config)) {
            diagnostics.add(diagnostic("PUSH_CONFIG_EMPTY", "ERROR", "STATIC",
                    "$", "推送配置不能为空。", "生成完整 YAML 配置后重新校验。"));
            return new StaticValidation(format, false, diagnostics);
        }
        if (detection.unsupported()) {
            diagnostics.add(diagnostic("PUSH_CONFIG_FORMAT_UNSUPPORTED", "ERROR", "STATIC",
                    "$format", "不支持的配置格式：" + requestedFormat,
                    "显式使用 yaml、toml 或 json；新任务默认使用 yaml。"));
            return new StaticValidation(format, false, diagnostics);
        }
        if (detection.ambiguous()) {
            diagnostics.add(diagnostic("PUSH_CONFIG_FORMAT_AMBIGUOUS", "ERROR", "STATIC",
                    "$", "无法可靠识别配置格式。",
                    "移除配置正文前的非注释文本，或显式传入 yaml、toml 或 json。"));
            return new StaticValidation(format, false, diagnostics);
        }

        Map<String, Object> root;
        try {
            root = parse(config, format);
        } catch (RuntimeException e) {
            diagnostics.add(diagnostic("PUSH_CONFIG_SYNTAX", "ERROR", "STATIC",
                    "$", "配置解析失败：" + safeMessage(e),
                    "输出单个完整配置对象，不要包含 Markdown 代码围栏或注释外文本。"));
            return new StaticValidation(format, false, diagnostics);
        }

        Map<String, Map<String, Object>> sources = componentMap(
                root, "sources", diagnostics);
        Map<String, Map<String, Object>> transforms = componentMap(
                root, "transforms", diagnostics);
        Map<String, Map<String, Object>> sinks = componentMap(
                root, "sinks", diagnostics);
        if (sources.isEmpty()) {
            diagnostics.add(diagnostic("PUSH_SOURCE_REQUIRED", "ERROR", "STATIC",
                    "$sources", "配置必须至少包含一个 source。",
                    "添加合法 sources 映射。"));
        }
        if (sinks.isEmpty()) {
            diagnostics.add(diagnostic("PUSH_SINK_REQUIRED", "ERROR", "STATIC",
                    "$sinks", "配置必须至少包含一个 sink。",
                    "添加合法 sinks 映射。"));
        }
        validateInputs(sources, transforms, sinks, diagnostics);
        validateHardCodedSecrets(root, diagnostics);
        validateReservedFields(config, diagnostics);

        boolean clickHousePresent = sinks.values().stream()
                .anyMatch(item -> "clickhouse".equalsIgnoreCase(text(item.get("type"))));
        boolean testsRequired = !transforms.isEmpty() || clickHousePresent;
        MetaFields metaFields = validateMetaTargets(sinks, metaContent, diagnostics);
        validateTransformFields(transforms, metaFields, diagnostics);
        validateTests(root, config, testsRequired, metaFields, diagnostics);

        return new StaticValidation(format, testsRequired, diagnostics);
    }

    private Map<String, Object> parse(String config, String format) {
        if ("json".equals(format)) {
            try {
                return JacksonConfig.OBJECT_MAPPER.readValue(
                        config, new TypeReference<LinkedHashMap<String, Object>>() {
                        });
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        if ("toml".equals(format)) {
            TomlParseResult parsed = Toml.parse(config);
            if (parsed.hasErrors()) {
                throw new IllegalArgumentException(parsed.errors().toString());
            }
            return (Map<String, Object>) normalizeValue(parsed);
        }
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(64);
        options.setCodePointLimit(4 * 1024 * 1024);
        Object loaded = new Yaml(new SafeConstructor(options)).load(config);
        if (loaded == null) {
            throw new IllegalArgumentException("YAML 内容为空");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("配置顶层必须是映射");
        }
        return normalizeMap(map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalizeValue(value)));
        return result;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof TomlTable table) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : table.keySet()) {
                result.put(key, normalizeValue(table.get(key)));
            }
            return result;
        }
        if (value instanceof TomlArray array) {
            List<Object> result = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                result.add(normalizeValue(array.get(index)));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizeValue).toList();
        }
        return value;
    }

    private Map<String, Map<String, Object>> componentMap(
            Map<String, Object> root,
            String key,
            List<PushTaskDiagnosticVo> diagnostics) {
        Object value = root.get(key);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            diagnostics.add(diagnostic("PUSH_COMPONENT_MAP_REQUIRED", "ERROR", "STATIC",
                    "$" + key, key + " 必须是对象映射，不能是数组。",
                    "使用以组件 ID 为键的 YAML 映射，例如 `sinks: { ch: { type: clickhouse } }`。"));
            return Map.of();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        raw.forEach((name, item) -> {
            String componentName = String.valueOf(name);
            if (!(item instanceof Map<?, ?> component)) {
                diagnostics.add(diagnostic("PUSH_COMPONENT_OBJECT_REQUIRED", "ERROR", "STATIC",
                        "$" + key + "." + componentName, "组件定义必须是对象。",
                        "补全 type、inputs 和组件字段。"));
                return;
            }
            result.put(componentName, normalizeMap(component));
        });
        return result;
    }

    private void validateInputs(
            Map<String, Map<String, Object>> sources,
            Map<String, Map<String, Object>> transforms,
            Map<String, Map<String, Object>> sinks,
            List<PushTaskDiagnosticVo> diagnostics) {
        Set<String> upstream = new LinkedHashSet<>(sources.keySet());
        upstream.addAll(transforms.keySet());
        Map<String, List<String>> graph = new LinkedHashMap<>();
        validateInputGroup("transforms", transforms, upstream, graph, diagnostics);
        validateInputGroup("sinks", sinks, upstream, graph, diagnostics);
        detectCycles(graph, diagnostics);
    }

    private void validateInputGroup(
            String group,
            Map<String, Map<String, Object>> components,
            Set<String> upstream,
            Map<String, List<String>> graph,
            List<PushTaskDiagnosticVo> diagnostics) {
        for (Map.Entry<String, Map<String, Object>> entry : components.entrySet()) {
            String name = entry.getKey();
            List<String> inputs = stringList(entry.getValue().get("inputs"));
            if (inputs.isEmpty()) {
                diagnostics.add(diagnostic("PUSH_INPUT_REQUIRED", "ERROR", "STATIC",
                        "$" + group + "." + name + ".inputs",
                        "组件 " + name + " 必须声明至少一个上游 inputs。",
                        "引用已存在的 source 或 transform ID。"));
                continue;
            }
            List<String> edges = new ArrayList<>();
            for (String input : inputs) {
                if (input.equals(name)) {
                    diagnostics.add(diagnostic("PUSH_INPUT_SELF_REFERENCE", "ERROR", "STATIC",
                            "$" + group + "." + name + ".inputs",
                            "组件不能引用自身。", "改为引用真实上游组件。"));
                } else if (!upstream.contains(input)) {
                    diagnostics.add(diagnostic("PUSH_INPUT_UNKNOWN", "ERROR", "STATIC",
                            "$" + group + "." + name + ".inputs",
                            "inputs 引用了不存在的上游：" + input,
                            "只引用同一配置中已存在的 source 或 transform。"));
                } else if (!"sources".equals(group) && components.containsKey(input)) {
                    edges.add(input);
                }
            }
            graph.put(group + "." + name, edges);
        }
    }

    private void detectCycles(Map<String, List<String>> graph,
                              List<PushTaskDiagnosticVo> diagnostics) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : graph.keySet()) {
            if (hasCycle(node, graph, visiting, visited)) {
                diagnostics.add(diagnostic("PUSH_INPUT_CYCLE", "ERROR", "STATIC",
                        "$" + node + ".inputs",
                        "inputs 拓扑存在循环引用。", "移除循环边并保持有向无环拓扑。"));
                return;
            }
        }
    }

    private boolean hasCycle(String node,
                             Map<String, List<String>> graph,
                             Set<String> visiting,
                             Set<String> visited) {
        if (visiting.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visiting.add(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            String target = graph.containsKey("transforms." + next)
                    ? "transforms." + next
                    : graph.containsKey("sinks." + next) ? "sinks." + next : "";
            if (!target.isEmpty() && hasCycle(target, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private MetaFields validateMetaTargets(
            Map<String, Map<String, Object>> sinks,
            String metaContent,
            List<PushTaskDiagnosticVo> diagnostics) {
        MetaFields result = new MetaFields();
        List<Map<String, Object>> clickHouseSinks = sinks.entrySet().stream()
                .filter(entry -> "clickhouse".equalsIgnoreCase(
                        text(entry.getValue().get("type"))))
                .map(entry -> {
                    Map<String, Object> sink = new LinkedHashMap<>(entry.getValue());
                    sink.put("__name", entry.getKey());
                    return sink;
                })
                .toList();
        if (clickHouseSinks.isEmpty()) {
            return result;
        }
        if (!StringUtils.hasText(metaContent)) {
            diagnostics.add(diagnostic("PUSH_META_REQUIRED", "ERROR", "META",
                    "$sinks", "ClickHouse 写入必须绑定已发布 Meta 原文。",
                    "先调用 config_read 获取真实 Meta，再提交校验。"));
            return result;
        }

        Map<String, MetaEntity> entities;
        try {
            entities = readMetaEntities(metaContent);
        } catch (RuntimeException e) {
            diagnostics.add(diagnostic("PUSH_META_INVALID", "ERROR", "META",
                    "$meta", "已发布 Meta 无法解析：" + safeMessage(e),
                    "重新读取已发布 Meta，不得编造实体或字段。"));
            return result;
        }

        for (Map<String, Object> sink : clickHouseSinks) {
            String sinkPath = "$sinks." + sink.get("__name");
            String database = resolvedValue(sink.get("database"));
            String table = resolvedValue(sink.get("table"));
            if (!"zenvis".equals(database)) {
                diagnostics.add(diagnostic("PUSH_CLICKHOUSE_DATABASE_INVALID", "ERROR", "META",
                        sinkPath + ".database", "ClickHouse database 必须为 zenvis。",
                        "设置 database: zenvis，不要编造其他数据库。"));
            }
            if (!StringUtils.hasText(table)) {
                diagnostics.add(diagnostic("PUSH_CLICKHOUSE_TABLE_UNKNOWN", "ERROR", "META",
                        sinkPath + ".table", "ClickHouse table 无法从运行环境解析。",
                        "直接使用已发布 Meta 的实体 table_name。"));
            } else {
                MetaEntity entity = entities.get(normalizeTable(table));
                if (entity == null) {
                    diagnostics.add(diagnostic("PUSH_META_TABLE_MISMATCH", "ERROR", "META",
                            sinkPath + ".table",
                            "目标表 " + table + " 不存在于已发布 Meta。",
                            "使用 Meta 中真实实体的 table_name。"));
                } else {
                    result.targetFields.addAll(entity.fields);
                    result.tableNames.add(normalizeTable(table));
                }
            }
            if (sink.containsKey("batch_size")) {
                diagnostics.add(diagnostic("PUSH_CLICKHOUSE_UNKNOWN_FIELD", "ERROR", "STATIC",
                        sinkPath + ".batch_size",
                        "Vector ClickHouse sink 不支持 batch_size。",
                        "删除 batch_size，使用 batch.max_bytes 和 batch.timeout_secs。"));
            }
        }
        return result;
    }

    private Map<String, MetaEntity> readMetaEntities(String metaContent) {
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(metaContent);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Meta 顶层必须是对象");
            }
            Map<String, MetaEntity> result = new LinkedHashMap<>();
            JsonNode entityNodes = root.path("entity");
            if (entityNodes.isArray()) {
                for (JsonNode entity : entityNodes) {
                    String name = entity.path("name").asText("");
                    String tableName = entity.path("table_name").asText("");
                    if (!StringUtils.hasText(name) || !StringUtils.hasText(tableName)) {
                        continue;
                    }
                    result.put(normalizeTable(tableName), new MetaEntity(name, tableName));
                }
            }
            JsonNode attributeNodes = root.path("attribute");
            if (attributeNodes.isArray()) {
                for (JsonNode attribute : attributeNodes) {
                    String entityName = attribute.path("entity").asText("");
                    String columnName = firstNonBlank(
                            attribute.path("column_name").asText(""),
                            attribute.path("name").asText(""));
                    if (StringUtils.hasText(columnName)) {
                        result.values().stream()
                                .filter(entity -> entity.name.equals(entityName))
                                .forEach(entity -> entity.fields.add(columnName));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private void validateTransformFields(
            Map<String, Map<String, Object>> transforms,
            MetaFields metaFields,
            List<PushTaskDiagnosticVo> diagnostics) {
        if (metaFields.targetFields.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> entry : transforms.entrySet()) {
            if (!"remap".equalsIgnoreCase(text(entry.getValue().get("type")))) {
                continue;
            }
            String source = text(entry.getValue().get("source"));
            Matcher matcher = SOURCE_FIELD_ASSIGNMENT.matcher(source);
            Set<String> reported = new HashSet<>();
            while (matcher.find()) {
                String field = matcher.group(1);
                if ("metadata".equals(field) || field.startsWith("zenvis_")) {
                    continue;
                }
                if (!metaFields.targetFields.contains(field) && reported.add(field)) {
                    diagnostics.add(diagnostic("PUSH_META_FIELD_UNKNOWN", "ERROR", "META",
                            "$transforms." + entry.getKey() + ".source." + field,
                            "Transform 输出了 Meta 中不存在的业务字段：" + field,
                            "只输出已发布 Attribute 的 column_name，删除或改名未知字段。"));
                }
            }
        }
    }

    private void validateTests(
            Map<String, Object> root,
            String config,
            boolean testsRequired,
            MetaFields metaFields,
            List<PushTaskDiagnosticVo> diagnostics) {
        List<Map<String, Object>> tests = testCases(root.get("tests"));
        if (testsRequired && tests.isEmpty()) {
            diagnostics.add(diagnostic("PUSH_TESTS_REQUIRED", "ERROR", "STATIC",
                    "$tests", "包含 Transform 或 ClickHouse 写入的配置必须提供单元测试。",
                    "至少增加一个合法样例测试和一个异常/死信测试。"));
            return;
        }
        if (tests.isEmpty()) {
            return;
        }
        int normal = 0;
        int error = 0;
        Set<String> coveredFields = new HashSet<>();
        for (Map<String, Object> test : tests) {
            String serialized = safeJson(test);
            String name = text(test.get("name"));
            if (ERROR_TEST.matcher(name).find()
                    || serialized.contains(".dropped")
                    || serialized.contains("drop_on_error")) {
                error++;
            } else if (NORMAL_TEST.matcher(name).find() || !serialized.isEmpty()) {
                normal++;
            }
            for (String field : metaFields.targetFields) {
                if (StringUtils.hasText(field) && containsField(serialized, field)) {
                    coveredFields.add(field);
                }
            }
        }
        if (testsRequired && normal == 0) {
            diagnostics.add(diagnostic("PUSH_TEST_VALID_CASE_MISSING", "ERROR", "STATIC",
                    "$tests", "缺少至少一个合法样例测试。",
                    "增加正常输入并断言目标字段的 Vector 测试。"));
        }
        if (testsRequired && error == 0) {
            diagnostics.add(diagnostic("PUSH_TEST_ERROR_CASE_MISSING", "ERROR", "STATIC",
                    "$tests", "缺少异常或死信测试。",
                    "增加非法输入并断言 dropped/错误路径的 Vector 测试。"));
        }
        if (!metaFields.targetFields.isEmpty() && coveredFields.isEmpty()) {
            diagnostics.add(diagnostic("PUSH_TEST_TARGET_FIELDS_UNCOVERED", "ERROR", "META",
                    "$tests", "样例测试没有覆盖任何已发布 Meta 目标字段。",
                    "在测试输入和断言中使用目标实体真实 column_name。"));
        } else if (!metaFields.targetFields.isEmpty()
                && coveredFields.size() < metaFields.targetFields.size()) {
            diagnostics.add(diagnostic("PUSH_TEST_TARGET_FIELDS_PARTIAL", "WARNING", "META",
                    "$tests", "样例测试仅覆盖 "
                            + coveredFields.size() + "/" + metaFields.targetFields.size()
                            + " 个目标字段。",
                    "补充关键目标字段的输入和断言。"));
        }
    }

    private List<Map<String, Object>> testCases(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(normalizeMap(map));
            }
        }
        return result;
    }

    private boolean containsField(String content, String field) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(field)
                + "(?![A-Za-z0-9_])").matcher(content).find();
    }

    private void validateHardCodedSecrets(Map<String, Object> root,
                                          List<PushTaskDiagnosticVo> diagnostics) {
        findHardCodedSecrets(root, "$", diagnostics);
    }

    private void findHardCodedSecrets(Object value,
                                      String path,
                                      List<PushTaskDiagnosticVo> diagnostics) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = text(entry.getKey());
                String childPath = path + "." + key;
                Object child = entry.getValue();
                if (SECRET_KEY.matcher(key).matches()
                        && child != null
                        && !isSafeEnvironmentReference(child.toString().trim())) {
                    diagnostics.add(diagnostic("PUSH_HARD_CODED_SECRET", "ERROR", "STATIC",
                            childPath,
                            "配置疑似包含硬编码密钥。",
                            "改为从环境变量或密钥管理注入，不得在候选配置中写明文或非空默认值。"));
                }
                findHardCodedSecrets(child, childPath, diagnostics);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                findHardCodedSecrets(list.get(index), path + "[" + index + "]", diagnostics);
            }
        }
    }

    private boolean isSafeEnvironmentReference(String value) {
        if (!value.startsWith("${")) {
            return false;
        }
        Matcher matcher = ENV_EXPRESSION.matcher(value);
        return matcher.matches() && !StringUtils.hasText(matcher.group(1));
    }

    private void validateReservedFields(String config,
                                        List<PushTaskDiagnosticVo> diagnostics) {
        if (RESERVED_FIELD_MAPPING.matcher(config).find()) {
            diagnostics.add(diagnostic("PUSH_RESERVED_FIELD_MAPPING", "ERROR", "META",
                    "$", "业务映射不得写入 zenvis_id 或 zenvis_insert_time。",
                    "删除保留字段映射，由 ZenVis 数据层维护。"));
        }
    }

    private String resolvedValue(Object value) {
        String text = text(value);
        if (!text.startsWith("${")) {
            return text;
        }
        Matcher matcher = ENV_EXPRESSION.matcher(text);
        if (matcher.matches()) {
            return matcher.group(1) == null ? "" : matcher.group(1);
        }
        return "";
    }

    private String normalizeTable(String table) {
        String normalized = table == null ? "" : table.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::text).filter(StringUtils::hasText).toList();
        }
        String text = text(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private String safeJson(Object value) {
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String safeMessage(Throwable error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return "未知错误";
        }
        String message = error.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= 1_000 ? message : message.substring(0, 1_000) + "…";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private PushTaskDiagnosticVo diagnostic(String code,
                                            String severity,
                                            String source,
                                            String path,
                                            String message,
                                            String repairHint) {
        return PushTaskDiagnosticVo.builder()
                .code(code)
                .severity(severity)
                .source(source)
                .path(path)
                .message(message)
                .repairHint(repairHint)
                .build();
    }

    private static final class MetaEntity {
        private final String name;
        private final String tableName;
        private final Set<String> fields = new LinkedHashSet<>();

        private MetaEntity(String name, String tableName) {
            this.name = name;
            this.tableName = tableName;
        }
    }

    private static final class MetaFields {
        private final Set<String> targetFields = new LinkedHashSet<>();
        private final Set<String> tableNames = new LinkedHashSet<>();

        private MetaFields() {
        }
    }

    /**
     * 静态规则结果。
     *
     * @param format 识别出的格式
     * @param testsRequired 是否要求单元测试
     * @param diagnostics 诊断列表
     */
    public record StaticValidation(String format,
                                   boolean testsRequired,
                                   List<PushTaskDiagnosticVo> diagnostics) {
        public boolean passed() {
            return diagnostics.stream().noneMatch(
                    item -> "ERROR".equalsIgnoreCase(item.getSeverity()));
        }
    }
}
