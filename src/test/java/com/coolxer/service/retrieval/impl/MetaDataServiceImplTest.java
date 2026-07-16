package com.coolxer.service.retrieval.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.vo.DataAttributeVo;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
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
    void readsAutoCompleteFlagFromSnakeCaseMeta() {
        MetaData metaData = JacksonUtil.toObject("""
                {
                  "attribute": [
                    {
                      "entity": "asset",
                      "name": "device_name",
                      "column_type": "String",
                      "operators": ["equal"],
                      "auto_complete": true
                    }
                  ]
                }
                """, MetaData.class);

        assertThat(metaData.getAttribute()).hasSize(1);
        assertThat(metaData.getAttribute().get(0).isAutoComplete()).isTrue();
    }

    @Test
    void injectsBuiltInInsertTimeForEveryEntity() throws Exception {
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
        DataAttribute insertTime = service.getDataAttributeByName(
                "asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE);

        assertThat(loaded.getAttribute()).hasSize(2);
        assertThat(insertTime).isNotNull();
        assertThat(insertTime.getLabel()).isEqualTo("创建时间");
        assertThat(insertTime.getDescription()).isEqualTo("创建时间");
        assertThat(insertTime.getColumnName()).isEqualTo(MetaDataConstants.INSERT_TIME_COLUMN);
        assertThat(insertTime.getColumnType()).isEqualTo("DateTime64(3)");
        assertThat(insertTime.getRetrievalType()).isEqualTo("date");
        assertThat(insertTime.isDisplaySelected()).isTrue();
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
    void serializesDataAttributeVoAutoCompleteAsSnakeCase() {
        DataAttributeVo dataAttributeVo = new DataAttributeVo();
        dataAttributeVo.setName("device_name");
        dataAttributeVo.setAutoComplete(true);

        assertThat(JacksonUtil.toMap(dataAttributeVo)).containsEntry("auto_complete", true);
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
        assertThat(service.getAllDataAttribute()).hasSize(2);

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

    private DataAttribute attribute(String name, String columnType, String retrievalType, List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("asset");
        attribute.setName(name);
        attribute.setColumnType(columnType);
        attribute.setRetrievalType(retrievalType);
        attribute.setOperators(operators);
        return attribute;
    }
}
