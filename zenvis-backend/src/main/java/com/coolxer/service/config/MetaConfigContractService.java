package com.coolxer.service.config;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the published Meta JSON Schema as the single runtime contract.
 */
@Service
public class MetaConfigContractService {

    private static final String SCHEMA_RESOURCE = "schema/meta_schema.json";

    private final ConfigService configService;
    private volatile Snapshot cached;

    public MetaConfigContractService(ConfigService configService) {
        this.configService = configService;
    }

    public Snapshot snapshot() {
        String schemaText = loadSchemaText();
        String schemaHash = MetaConfigValidationService.digest(schemaText);
        Snapshot current = cached;
        if (current != null && current.schemaHash().equals(schemaHash)) {
            return current;
        }
        Snapshot loaded = parse(schemaText, schemaHash);
        cached = loaded;
        return loaded;
    }

    private String loadSchemaText() {
        if (configService != null) {
            try {
                String configured = configService.readFileSchema("meta", "meta_schema.json");
                if (StringUtils.hasText(configured)) {
                    return configured;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the packaged copy of the same published schema.
            }
        }
        try (InputStream input = new ClassPathResource(SCHEMA_RESOURCE).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 Meta Schema: " + SCHEMA_RESOURCE, e);
        }
    }

    private Snapshot parse(String schemaText, String schemaHash) {
        JsonNode schema;
        try {
            schema = JacksonConfig.OBJECT_MAPPER.readTree(schemaText);
        } catch (Exception e) {
            throw new IllegalStateException("Meta Schema 不是合法 JSON", e);
        }
        JsonNode properties = schema == null ? null : schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw new IllegalStateException("Meta Schema 缺少 properties");
        }
        Set<String> topLevel = fields(properties);
        JsonNode entityDefinition = itemSchema(properties.get("entity"));
        JsonNode attributeDefinition = itemSchema(properties.get("attribute"));
        JsonNode operatorDefinition = itemSchema(properties.get("operator"));
        Set<String> entityFields = fields(entityDefinition.get("properties"));
        Set<String> attributeFields = fields(attributeDefinition.get("properties"));
        Set<String> operatorFields = fields(operatorDefinition.get("properties"));
        JsonNode autoCreate = entityDefinition.path("properties").path("auto_create");
        JsonNode ttl = autoCreate.path("properties").path("ttl");
        JsonNode linkCondition = attributeDefinition.path("properties").path("link_condition");
        Set<String> autoCreateFields = fields(autoCreate.get("properties"));
        Set<String> ttlFields = fields(ttl.get("properties"));
        Set<String> linkConditionFields = fields(linkCondition.get("properties"));
        List<String> entityRequired = textList(entityDefinition.get("required"));
        List<String> attributeRequired = textList(attributeDefinition.get("required"));
        List<String> operatorRequired = textList(operatorDefinition.get("required"));
        String schemaVersion = "meta-schema@" + schemaHash.substring(0, 12);
        String promptContract = promptContract(schemaVersion, schemaHash, topLevel,
                entityFields, entityRequired, attributeFields, attributeRequired,
                operatorFields, operatorRequired);
        return new Snapshot(schemaText, schemaVersion, schemaHash, topLevel,
                entityFields, autoCreateFields, ttlFields, attributeFields,
                operatorFields, linkConditionFields, entityRequired,
                attributeRequired, operatorRequired, promptContract);
    }

    private JsonNode itemSchema(JsonNode arraySchema) {
        if (arraySchema == null || !arraySchema.isObject()) {
            throw new IllegalStateException("Meta Schema 顶层数组定义缺失");
        }
        JsonNode items = arraySchema.get("items");
        if (items == null || !items.isObject()) {
            throw new IllegalStateException("Meta Schema 顶层数组缺少 items");
        }
        return items;
    }

    private Set<String> fields(JsonNode properties) {
        if (properties == null || !properties.isObject()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Iterator<String> iterator = properties.fieldNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        return Collections.unmodifiableSet(names);
    }

    private List<String> textList(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private String promptContract(String schemaVersion,
                                  String schemaHash,
                                  Set<String> topLevel,
                                  Set<String> entityFields,
                                  List<String> entityRequired,
                                  Set<String> attributeFields,
                                  List<String> attributeRequired,
                                  Set<String> operatorFields,
                                  List<String> operatorRequired) {
        return """
                【Meta 精简契约】
                契约版本：%s
                Schema 指纹：%s
                1. 顶层只允许 %s 三个数组，字段名使用 snake_case。
                2. entity 允许字段：%s；必填：%s；data_source 固定 clickhouse，
                   table_name 固定 zenvis.<table_name>；auto_create.engine 固定 MergeTree，
                   order_by 至少一项且禁止 zenvis_id。
                3. attribute 允许字段：%s；必填：%s；operators 引用顶层 operator.name。
                4. operator 允许字段：%s；必填：%s；展示名必须使用 label，不使用 description。
                5. 禁止生成或重复 zenvis_id、zenvis_insert_time，平台会自动注入。
                6. 先调用 config_meta_draft 展开精简草稿，再调用
                   config_validate(type="meta", fileName, text)。只有本次返回的 passed=true、
                   validationId 和 candidateDigest 可作为确认依据；确认卡必须携带 validationId 与 candidateDigest。
                7. config_validate 只接受 Meta 根对象。确认卡/记录卡的 fileName、configKind、
                   overwrite、candidateDigest、validationStatus、config 等字段绝不能放入 zenvis:meta-config。
                """.formatted(schemaVersion, schemaHash, topLevel, entityFields,
                entityRequired, attributeFields, attributeRequired, operatorFields,
                operatorRequired);
    }

    public record Snapshot(String schemaText,
                           String schemaVersion,
                           String schemaHash,
                           Set<String> topLevelFields,
                           Set<String> entityFields,
                           Set<String> autoCreateFields,
                           Set<String> ttlFields,
                           Set<String> attributeFields,
                           Set<String> operatorFields,
                           Set<String> linkConditionFields,
                           List<String> entityRequired,
                           List<String> attributeRequired,
                           List<String> operatorRequired,
                           String promptContract) {
    }
}
