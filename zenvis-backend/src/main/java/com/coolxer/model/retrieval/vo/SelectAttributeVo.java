package com.coolxer.model.retrieval.vo;

import com.coolxer.model.retrieval.meta.DataAttribute;
import lombok.Data;

import java.util.List;

@Data
public class SelectAttributeVo {

    private String name;

    private String label;

    private String displayType;

    private String operatorName;

    private String linkTemplate;

    private DataAttribute.LinkCondition linkCondition;

    private boolean copyable;

    private List<String> valueList;

}
