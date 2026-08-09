package com.coolxer.service.retrieval.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class MetaDataServiceImpl implements MetaDataService {

    private static final Pattern LOGICAL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern PHYSICAL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern LINK_TEMPLATE_PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Map<String, String> DEFAULT_OPERATOR_LABEL_MAP = new LinkedHashMap<>();

    static {
        DEFAULT_OPERATOR_LABEL_MAP.put("equal", "等于");
        DEFAULT_OPERATOR_LABEL_MAP.put("notequal", "不等于");
        DEFAULT_OPERATOR_LABEL_MAP.put("isnull", "为空");
        DEFAULT_OPERATOR_LABEL_MAP.put("isnotnull", "不为空");
        DEFAULT_OPERATOR_LABEL_MAP.put("match", "模糊匹配");
        DEFAULT_OPERATOR_LABEL_MAP.put("greatthan", "大于");
        DEFAULT_OPERATOR_LABEL_MAP.put("greatequalthan", "大于等于");
        DEFAULT_OPERATOR_LABEL_MAP.put("lessthan", "小于");
        DEFAULT_OPERATOR_LABEL_MAP.put("lessequalthan", "小于等于");
        DEFAULT_OPERATOR_LABEL_MAP.put("between", "之间");
        DEFAULT_OPERATOR_LABEL_MAP.put("in", "包含");
    }

    @Autowired
    private CustomWebConfig customWebConfig;

    private final AtomicReference<MetadataSnapshot> snapshotRef =
            new AtomicReference<>(MetadataSnapshot.empty());

    @Override
    public MetaData loadMetaData() {
        String metadataPath = customWebConfig.getRetrievalMetaFilePath();
        try {
            LoadedMetadata loaded = readMetaData(metadataPath);
            supplementBuiltInAttributes(loaded.metaData(), metadataPath, loaded.sourceByObject());
            normalizeLinkTemplates(loaded.metaData());
            supplementOperators(loaded.metaData());
            MetadataSnapshot next = buildSnapshot(loaded.metaData(), metadataPath, loaded.sourceByObject());
            snapshotRef.set(next);
            log.info("retrieval metadata loaded, path={}, entities={}, attributes={}, operators={}",
                    metadataPath, next.entities().size(), next.attributes().size(), next.operatorsByName().size());
            return next.metaData();
        } catch (Exception ex) {
            MetadataSnapshot previous = snapshotRef.get();
            log.error("retrieval metadata reload failed, keeping previous snapshot, path={}, reason={}",
                    metadataPath, ex.getMessage(), ex);
            return previous.isEmpty() ? null : previous.metaData();
        }
    }

    @Override
    public MetaData validateMetaDataFiles(List<Path> metaFiles) {
        if (metaFiles == null || metaFiles.isEmpty()) {
            throw new IllegalArgumentException("Meta 文件不能为空");
        }
        try {
            List<Path> normalizedFiles = metaFiles.stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
            LoadedMetadata loaded = readMetaDataFiles(normalizedFiles);
            String source = normalizedFiles.toString();
            supplementBuiltInAttributes(loaded.metaData(), source, loaded.sourceByObject());
            normalizeLinkTemplates(loaded.metaData());
            supplementOperators(loaded.metaData());
            return buildSnapshot(loaded.metaData(), source, loaded.sourceByObject()).metaData();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取 Meta 文件失败", e);
        }
    }

    private LoadedMetadata readMetaData(String metadataPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(metadataPath))) {
            List<Path> jsonFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            return readMetaDataFiles(jsonFiles);
        }
    }

    private LoadedMetadata readMetaDataFiles(List<Path> jsonFiles) throws IOException {
        MetaData merged = new MetaData();
        Map<Object, String> sourceByObject = new IdentityHashMap<>();
        for (Path path : jsonFiles) {
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".json")) {
                throw new IllegalArgumentException("Meta 文件不存在或不是 JSON: " + path);
            }
            String json = FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8);
            MetaData part;
            try {
                part = JacksonUtil.toObject(json, MetaData.class);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("元数据文件解析失败: " + path, ex);
            }
            if (part == null) {
                throw new IllegalArgumentException("元数据文件内容为空: " + path);
            }
            appendWithSource(merged.getEntity(), part.getEntity(), path, sourceByObject);
            appendWithSource(merged.getAttribute(), part.getAttribute(), path, sourceByObject);
            appendWithSource(merged.getOperator(), part.getOperator(), path, sourceByObject);
        }
        return new LoadedMetadata(merged, sourceByObject);
    }

    private <T> void appendWithSource(List<T> target, List<T> values, Path path, Map<Object, String> sourceByObject) {
        for (T value : safe(values)) {
            target.add(value);
            if (value != null) {
                sourceByObject.put(value, path.toString());
            }
        }
    }

    private MetadataSnapshot buildSnapshot(MetaData metaData, String sourcePath, Map<Object, String> sourceByObject) {
        if (metaData == null) {
            throw new IllegalArgumentException("元数据不能为空: " + sourcePath);
        }
        Map<String, DataEntity> entitiesByName = new LinkedHashMap<>();
        Map<Integer, DataEntity> entitiesById = new LinkedHashMap<>();
        for (DataEntity entity : safe(metaData.getEntity())) {
            String itemSource = sourceOf(entity, sourceByObject, sourcePath);
            requireIdentifier(entity == null ? null : entity.getName(), "实体name", itemSource, LOGICAL_IDENTIFIER);
            requireIdentifier(entity.getTableName(), "实体table_name", itemSource, PHYSICAL_IDENTIFIER);
            putUnique(entitiesByName, entity.getName(), entity, "重复实体: " + entity.getName(), itemSource);
            if (entity.getId() > 0) {
                putUnique(entitiesById, entity.getId(), entity, "重复实体ID: " + entity.getId(), itemSource);
            }
        }
        if (entitiesByName.isEmpty()) {
            throw invalid(sourcePath, "未加载到任何检索实体");
        }

        Map<String, DataOperator> operatorsByName = new LinkedHashMap<>();
        for (DataOperator operator : safe(metaData.getOperator())) {
            String itemSource = sourceOf(operator, sourceByObject, sourcePath);
            requireIdentifier(operator == null ? null : operator.getName(), "操作符name", itemSource, LOGICAL_IDENTIFIER);
            operatorsByName.putIfAbsent(operator.getName(), operator);
        }

        Map<String, Map<String, DataAttribute>> attributesByEntity = new LinkedHashMap<>();
        Map<Integer, DataAttribute> attributesById = new LinkedHashMap<>();
        for (DataAttribute attribute : safe(metaData.getAttribute())) {
            String itemSource = sourceOf(attribute, sourceByObject, sourcePath);
            if (attribute == null || !entitiesByName.containsKey(attribute.getEntity())) {
                throw invalid(itemSource, "字段引用了不存在的实体: " + (attribute == null ? null : attribute.getEntity()));
            }
            requireIdentifier(attribute.getName(), "字段name", itemSource, LOGICAL_IDENTIFIER);
            requireIdentifier(attribute.getColumnName(), "字段column_name", itemSource, PHYSICAL_IDENTIFIER);
            Map<String, DataAttribute> entityAttributes =
                    attributesByEntity.computeIfAbsent(attribute.getEntity(), ignored -> new LinkedHashMap<>());
            putUnique(entityAttributes, attribute.getName(), attribute,
                    "实体" + attribute.getEntity() + "存在重复字段: " + attribute.getName(), itemSource);
            if (attribute.getId() > 0) {
                putUnique(attributesById, attribute.getId(), attribute, "重复字段ID: " + attribute.getId(), itemSource);
            }
            for (String operator : safe(attribute.getOperators())) {
                if (!operatorsByName.containsKey(operator)) {
                    throw invalid(itemSource, "字段" + attribute.getEntity() + "." + attribute.getName()
                            + "引用未知操作符: " + operator);
                }
            }
        }

        for (DataAttribute attribute : safe(metaData.getAttribute())) {
            validateLinkTemplate(attribute, attributesByEntity, sourceOf(attribute, sourceByObject, sourcePath));
        }

        for (DataEntity entity : entitiesByName.values()) {
            String itemSource = sourceOf(entity, sourceByObject, sourcePath);
            if (StringUtils.isNotBlank(entity.getSortColumn())) {
                requireIdentifier(entity.getSortColumn(), "实体sort_column", itemSource, PHYSICAL_IDENTIFIER);
                boolean exists = attributesByEntity.getOrDefault(entity.getName(), Map.of()).values().stream()
                        .anyMatch(attribute -> entity.getSortColumn().equals(attribute.getColumnName()));
                if (!exists) {
                    throw invalid(itemSource, "实体" + entity.getName() + "的默认排序字段不存在: " + entity.getSortColumn());
                }
            }
            validateTableTtl(entity, attributesByEntity, itemSource);
        }

        MetaData immutableMetaData = new MetaData();
        immutableMetaData.setEntity(List.copyOf(safe(metaData.getEntity())));
        immutableMetaData.setAttribute(List.copyOf(safe(metaData.getAttribute())));
        immutableMetaData.setOperator(List.copyOf(safe(metaData.getOperator())));
        Map<String, Map<String, DataAttribute>> nested = new LinkedHashMap<>();
        attributesByEntity.forEach((entity, attributes) -> nested.put(entity, Collections.unmodifiableMap(attributes)));
        return new MetadataSnapshot(
                immutableMetaData,
                immutableMetaData.getEntity(),
                immutableMetaData.getAttribute(),
                Collections.unmodifiableMap(entitiesByName),
                Collections.unmodifiableMap(entitiesById),
                Collections.unmodifiableMap(nested),
                Collections.unmodifiableMap(attributesById),
                Collections.unmodifiableMap(operatorsByName)
        );
    }

    private void validateTableTtl(DataEntity entity,
                                  Map<String, Map<String, DataAttribute>> attributesByEntity,
                                  String sourcePath) {
        if (entity.getAutoCreate() == null || entity.getAutoCreate().getTtl() == null) {
            return;
        }
        DataEntity.Ttl ttl = entity.getAutoCreate().getTtl();
        requireIdentifier(ttl.getColumn(), "实体" + entity.getName() + "的TTL列", sourcePath,
                LOGICAL_IDENTIFIER);
        if (ttl.getExpireAfter() <= 0) {
            throw invalid(sourcePath, "实体" + entity.getName() + "的TTL expire_after必须大于0");
        }
        if (ttl.getUnit() == null) {
            throw invalid(sourcePath, "实体" + entity.getName() + "的TTL unit不支持或为空");
        }
        DataAttribute ttlAttribute = attributesByEntity.getOrDefault(entity.getName(), Map.of()).values().stream()
                .filter(attribute -> ttl.getColumn().equals(attribute.getColumnName()))
                .findFirst()
                .orElseThrow(() -> invalid(sourcePath,
                        "实体" + entity.getName() + "的TTL列不存在: " + ttl.getColumn()));
        if (!isNonNullableTemporalType(ttlAttribute.getColumnType())) {
            throw invalid(sourcePath, "实体" + entity.getName()
                    + "的TTL列必须是非Nullable的Date、Date32、DateTime或DateTime64: " + ttl.getColumn());
        }
    }

    private boolean isNonNullableTemporalType(String columnType) {
        String type = StringUtils.trimToEmpty(columnType).replace(" ", "").toLowerCase(Locale.ROOT);
        return type.equals("date")
                || type.equals("date32")
                || type.equals("datetime")
                || type.startsWith("datetime(")
                || type.equals("datetime64")
                || type.startsWith("datetime64(");
    }

    private <K, V> void putUnique(Map<K, V> map, K key, V value, String message, String sourcePath) {
        if (map.putIfAbsent(key, value) != null) {
            throw invalid(sourcePath, message);
        }
    }

    private void requireIdentifier(String value, String field, String sourcePath, Pattern pattern) {
        if (StringUtils.isBlank(value) || !pattern.matcher(value).matches()) {
            throw invalid(sourcePath, field + "不合法: " + value);
        }
    }

    private IllegalArgumentException invalid(String sourcePath, String message) {
        return new IllegalArgumentException(message + "，元数据文件: " + sourcePath);
    }

    private String sourceOf(Object value, Map<Object, String> sourceByObject, String fallback) {
        return value == null ? fallback : sourceByObject.getOrDefault(value, fallback);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private void normalizeLinkTemplates(MetaData metaData) {
        if (metaData == null) {
            return;
        }
        for (DataAttribute attribute : safe(metaData.getAttribute())) {
            if (attribute == null) {
                continue;
            }
            if (StringUtils.isBlank(attribute.getLinkTemplate())) {
                attribute.setLinkTemplate(null);
            } else {
                attribute.setLinkTemplate(attribute.getLinkTemplate().trim());
            }
        }
    }

    private void validateLinkTemplate(DataAttribute attribute,
                                      Map<String, Map<String, DataAttribute>> attributesByEntity,
                                      String sourcePath) {
        if (attribute == null || StringUtils.isBlank(attribute.getLinkTemplate())) {
            return;
        }
        String template = attribute.getLinkTemplate();
        Matcher matcher = LINK_TEMPLATE_PLACEHOLDER.matcher(template);
        StringBuffer resolvedTemplate = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!attributesByEntity.getOrDefault(attribute.getEntity(), Collections.emptyMap())
                    .containsKey(placeholder)) {
                throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                        + "的link_template引用未知属性: " + placeholder);
            }
            matcher.appendReplacement(resolvedTemplate, "value");
        }
        matcher.appendTail(resolvedTemplate);
        if (resolvedTemplate.indexOf("{") >= 0 || resolvedTemplate.indexOf("}") >= 0) {
            throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                    + "的link_template占位符格式不正确");
        }
        validateLinkTemplateUrl(attribute, resolvedTemplate.toString(), sourcePath);
    }

    private void validateLinkTemplateUrl(DataAttribute attribute, String resolvedTemplate, String sourcePath) {
        String candidate = StringUtils.trimToEmpty(resolvedTemplate);
        if (candidate.isEmpty() || candidate.startsWith("//") || candidate.contains("\\")
                || CONTROL_CHARACTER.matcher(candidate).find()) {
            throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                    + "的link_template地址不合法");
        }
        if (URI_SCHEME.matcher(candidate).find() && !HTTP_URL.matcher(candidate).find()) {
            throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                    + "的link_template仅支持相对地址或http/https地址");
        }
        try {
            URI uri = new URI(candidate);
            if (uri.isAbsolute() && (!StringUtils.equalsAnyIgnoreCase(uri.getScheme(), "http", "https")
                    || StringUtils.isBlank(uri.getRawAuthority()))) {
                throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                        + "的link_template仅支持相对地址或http/https地址");
            }
        } catch (URISyntaxException ex) {
            throw invalid(sourcePath, "字段" + attribute.getEntity() + "." + attribute.getName()
                    + "的link_template地址不合法");
        }
    }

    private void supplementBuiltInAttributes(MetaData metaData,
                                             String sourcePath,
                                             Map<Object, String> sourceByObject) {
        if (metaData == null) {
            return;
        }
        if (metaData.getAttribute() == null) {
            metaData.setAttribute(new ArrayList<>());
        }
        List<DataAttribute> attributes = metaData.getAttribute();
        for (DataEntity entity : safe(metaData.getEntity())) {
            if (entity == null || StringUtils.isBlank(entity.getName())) {
                continue;
            }
            replaceCompatibleBuiltInAttribute(attributes, entity, sourcePath, sourceByObject,
                    MetaDataConstants.RECORD_ID_ATTRIBUTE, MetaDataConstants.RECORD_ID_COLUMN, "记录ID");
            replaceCompatibleBuiltInAttribute(attributes, entity, sourcePath, sourceByObject,
                    MetaDataConstants.INSERT_TIME_ATTRIBUTE, MetaDataConstants.INSERT_TIME_COLUMN, "创建时间");

            DataAttribute recordId = builtInRecordId(entity.getName());
            DataAttribute insertTime = builtInInsertTime(entity.getName());
            attributes.add(recordId);
            attributes.add(insertTime);
            sourceByObject.put(recordId, sourceOf(entity, sourceByObject, sourcePath));
            sourceByObject.put(insertTime, sourceOf(entity, sourceByObject, sourcePath));
        }
    }

    private void replaceCompatibleBuiltInAttribute(List<DataAttribute> attributes,
                                                    DataEntity entity,
                                                    String sourcePath,
                                                    Map<Object, String> sourceByObject,
                                                    String reservedName,
                                                    String reservedColumn,
                                                    String label) {
        DataAttribute compatibleConfiguredAttribute = null;
        for (DataAttribute attribute : new ArrayList<>(attributes)) {
            if (attribute == null || !entity.getName().equals(attribute.getEntity())) {
                continue;
            }
            boolean usesReservedName = reservedName.equals(attribute.getName());
            boolean usesReservedColumn = reservedColumn.equals(attribute.getColumnName());
            if (!usesReservedName && !usesReservedColumn) {
                continue;
            }
            if (usesReservedName && usesReservedColumn) {
                compatibleConfiguredAttribute = attribute;
                continue;
            }
            throw invalid(sourceOf(attribute, sourceByObject, sourcePath),
                    "字段名和列名" + reservedName + "为平台保留字段");
        }
        if (compatibleConfiguredAttribute != null) {
            attributes.remove(compatibleConfiguredAttribute);
            log.warn("实体{}显式配置了平台内置{}字段，已使用系统定义替代", entity.getName(), label);
        }
    }

    private DataAttribute builtInRecordId(String entityName) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity(entityName);
        attribute.setName(MetaDataConstants.RECORD_ID_ATTRIBUTE);
        attribute.setLabel("记录ID");
        attribute.setDescription("记录唯一ID");
        attribute.setColumnName(MetaDataConstants.RECORD_ID_COLUMN);
        attribute.setColumnType(MetaDataConstants.RECORD_ID_COLUMN_TYPE);
        attribute.setOperators(List.of("equal", "notequal", "in", "isnull", "isnotnull"));
        attribute.setDisplaySelected(false);
        attribute.setMustCandidate(false);
        attribute.setCopyable(true);
        return attribute;
    }

    private DataAttribute builtInInsertTime(String entityName) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity(entityName);
        attribute.setName(MetaDataConstants.INSERT_TIME_ATTRIBUTE);
        attribute.setLabel("创建时间");
        attribute.setDescription("创建时间");
        attribute.setColumnName(MetaDataConstants.INSERT_TIME_COLUMN);
        attribute.setColumnType(MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        attribute.setRetrievalType("date");
        attribute.setOperators(List.of("greatthan", "lessthan", "greatequalthan", "lessequalthan"));
        attribute.setDisplaySelected(false);
        attribute.setMustCandidate(false);
        return attribute;
    }

    private void supplementOperators(MetaData metaData) {
        if (metaData == null) {
            return;
        }
        if (metaData.getEntity() == null) {
            metaData.setEntity(new ArrayList<>());
        }
        if (metaData.getAttribute() == null) {
            metaData.setAttribute(new ArrayList<>());
        }
        if (metaData.getOperator() == null) {
            metaData.setOperator(new ArrayList<>());
        }
        supplementOperatorDefinitions(metaData);
        metaData.getAttribute().stream().filter(java.util.Objects::nonNull).forEach(this::supplementAttributeOperators);
    }

    private void supplementOperatorDefinitions(MetaData metaData) {
        Set<String> operatorNames = new LinkedHashSet<>();
        metaData.getOperator().stream().filter(java.util.Objects::nonNull)
                .forEach(operator -> operatorNames.add(operator.getName()));
        DEFAULT_OPERATOR_LABEL_MAP.forEach((name, label) -> {
            if (!operatorNames.contains(name)) {
                DataOperator dataOperator = new DataOperator();
                dataOperator.setName(name);
                dataOperator.setLabel(label);
                metaData.getOperator().add(dataOperator);
            }
        });
    }

    private void supplementAttributeOperators(DataAttribute attribute) {
        List<String> operators = new ArrayList<>();
        if (attribute.getOperators() != null) {
            operators.addAll(attribute.getOperators());
        }
        defaultOperatorsFor(attribute).forEach(operator -> {
            if (!operators.contains(operator)) {
                operators.add(operator);
            }
        });
        attribute.setOperators(List.copyOf(operators));
    }

    private List<String> defaultOperatorsFor(DataAttribute attribute) {
        String columnType = StringUtils.defaultString(attribute.getColumnType()).toLowerCase(Locale.ROOT);
        String retrievalType = StringUtils.defaultString(attribute.getRetrievalType()).toLowerCase(Locale.ROOT);
        if (columnType.startsWith("array")) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in", "match");
        }
        if ("date".equals(retrievalType) || columnType.startsWith("date")) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "greatthan", "greatequalthan", "lessthan", "lessequalthan", "between");
        }
        if (isNumberType(columnType)) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in", "greatthan", "greatequalthan", "lessthan", "lessequalthan", "between");
        }
        if (columnType.contains("string") || StringUtils.isBlank(columnType)) {
            return List.of("equal", "notequal", "isnull", "isnotnull", "in", "match");
        }
        return List.of("equal", "notequal", "isnull", "isnotnull", "in");
    }

    private boolean isNumberType(String columnType) {
        return columnType.startsWith("int")
                || columnType.startsWith("uint")
                || columnType.startsWith("float")
                || columnType.startsWith("decimal");
    }

    @Override
    public DataEntity getDataEntityById(Integer id) {
        return id == null ? null : snapshotRef.get().entitiesById().get(id);
    }

    @Override
    public DataEntity getDataEntityByName(String name) {
        return StringUtils.isBlank(name) ? null : snapshotRef.get().entitiesByName().get(name);
    }

    @Override
    public DataAttribute getDataAttributeById(Integer id) {
        return id == null ? null : snapshotRef.get().attributesById().get(id);
    }

    @Override
    public DataAttribute getDataAttributeByName(String entity, String attribute) {
        return snapshotRef.get().attributesByEntity()
                .getOrDefault(entity, Collections.emptyMap())
                .get(attribute);
    }

    @Override
    public List<DataEntity> getAllDataEntity() {
        return snapshotRef.get().entities();
    }

    @Override
    public List<DataAttribute> getAllDataAttribute() {
        return snapshotRef.get().attributes();
    }

    @Override
    public List<DataAttribute> getAllDataAttributeByEntity(DataEntity dataEntity) {
        if (dataEntity == null || StringUtils.isBlank(dataEntity.getName())) {
            return Collections.emptyList();
        }
        return snapshotRef.get().attributesByEntity()
                .getOrDefault(dataEntity.getName(), Collections.emptyMap()).values().stream()
                .sorted(Comparator.comparingInt(attribute ->
                        MetaDataConstants.isRecordId(attribute) ? 0 : 1))
                .toList();
    }

    @Override
    public DataOperator getDataOperatorByName(String name) {
        return StringUtils.isBlank(name) ? null : snapshotRef.get().operatorsByName().get(name);
    }

    private record MetadataSnapshot(
            MetaData metaData,
            List<DataEntity> entities,
            List<DataAttribute> attributes,
            Map<String, DataEntity> entitiesByName,
            Map<Integer, DataEntity> entitiesById,
            Map<String, Map<String, DataAttribute>> attributesByEntity,
            Map<Integer, DataAttribute> attributesById,
            Map<String, DataOperator> operatorsByName
    ) {
        private static MetadataSnapshot empty() {
            MetaData empty = new MetaData();
            empty.setEntity(List.of());
            empty.setAttribute(List.of());
            empty.setOperator(List.of());
            return new MetadataSnapshot(empty, List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        private boolean isEmpty() {
            return entities.isEmpty() && attributes.isEmpty();
        }
    }

    private record LoadedMetadata(MetaData metaData, Map<Object, String> sourceByObject) {
    }
}
