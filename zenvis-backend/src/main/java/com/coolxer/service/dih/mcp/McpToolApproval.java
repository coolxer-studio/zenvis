package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolRiskLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolApproval {
    McpApprovalPolicy value();
    McpToolRiskLevel risk();
}
