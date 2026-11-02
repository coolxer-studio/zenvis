package com.coolxer.model.dih.dashboard;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/** A complete replacement spec guarded by the currently published baseline. */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DashboardChangeSet {
    private String dashboardId;
    private Integer baseDashboardVersion;
    private String baseSpecDigest;
    private String baseFileDigest;
    private DashboardSpec spec;
}
