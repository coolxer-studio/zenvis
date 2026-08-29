package com.coolxer.risk.model;

import lombok.Data;

import java.util.List;

@Data
public abstract class Risk {

    private String id;
    private String riskType;

    public abstract String getPatternKey();

}
