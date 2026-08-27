package com.coolxer.model.retrieval.rule;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import lombok.Data;

import java.util.List;

@Data
public class DisplayAttribute {

    private DataEntity entity;

    private List<DataAttribute> attributeList;

}
