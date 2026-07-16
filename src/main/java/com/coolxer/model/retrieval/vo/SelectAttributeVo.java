package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.List;

@Data
public class SelectAttributeVo {

    private String name;

    private String label;

    private String displayType;

    private String operatorName;

    private String linkTemplate;

    private List<String> valueList;

}
