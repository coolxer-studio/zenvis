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
public class DataAppApplyResult {
    private String status;
    private boolean applied;
    private boolean partialSuccess;
    private String appId;
    private Integer appVersion;
    private String mode;
    private String manifestDigest;
    private List<DataAppArtifact> files = new ArrayList<>();
    private Map<String, Object> menu = new LinkedHashMap<>();
    private List<DataAppDiagnostic> diagnostics = new ArrayList<>();
    private String message;
}
