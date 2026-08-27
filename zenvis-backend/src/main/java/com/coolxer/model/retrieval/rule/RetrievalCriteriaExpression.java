package com.coolxer.model.retrieval.rule;

import lombok.Data;

import java.util.List;

@Data
public class RetrievalCriteriaExpression {

    private String type;

    private String logic;

    private RetrievalCriteria criteria;

    private List<RetrievalCriteriaExpression> children;

}
