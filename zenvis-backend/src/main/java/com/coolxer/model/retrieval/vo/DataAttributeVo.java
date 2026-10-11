package com.coolxer.model.retrieval.vo;

import com.coolxer.model.retrieval.meta.DataAttribute;
import lombok.Data;

import java.util.List;

@Data
public class DataAttributeVo {

    private String name;

    private String label;

    private String retrievalType;

    private String searchType;

    private String displayType;

    private String description;

    private String linkTemplate;

    private DataAttribute.LinkCondition linkCondition;

    private boolean autoComplete;

    private boolean copyable;

    private List<OperatorVo> operatorList;

}
