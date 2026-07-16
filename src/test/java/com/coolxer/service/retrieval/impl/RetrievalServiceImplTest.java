package com.coolxer.service.retrieval.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.service.retrieval.MetaDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalServiceImplTest {

    @Test
    void returnsPresentationFlagsForAttributeAndDefaultDisplayLists() {
        DataEntity entity = new DataEntity();
        entity.setName("msg");
        entity.setTableName("msg");
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("msg");
        attribute.setName("guid");
        attribute.setLabel("设备ID");
        attribute.setColumnName("guid");
        attribute.setColumnType("String");
        attribute.setOperators(List.of("equal"));
        attribute.setDisplaySelected(true);
        attribute.setLinkTemplate("/device/detail?guid={guid}");
        attribute.setCopyable(true);
        DataOperator operator = new DataOperator();
        operator.setName("equal");
        operator.setLabel("等于");
        MetaDataService metaDataService = mock(MetaDataService.class);
        when(metaDataService.getDataEntityByName("msg")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity)).thenReturn(List.of(attribute));
        when(metaDataService.getDataOperatorByName("equal")).thenReturn(operator);
        RetrievalServiceImpl service = new RetrievalServiceImpl();
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);

        DataAttributeResultVo attributeResult = service.listAttribute("msg", null, 7);
        DataAttributeResultVo displayResult = service.listAttributeForDisplay("msg", null, 7);

        assertThat(attributeResult.getAttributeList()).singleElement()
                .satisfies(result -> {
                    assertThat(result.getLinkTemplate()).isEqualTo("/device/detail?guid={guid}");
                    assertThat(result.isCopyable()).isTrue();
                });
        assertThat(displayResult.getSelectAttributeList()).singleElement()
                .satisfies(result -> {
                    assertThat(result.getLinkTemplate()).isEqualTo("/device/detail?guid={guid}");
                    assertThat(result.isCopyable()).isTrue();
                });
    }
}
