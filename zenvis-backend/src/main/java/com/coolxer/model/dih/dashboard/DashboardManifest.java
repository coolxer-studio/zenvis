package com.coolxer.model.dih.dashboard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ownership, drift and publish-state manifest for a managed dashboard. */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DashboardManifest {
    private String schemaVersion = "1.0";
    private String dashboardId;
    private Integer dashboardVersion;
    private String name;
    private String description;
    private String mode;
    private String theme;
    private String dashboardType;
    private Integer dashboardRecordId;
    private String dashboardCode;
    private String configIndex;
    private String htmlPath;
    private String specDigest;
    private String metaDigest;
    private String fileDigest;
    private String publishStatus;
    private DashboardSpec spec;
    private Map<String, String> files = new LinkedHashMap<>();
    private String createdAt;
    private String updatedAt;
}
