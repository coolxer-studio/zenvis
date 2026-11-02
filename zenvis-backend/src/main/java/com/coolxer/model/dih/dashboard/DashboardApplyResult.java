package com.coolxer.model.dih.dashboard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DashboardApplyResult {
    private String status;
    private boolean applied;
    private String dashboardId;
    private Integer dashboardVersion;
    private String mode;
    private String fileDigest;
    private Integer dashboardRecordId;
    private String dashboardType;
    private String dashboardCode;
    private List<DashboardArtifact> files = new ArrayList<>();
    private Map<String, Object> dashboard = new LinkedHashMap<>();
    private List<DashboardDiagnostic> diagnostics = new ArrayList<>();
    private String message;
}
