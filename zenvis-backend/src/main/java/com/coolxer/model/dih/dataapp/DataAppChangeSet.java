package com.coolxer.model.dih.dataapp;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A page-level change set applied to an existing application version.
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DataAppChangeSet {

    private String appId;
    private String name;
    private String description;
    private Integer baseAppVersion;
    private String baseSpecDigest;
    private String homePageId;
    private List<DataAppSpec.DataAppPage> putPages = new ArrayList<>();
    private List<String> deletePageIds = new ArrayList<>();
    private DataAppSpec.DataAppMenu menu;
}
