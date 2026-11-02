package com.coolxer.model.dih.dataapp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class DataAppPackage {
    private DataAppSpec spec;
    private DataAppManifest manifest;
    private Map<String, String> files;
    private List<String> deletedFiles;
    private DataAppManifest previousManifest;

    public Map<String, String> copyFiles() {
        return new LinkedHashMap<>(files);
    }
}
