package com.coolxer.model.retrieval.query;

import com.coolxer.model.retrieval.rule.RetrievalPageable;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DataQueryContext {

    private String contextId;

    private List<DataQuery> queryChain;

    private RetrievalPageable pageable;

    private List<Map<String, Object>> resultList;

    private DataQuery previousQuery;

    private BigDecimal total;

}
