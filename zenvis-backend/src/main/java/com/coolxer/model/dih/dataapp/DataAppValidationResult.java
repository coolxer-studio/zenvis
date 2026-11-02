package com.coolxer.model.dih.dataapp;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DataAppValidationResult {
    private boolean passed;
    private String status;
    private String workflowId;
    private String appId;
    private Integer appVersion;
    private String mode;
    private String previousMode;
    private String candidateDigest;
    private String validationDigest;
    private String specDigest;
    private String metaDigest;
    private DataAppSpec spec;
    private List<DataAppArtifact> files = new ArrayList<>();
    private List<String> deletedFiles = new ArrayList<>();
    private List<DataAppDiagnostic> diagnostics = new ArrayList<>();
    private Map<String, Object> preview = new LinkedHashMap<>();
    private DataAppManifest menu;
    private String message;
}
