package com.coolxer.service.retrieval.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.vo.DataAttributeVo;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetaDataServiceImplTest {

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
    void serializesDataAttributeVoAutoCompleteAsSnakeCase() {
        DataAttributeVo dataAttributeVo = new DataAttributeVo();
        dataAttributeVo.setName("device_name");
        dataAttributeVo.setAutoComplete(true);

        assertThat(JacksonUtil.toMap(dataAttributeVo)).containsEntry("auto_complete", true);
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
