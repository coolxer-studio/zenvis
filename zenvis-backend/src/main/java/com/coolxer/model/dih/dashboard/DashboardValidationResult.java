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
public class DashboardValidationResult {
    private boolean passed;
    private String status;
    private String workflowId;
    private String dashboardId;
    private Integer dashboardVersion;
    private String mode;
    private String previousMode;
    private String candidateDigest;
    private String validationDigest;
    private String specDigest;
    private String metaDigest;
    private String fileDigest;
    private DashboardSpec spec;
    private List<DashboardArtifact> files = new ArrayList<>();
    private List<DashboardDiagnostic> diagnostics = new ArrayList<>();
    private Map<String, Object> preview = new LinkedHashMap<>();
    private DashboardManifest manifest;
    private String message;
}
