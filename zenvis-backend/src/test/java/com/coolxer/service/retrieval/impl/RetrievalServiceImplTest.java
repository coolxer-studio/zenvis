package com.coolxer.service.retrieval.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.DataQueryContext;
import com.coolxer.model.retrieval.rule.DisplayAttribute;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.RetrievalExportResult;
import com.coolxer.service.retrieval.DataQueryService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.RetrievalAccessPolicy;
import com.coolxer.service.retrieval.RetrievalRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceImplTest {

    @Test
    void exportsConfiguredMaximumWithDisplayLabelsAndEscapedCsvValues() {
        DataAttribute first = new DataAttribute();
        first.setName("first");
        first.setLabel("相同");
        DataAttribute second = new DataAttribute();
        second.setName("second");
        second.setLabel("相同");
        DisplayAttribute display = new DisplayAttribute();
        display.setAttributeList(List.of(first, second));
        RetrievalRule rule = new RetrievalRule();
        rule.setDisplayAttributes(List.of(display));

        RetrievalRuleService ruleService = mock(RetrievalRuleService.class);
        when(ruleService.generateRetrievalRule(org.mockito.ArgumentMatchers.any())).thenReturn(rule);
        DataQueryService queryService = mock(DataQueryService.class);
        AtomicInteger calls = new AtomicInteger();
        List<Integer> queriedPages = new ArrayList<>();
        when(queryService.query(rule)).thenAnswer(invocation -> {
            queriedPages.add(rule.getRetrievalPageable().getPage());
            DataQueryContext context = new DataQueryContext();
            context.setTotal(BigDecimal.valueOf(5));
            if (calls.incrementAndGet() == 1) {
                context.setResultList(List.of(
                        Map.of("first", "a,b", "second", Map.of("nested", "a\"b")),
                        Map.of("first", "second", "second", List.of("x", "y"))));
            } else {
                context.setResultList(List.of(
                        Map.of("first", "third", "second", "value"),
                        Map.of("first", "fourth", "second", "value")));
            }
            return context;
        });

        RetrievalServiceImpl service = new RetrievalServiceImpl();
        ReflectionTestUtils.setField(service, "retrievalRuleService", ruleService);
        ReflectionTestUtils.setField(service, "dataQueryService", queryService);
        ReflectionTestUtils.setField(service, "retrievalAccessPolicy", mock(RetrievalAccessPolicy.class));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxExportRows", 3);

        com.coolxer.model.retrieval.dto.RetrievalRequestDto request = new com.coolxer.model.retrieval.dto.RetrievalRequestDto();
        request.setPage(99);
        request.setSize(1);
        RetrievalExportResult result = service.exportByCriteria(request);

        String csv = new String(result.content());
        assertThat(result.exportedRows()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(3);
        assertThat(result.truncated()).isTrue();
        assertThat(csv).startsWith("\ufeff\"相同 (first)\",\"相同 (second)\"\r\n");
        assertThat(csv).contains("\"a,b\"");
        assertThat(csv).contains("\"\"nested\"\"");
        assertThat(queriedPages).containsExactly(1, 2);
        verify(queryService, times(2)).query(rule);
    }

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
        attribute.setSearchType("number");
        attribute.setOperators(List.of("equal"));
        attribute.setDisplaySelected(true);
        attribute.setLinkTemplate("/device/detail?guid={guid}");
        attribute.setCopyable(true);
        DataAttribute recordId = new DataAttribute();
        recordId.setEntity("msg");
        recordId.setName(MetaDataConstants.RECORD_ID_ATTRIBUTE);
        recordId.setLabel("记录ID");
        recordId.setColumnName(MetaDataConstants.RECORD_ID_COLUMN);
        recordId.setColumnType(MetaDataConstants.RECORD_ID_COLUMN_TYPE);
        recordId.setOperators(List.of("equal"));
        recordId.setDisplaySelected(false);
        DataAttribute insertTime = new DataAttribute();
        insertTime.setEntity("msg");
        insertTime.setName(MetaDataConstants.INSERT_TIME_ATTRIBUTE);
        insertTime.setLabel("创建时间");
        insertTime.setColumnName(MetaDataConstants.INSERT_TIME_COLUMN);
        insertTime.setColumnType(MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        insertTime.setSearchType("datetime");
        insertTime.setOperators(List.of("equal"));
        insertTime.setDisplaySelected(false);
        DataOperator operator = new DataOperator();
        operator.setName("equal");
        operator.setLabel("等于");
        MetaDataService metaDataService = mock(MetaDataService.class);
        when(metaDataService.getDataEntityByName("msg")).thenReturn(entity);
        when(metaDataService.getAllDataAttributeByEntity(entity))
                .thenReturn(List.of(recordId, insertTime, attribute));
        when(metaDataService.getDataOperatorByName("equal")).thenReturn(operator);
        RetrievalServiceImpl service = new RetrievalServiceImpl();
        ReflectionTestUtils.setField(service, "metaDataService", metaDataService);

        DataAttributeResultVo attributeResult = service.listAttribute("msg", null, 7);
        DataAttributeResultVo displayResult = service.listAttributeForDisplay("msg", null, 7);

        assertThat(attributeResult.getAttributeList()).hasSize(3);
        assertThat(attributeResult.getAttributeList())
                .filteredOn(result -> MetaDataConstants.INSERT_TIME_ATTRIBUTE.equals(result.getName()))
                .singleElement()
                .satisfies(result -> assertThat(result.getSearchType()).isEqualTo("datetime"));
        assertThat(attributeResult.getAttributeList())
                .filteredOn(result -> "guid".equals(result.getName()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getLinkTemplate()).isEqualTo("/device/detail?guid={guid}");
                    assertThat(result.isCopyable()).isTrue();
                    assertThat(result.getSearchType()).isEqualTo("number");
                });
        assertThat(displayResult.getSelectAttributeList()).singleElement()
                .satisfies(result -> {
                    assertThat(result.getLinkTemplate()).isEqualTo("/device/detail?guid={guid}");
                    assertThat(result.isCopyable()).isTrue();
                });
    }
}
