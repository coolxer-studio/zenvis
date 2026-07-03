package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataAttributeVo {

    private String name;

    private String label;

    private String retrievalType;

    private String description;

    private boolean aggregateLink;

    private boolean autoComplete;

    private List<OperatorVo> operatorList;

}
