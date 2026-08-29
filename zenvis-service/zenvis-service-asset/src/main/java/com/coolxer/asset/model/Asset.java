package com.coolxer.asset.model;

import lombok.Data;

import java.util.List;

@Data
public abstract class Asset {

    private String id;
    private String assetType;
    private String source;
    private String type;
    private String owner;
    private String status;
    private List<String> label;
    private Boolean access;
    private String level;
    private String risk;
    private String riskInfo;

    public abstract String getPatternKey();

}
