package com.coolxer.risk.model;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RiskBaseLine extends Risk{

    private String userId;
    private String startId;
    private String assetId;
    private String configurationName;
    private String expectedValue;
    private String currentValue;
    private String verificationMethod;
    private String verificationResult;
    private List<String> label;
    private String riskLevel;
    private String netType;
    private String lanIp;
    private String wanIp;

    @Override
    public String getPatternKey() {
        return UUID.randomUUID().toString();
    }
}
