package com.coolxer.model.retrieval.meta;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MetaData {

    private List<DataEntity> entity = new ArrayList<>();

    private List<DataAttribute> attribute = new ArrayList<>();

    private List<DataOperator> operator = new ArrayList<>();

    public void merge(MetaData metaDataTmp) {
        this.entity.addAll(metaDataTmp.getEntity());
        this.attribute.addAll(metaDataTmp.getAttribute());
        // operator需要合并后去重(name)
        metaDataTmp.getOperator().stream().filter(
                operatorTmp -> !this.operator.stream().anyMatch(op -> op.getName().equals(operatorTmp.getName()))
        ).forEach(
                operatorTmp -> this.operator.add(operatorTmp)
        );
    }
}
