package com.coolxer.model.retrieval.query;

import lombok.Data;

import java.util.List;

@Data
public class ColumnCriteria {

    private String tableName;

    private String columnName;

    private String columnType;

    private String operatorName;

    private List<String> valueList;

    private String retrievalType;

}
