package com.coolxer.model.dih.dataapp;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ownership and drift-control manifest stored as _app.json.
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DataAppManifest {

    private String schemaVersion = "1.0";
    private String appId;
    private Integer appVersion;
    private String mode;
    private String name;
    private String description;
    private String homePageId;
    private String specDigest;
    private String metaDigest;
    private DataAppSpec spec;
    private String menuType;
    private Integer menuId;
    private String menuName;
    private String publishStatus;
    private List<DataAppPageRef> pages = new ArrayList<>();
    private Map<String, String> files = new LinkedHashMap<>();
    private List<String> compatibilityFiles = new ArrayList<>();
    private String createdAt;
    private String updatedAt;

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DataAppPageRef {
        private String pageId;
        private String title;
        private String path;
    }
}
