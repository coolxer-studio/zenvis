package com.coolxer.model.retrieval.query;

import lombok.Data;

import java.util.List;

@Data
public class ColumnCriteriaExpression {

    private String type;

    private String logic;

    private ColumnCriteria criteria;

    private List<ColumnCriteriaExpression> children;

}
