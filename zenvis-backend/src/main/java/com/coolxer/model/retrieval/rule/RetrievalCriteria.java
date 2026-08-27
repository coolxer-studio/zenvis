package com.coolxer.model.retrieval.rule;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import lombok.Data;

import java.util.List;

@Data
public class RetrievalCriteria {

    private DataEntity entity;

    private DataAttribute attribute;

    private DataOperator operator;

    private List<String> valueList;

}
