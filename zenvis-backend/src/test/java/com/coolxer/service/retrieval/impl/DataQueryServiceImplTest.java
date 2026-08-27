package com.coolxer.service.retrieval.impl;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.DataQuery;
import com.coolxer.model.retrieval.rule.DisplayAttribute;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.service.retrieval.QueryEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataQueryServiceImplTest {

    @Test
    void appendsHiddenRecordIdWhenDisplayedFieldLinkDependsOnIt() {
        QueryEngine queryEngine = mock(QueryEngine.class);
        when(queryEngine.queryWithRetrieval(any(DataQuery.class), nullable(RetrievalPageable.class)))
                .thenReturn(Map.of("data", List.of(), "total", BigDecimal.ZERO));
        DataQueryServiceImpl service = new DataQueryServiceImpl();
        service.queryEngine = queryEngine;

        service.query(ruleWithLink("/detail?record_id={zenvis_id}"));

        ArgumentCaptor<DataQuery> queryCaptor = ArgumentCaptor.forClass(DataQuery.class);
        verify(queryEngine).queryWithRetrieval(queryCaptor.capture(), nullable(RetrievalPageable.class));
        assertThat(queryCaptor.getValue().getDisplayColumnList())
                .extracting(column -> column.getDisplayName())
                .containsExactly("event_id", MetaDataConstants.RECORD_ID_ATTRIBUTE);
    }

    @Test
    void doesNotAppendRecordIdWhenLinkUsesVisibleBusinessField() {
        QueryEngine queryEngine = mock(QueryEngine.class);
        when(queryEngine.queryWithRetrieval(any(DataQuery.class), nullable(RetrievalPageable.class)))
                .thenReturn(Map.of("data", List.of(), "total", BigDecimal.ZERO));
        DataQueryServiceImpl service = new DataQueryServiceImpl();
        service.queryEngine = queryEngine;

        service.query(ruleWithLink("/detail?event_id={event_id}"));

        ArgumentCaptor<DataQuery> queryCaptor = ArgumentCaptor.forClass(DataQuery.class);
        verify(queryEngine).queryWithRetrieval(queryCaptor.capture(), nullable(RetrievalPageable.class));
        assertThat(queryCaptor.getValue().getDisplayColumnList())
                .extracting(column -> column.getDisplayName())
                .containsExactly("event_id");
    }

    private RetrievalRule ruleWithLink(String linkTemplate) {
        DataEntity entity = new DataEntity();
        entity.setName("event");
        entity.setTableName("event");

        DataAttribute eventId = new DataAttribute();
        eventId.setEntity("event");
        eventId.setName("event_id");
        eventId.setColumnName("event_id");
        eventId.setColumnType("String");
        eventId.setLinkTemplate(linkTemplate);

        DisplayAttribute displayAttribute = new DisplayAttribute();
        displayAttribute.setEntity(entity);
        displayAttribute.setAttributeList(List.of(eventId));

        RetrievalRule rule = new RetrievalRule();
        rule.setRetrievalCriteria(List.of());
        rule.setDisplayAttributes(List.of(displayAttribute));
        return rule;
    }
}
