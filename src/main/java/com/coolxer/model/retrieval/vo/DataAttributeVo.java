package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataAttributeVo {

    private String name;

    private String label;

    private String retrievalType;

    private String displayType;

    private String description;

    private String linkTemplate;

    private boolean autoComplete;

    private boolean copyable;

    private List<OperatorVo> operatorList;

}
