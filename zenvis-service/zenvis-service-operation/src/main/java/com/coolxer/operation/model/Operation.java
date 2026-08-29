package com.coolxer.operation.model;

import lombok.Data;

@Data
public abstract class Operation {

    private String id;
    private String operationType;

    public abstract String getPatternKey();

}
