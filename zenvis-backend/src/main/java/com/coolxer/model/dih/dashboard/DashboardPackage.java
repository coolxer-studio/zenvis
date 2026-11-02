package com.coolxer.model.dih.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class DashboardPackage {
    private DashboardSpec spec;
    private DashboardManifest manifest;
    private Map<String, String> files;
    private String manifestPath;
    private DashboardManifest previousManifest;

    public Map<String, String> copyFiles() {
        return new LinkedHashMap<>(files);
    }
}
