package com.coolxer.service.retrieval.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.vo.DataAttributeVo;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaDataServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void supplementOperatorsAddsTypeAwareOperators() {
        MetaDataServiceImpl metaDataService = new MetaDataServiceImpl();
        MetaData metaData = new MetaData();
        DataAttribute textAttribute = attribute("attack_type_name", "String", null, List.of("equal"));
        DataAttribute dateAttribute = attribute("server_time", "Int64", "date", List.of("equal"));
        DataAttribute arrayAttribute = attribute("tags", "Array(String)", null, new ArrayList<>());
        metaData.setAttribute(List.of(textAttribute, dateAttribute, arrayAttribute));

        ReflectionTestUtils.invokeMethod(metaDataService, "supplementOperators", metaData);

        assertThat(metaData.getOperator()).extracting("name")
                .contains("equal", "notequal", "isnull", "isnotnull", "match", "greatthan", "between", "in");
        assertThat(textAttribute.getOperators()).containsExactly("equal", "notequal", "isnull", "isnotnull", "in", "match");
        assertThat(dateAttribute.getOperators()).containsExactly(
                "equal", "notequal", "isnull", "isnotnull", "greatthan", "greatequalthan", "lessthan", "lessequalthan", "between");
        assertThat(arrayAttribute.getOperators()).containsExactly("equal", "notequal", "isnull", "isnotnull", "in", "match");
    }

    @Test
    void readsBooleanFlagsFromSnakeCaseMetaAndKeepsCompatibleDefaults() {
        MetaData metaData = JacksonUtil.toObject("""
                {
                  "attribute": [
                    {
                      "entity": "asset",
                      "name": "device_name",
                      "column_type": "String",
                      "search_type": "datetime",
                      "operators": ["equal"],
                      "auto_complete": true,
                      "copyable": true
                    },
                    {
                      "entity": "asset",
                      "name": "legacy_field",
                      "column_type": "String",
                      "operators": ["equal"]
                    }
                  ]
                }
                """, MetaData.class);

        assertThat(metaData.getAttribute()).hasSize(2);
        assertThat(metaData.getAttribute().get(0).isAutoComplete()).isTrue();
        assertThat(metaData.getAttribute().get(0).isCopyable()).isTrue();
        assertThat(metaData.getAttribute().get(0).getSearchType()).isEqualTo("datetime");
        assertThat(metaData.getAttribute().get(1).isCopyable()).isFalse();
    }

    @Test
    void readsLinkTemplateStringAndSerializesVoAsSnakeCase() {
        MetaData metaData = JacksonUtil.toObject("""
                {
                  "attribute": [
                    {
                      "entity": "asset",
                      "name": "device_name",
                      "link_template": "/detail?name={device_name}"
                    }
                  ]
                }
                """, MetaData.class);
        DataAttributeVo dataAttributeVo = new DataAttributeVo();
        dataAttributeVo.setName("device_name");
        dataAttributeVo.setLinkTemplate(metaData.getAttribute().get(0).getLinkTemplate());

        assertThat(metaData.getAttribute().get(0).getLinkTemplate())
                .isEqualTo("/detail?name={device_name}");
        assertThat(JacksonUtil.toMap(dataAttributeVo))
                .containsEntry("link_template", "/detail?name={device_name}");
        assertThat(JacksonUtil.toMap(new DataAttributeVo()))
                .doesNotContainKey("link_template");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"link_template\": true",
            "\"link_template\": false",
            "\"link_template\": 123",
            "\"link_template\": []",
            "\"link_template\": {}"
    })
    void nonStringLinkTemplateKeepsPreviousSnapshot(String linkProperty) throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, metadataWithLinkTemplate("/detail?ip={ip}"));
        MetaDataServiceImpl service = metadataService(tempDir);
        MetaData first = service.loadMetaData();

        Files.writeString(metadata, metadataWithLinkProperty(linkProperty));
        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", "ip").getLinkTemplate())
                .isEqualTo("/detail?ip={ip}");
    }

    @Test
    void deprecatedLinkFieldKeepsPreviousSnapshot() throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, metadataWithLinkTemplate("/detail?ip={ip}"));
        MetaDataServiceImpl service = metadataService(tempDir);
        MetaData first = service.loadMetaData();
        String deprecatedField = String.join("_", "aggregate", "link");

        Files.writeString(metadata, metadataWithLinkProperty(
                "\"" + deprecatedField + "\": \"/detail?ip={ip}\""));
        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", "ip").getLinkTemplate())
                .isEqualTo("/detail?ip={ip}");
    }

    @Test
    void acceptsLinkTemplateWithMultipleAndRepeatedPlaceholders() throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset"}],
                  "attribute": [
                    {
                      "id": 10,
                      "entity": "asset",
                      "name": "ip",
                      "column_name": "ip",
                      "column_type": "String",
                      "link_template": "https://example.com/detail?ip={ip}&copy={ip}&name={name}"
                    },
                    {
                      "id": 11,
                      "entity": "asset",
                      "name": "name",
                      "column_name": "name",
                      "column_type": "String"
                    }
                  ]
                }
                """);
        MetaDataServiceImpl service = metadataService(tempDir);

        MetaData loaded = service.loadMetaData();

        assertThat(loaded).isNotNull();
        assertThat(service.getDataAttributeByName("asset", "ip").getLinkTemplate())
                .isEqualTo("https://example.com/detail?ip={ip}&copy={ip}&name={name}");
    }

    @Test
    void acceptsStaticRelativeLinkTemplate() throws Exception {
        Files.writeString(tempDir.resolve("meta.json"), metadataWithLinkTemplate("/asset/help"));
        MetaDataServiceImpl service = metadataService(tempDir);

        MetaData loaded = service.loadMetaData();

        assertThat(loaded).isNotNull();
        assertThat(service.getDataAttributeByName("asset", "ip").getLinkTemplate())
                .isEqualTo("/asset/help");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/detail?ip={missing}",
            "/detail?ip={ip",
            "/detail?ip={}",
            "javascript:alert(1)",
            "data:text/plain,test",
            "blob:https://example.com/id",
            "file:///tmp/file",
            "//example.com/detail"
    })
    void invalidLinkTemplateKeepsPreviousSnapshot(String linkTemplate) throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, metadataWithLinkTemplate("/detail?ip={ip}"));
        MetaDataServiceImpl service = metadataService(tempDir);
        MetaData first = service.loadMetaData();

        Files.writeString(metadata, metadataWithLinkTemplate(linkTemplate));
        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", "ip").getLinkTemplate())
                .isEqualTo("/detail?ip={ip}");
    }

    @Test
    void injectsBuiltInRecordIdAndInsertTimeForEveryEntity() throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, """
                {
                  "entity": [{
                    "id": 1,
                    "name": "asset",
                    "table_name": "asset_table"
                  }],
                  "attribute": [{
                    "id": 10,
                    "entity": "asset",
                    "name": "name",
                    "column_name": "name",
                    "column_type": "String"
                  }]
                }
                """);
        CustomWebConfig config = mock(CustomWebConfig.class);
        when(config.getRetrievalMetaFilePath()).thenReturn(tempDir.toString());
        MetaDataServiceImpl service = new MetaDataServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", config);

        MetaData loaded = service.loadMetaData();
        DataAttribute recordId = service.getDataAttributeByName(
                "asset", MetaDataConstants.RECORD_ID_ATTRIBUTE);
        DataAttribute insertTime = service.getDataAttributeByName(
                "asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE);

        assertThat(loaded.getAttribute()).hasSize(3);
        assertThat(recordId).isNotNull();
        assertThat(recordId.getLabel()).isEqualTo("记录ID");
        assertThat(recordId.getDescription()).isEqualTo("记录唯一ID");
        assertThat(recordId.getColumnName()).isEqualTo(MetaDataConstants.RECORD_ID_COLUMN);
        assertThat(recordId.getColumnType()).isEqualTo("Nullable(UUID)");
        assertThat(recordId.getOperators()).containsExactly(
                "equal", "notequal", "in", "isnull", "isnotnull");
        assertThat(recordId.isDisplaySelected()).isFalse();
        assertThat(recordId.isMustCandidate()).isFalse();
        assertThat(recordId.isCopyable()).isTrue();
        assertThat(service.getAllDataAttributeByEntity(service.getDataEntityByName("asset")))
                .first()
                .extracting(DataAttribute::getName)
                .isEqualTo(MetaDataConstants.RECORD_ID_ATTRIBUTE);
        assertThat(insertTime).isNotNull();
        assertThat(insertTime.getLabel()).isEqualTo("创建时间");
        assertThat(insertTime.getDescription()).isEqualTo("创建时间");
        assertThat(insertTime.getColumnName()).isEqualTo(MetaDataConstants.INSERT_TIME_COLUMN);
        assertThat(insertTime.getColumnType()).isEqualTo("DateTime64(3)");
        assertThat(insertTime.getRetrievalType()).isEqualTo("date");
        assertThat(insertTime.isDisplaySelected()).isFalse();
        assertThat(insertTime.isMustCandidate()).isFalse();
        assertThat(insertTime.getOperators()).contains(
                "greatthan", "lessthan", "greatequalthan", "lessequalthan");
    }

    @Test
    void reservedInsertTimeCollisionKeepsPreviousSnapshot() throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "name", "column_name": "name", "column_type": "String"}]
                }
                """);
        CustomWebConfig config = mock(CustomWebConfig.class);
        when(config.getRetrievalMetaFilePath()).thenReturn(tempDir.toString());
        MetaDataServiceImpl service = new MetaDataServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", config);
        MetaData first = service.loadMetaData();

        Files.writeString(metadata, """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{
                    "id": 10,
                    "entity": "asset",
                    "name": "zenvis_insert_time",
                    "column_name": "created_at",
                    "column_type": "DateTime64(3)"
                  }]
                }
                """);

        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE))
                .isNotNull()
                .extracting(DataAttribute::getColumnName)
                .isEqualTo(MetaDataConstants.INSERT_TIME_COLUMN);
    }

    @Test
    void compatibleConfiguredRecordIdIsReplacedAndBusinessIdRemainsOrdinary() throws Exception {
        Files.writeString(tempDir.resolve("meta.json"), """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [
                    {"id": 10, "entity": "asset", "name": "id", "column_name": "id", "column_type": "String"},
                    {"id": 11, "entity": "asset", "name": "zenvis_id", "column_name": "zenvis_id", "column_type": "String"}
                  ]
                }
                """);
        MetaDataServiceImpl service = metadataService(tempDir);

        MetaData loaded = service.loadMetaData();

        assertThat(loaded).isNotNull();
        assertThat(service.getDataAttributeByName("asset", "id").getColumnType()).isEqualTo("String");
        assertThat(service.getDataAttributeByName("asset", MetaDataConstants.RECORD_ID_ATTRIBUTE))
                .isNotNull()
                .extracting(DataAttribute::getColumnType)
                .isEqualTo(MetaDataConstants.RECORD_ID_COLUMN_TYPE);
        assertThat(loaded.getAttribute()).extracting(DataAttribute::getName)
                .containsExactlyInAnyOrder("id", MetaDataConstants.RECORD_ID_ATTRIBUTE,
                        MetaDataConstants.INSERT_TIME_ATTRIBUTE);
    }

    @Test
    void repeatedMetadataLoadDoesNotDuplicateBuiltInAttributes() throws Exception {
        Files.writeString(tempDir.resolve("meta.json"), """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "name", "column_name": "name", "column_type": "String"}]
                }
                """);
        MetaDataServiceImpl service = metadataService(tempDir);

        service.loadMetaData();
        MetaData reloaded = service.loadMetaData();

        assertThat(reloaded.getAttribute()).extracting(DataAttribute::getName)
                .containsExactlyInAnyOrder("name", MetaDataConstants.RECORD_ID_ATTRIBUTE,
                        MetaDataConstants.INSERT_TIME_ATTRIBUTE);
    }

    @Test
    void reservedRecordIdCollisionKeepsPreviousSnapshot() throws Exception {
        Files.writeString(tempDir.resolve("meta.json"), """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "name", "column_name": "name", "column_type": "String"}]
                }
                """);
        MetaDataServiceImpl service = metadataService(tempDir);
        MetaData first = service.loadMetaData();

        Files.writeString(tempDir.resolve("meta.json"), """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "zenvis_id", "column_name": "business_id", "column_type": "UUID"}]
                }
                """);

        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", MetaDataConstants.RECORD_ID_ATTRIBUTE))
                .isNotNull()
                .extracting(DataAttribute::getColumnName)
                .isEqualTo(MetaDataConstants.RECORD_ID_COLUMN);

        Files.writeString(tempDir.resolve("meta.json"), """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "business_id", "column_name": "zenvis_id", "column_type": "UUID"}]
                }
                """);

        assertThat(service.loadMetaData()).isSameAs(first);
        assertThat(service.getDataAttributeByName("asset", MetaDataConstants.RECORD_ID_ATTRIBUTE))
                .isNotNull()
                .extracting(DataAttribute::getColumnName)
                .isEqualTo(MetaDataConstants.RECORD_ID_COLUMN);
    }

    @Test
    void serializesDataAttributeVoAutoCompleteAsSnakeCase() {
        DataAttributeVo dataAttributeVo = new DataAttributeVo();
        dataAttributeVo.setName("device_name");
        dataAttributeVo.setSearchType("datetime");
        dataAttributeVo.setAutoComplete(true);
        dataAttributeVo.setCopyable(true);

        assertThat(JacksonUtil.toMap(dataAttributeVo))
                .containsEntry("search_type", "datetime")
                .containsEntry("auto_complete", true)
                .containsEntry("copyable", true);
    }

    @Test
    void loadsIdIndexesAndKeepsPreviousSnapshotWhenReloadFails() throws Exception {
        Path metadata = tempDir.resolve("meta.json");
        Files.writeString(metadata, """
                {
                  "entity": [{"id": 1, "name": "asset", "label": "资产", "table_name": "asset_table", "sort_column": "src_ip"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "ip", "label": "IP", "column_name": "src_ip", "column_type": "String", "operators": ["equal"]}],
                  "operator": [{"id": 1, "name": "equal", "label": "等于"}]
                }
                """);
        CustomWebConfig config = mock(CustomWebConfig.class);
        when(config.getRetrievalMetaFilePath()).thenReturn(tempDir.toString());
        MetaDataServiceImpl service = new MetaDataServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", config);

        MetaData first = service.loadMetaData();

        assertThat(first).isNotNull();
        assertThat(service.getDataEntityById(1).getName()).isEqualTo("asset");
        assertThat(service.getDataAttributeById(10).getName()).isEqualTo("ip");
        assertThat(service.getAllDataAttribute()).hasSize(3);

        Files.writeString(metadata, "{ invalid json }");
        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataEntityByName("asset")).isNotNull();
    }

    @Test
    void duplicateDefinitionDoesNotReplacePreviousSnapshot() throws Exception {
        Path firstFile = tempDir.resolve("01-first.json");
        Files.writeString(firstFile, """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset_table", "sort_column": "src_ip"}],
                  "attribute": [{"id": 10, "entity": "asset", "name": "ip", "column_name": "src_ip", "column_type": "String"}]
                }
                """);
        CustomWebConfig config = mock(CustomWebConfig.class);
        when(config.getRetrievalMetaFilePath()).thenReturn(tempDir.toString());
        MetaDataServiceImpl service = new MetaDataServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", config);
        MetaData first = service.loadMetaData();

        Files.writeString(tempDir.resolve("02-duplicate.json"), """
                {"entity": [{"id": 2, "name": "asset", "table_name": "other_table"}]}
                """);
        MetaData retained = service.loadMetaData();

        assertThat(retained).isSameAs(first);
        assertThat(service.getDataEntityByName("asset").getTableName()).isEqualTo("asset_table");
    }

    @Test
    void validatesStructuredTtlAndAllowsBuiltInInsertTime() throws Exception {
        Path metadata = tempDir.resolve("ttl.json");
        Files.writeString(metadata, metadataWithTtl(
                "zenvis_insert_time", "String", 30, "DAY", ""));

        MetaData validated = new MetaDataServiceImpl().validateMetaDataFiles(List.of(metadata));

        assertThat(validated.getEntity().get(0).getAutoCreate().getTtl().getColumn())
                .isEqualTo("zenvis_insert_time");
        assertThat(validated.getEntity().get(0).getAutoCreate().getTtl().getExpireAfter())
                .isEqualTo(30);
    }

    @Test
    void rejectsInvalidTtlColumnTypeAndRetention() throws Exception {
        Path nullable = tempDir.resolve("nullable.json");
        Files.writeString(nullable, metadataWithTtl("event_time", "Nullable(DateTime64(3))", 30, "DAY", ""));
        Path zero = tempDir.resolve("zero.json");
        Files.writeString(zero, metadataWithTtl("event_time", "DateTime64(3)", 0, "DAY", ""));

        MetaDataServiceImpl service = new MetaDataServiceImpl();
        assertThatThrownBy(() -> service.validateMetaDataFiles(List.of(nullable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL列必须是非Nullable");
        assertThatThrownBy(() -> service.validateMetaDataFiles(List.of(zero)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expire_after必须大于0");
    }

    @Test
    void rejectsMissingTtlColumnAndUnsupportedUnit() throws Exception {
        Path missing = tempDir.resolve("missing.json");
        Files.writeString(missing, metadataWithTtl("missing_time", "DateTime64(3)", 30, "DAY", ""));
        Path unsupportedUnit = tempDir.resolve("unit.json");
        Files.writeString(unsupportedUnit, metadataWithTtl("event_time", "DateTime64(3)", 30, "MINUTE", ""));

        MetaDataServiceImpl service = new MetaDataServiceImpl();
        assertThatThrownBy(() -> service.validateMetaDataFiles(List.of(missing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL列不存在");
        assertThatThrownBy(() -> service.validateMetaDataFiles(List.of(unsupportedUnit)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL unit不支持或为空");
    }

    @Test
    void rejectsUnknownStructuredTtlProperty() {
        assertThatThrownBy(() -> JacksonUtil.toObject(
                metadataWithTtl("event_time", "DateTime64(3)", 30, "DAY", ", \"days\": 30"),
                MetaData.class))
                .isInstanceOf(com.coolxer.commons.exception.ApiException.class)
                .hasMessageContaining("json解析失败");
    }

    private DataAttribute attribute(String name, String columnType, String retrievalType, List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("asset");
        attribute.setName(name);
        attribute.setColumnType(columnType);
        attribute.setRetrievalType(retrievalType);
        attribute.setOperators(operators);
        return attribute;
    }

    private MetaDataServiceImpl metadataService(Path metadataPath) {
        CustomWebConfig config = mock(CustomWebConfig.class);
        when(config.getRetrievalMetaFilePath()).thenReturn(metadataPath.toString());
        MetaDataServiceImpl service = new MetaDataServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", config);
        return service;
    }

    private String metadataWithLinkTemplate(String linkTemplate) {
        return metadataWithLinkProperty("\"link_template\": \"" + linkTemplate + "\"");
    }

    private String metadataWithLinkProperty(String linkProperty) {
        return """
                {
                  "entity": [{"id": 1, "name": "asset", "table_name": "asset"}],
                  "attribute": [
                    {
                      "id": 10,
                      "entity": "asset",
                      "name": "ip",
                      "column_name": "ip",
                      "column_type": "String",
                      %s
                    }
                  ]
                }
                """.formatted(linkProperty);
    }

    private String metadataWithTtl(String ttlColumn, String columnType, long expireAfter,
                                   String unit, String extraTtlProperty) {
        return """
                {
                  "entity": [{
                    "id": 1,
                    "name": "asset",
                    "table_name": "asset",
                    "auto_create": {
                      "engine": "MergeTree()",
                      "order_by": ["event_time"],
                      "ttl": {
                        "column": "%s",
                        "expire_after": %d,
                        "unit": "%s"%s
                      }
                    }
                  }],
                  "attribute": [{
                    "id": 10,
                    "entity": "asset",
                    "name": "event_time",
                    "column_name": "event_time",
                    "column_type": "%s"
                  }]
                }
                """.formatted(ttlColumn, expireAfter, unit, extraTtlProperty, columnType);
    }
}
