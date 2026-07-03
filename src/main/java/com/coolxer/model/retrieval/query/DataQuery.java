package com.coolxer.model.retrieval.query;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataQuery {

    private String tableName;

    private List<ColumnCriteria> columnCriteria;

    private String criteriaLogic;

    private ColumnCriteriaExpression criteriaExpression;

    private String sql;

    private List<DisplayColumn> displayColumnList;

    private List<Map<String, Object>> cacheList;

    private Map<String, Object> cursor;

}
