package com.coolxer.web;

import lombok.Data;

/**
 * desc
 */
@Data
//@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class DetectionResult {
    private String description;
    private String ruleName;
    private String ruleId;
    private String severity;
    private int riskScore;
}
