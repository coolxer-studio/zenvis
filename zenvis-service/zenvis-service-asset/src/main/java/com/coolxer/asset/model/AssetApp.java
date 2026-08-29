package com.coolxer.asset.model;

import lombok.Data;

import java.util.List;

@Data
public class AssetApp extends Asset{

    private String appName;
    private String appVersion;
    private String appType;
    private String platform;
    private String packageName;
    private String developer;
    private String publishTime;
    private String updateChannel;
    private String minOsVersion;
    private String targetOsVersion;
    private List<String> permissions;
    private List<String> dependencies;
    private String fileMd5;
    private String signatureMethod;
    private String certificateMd5;
    private String certificateDetails;
    private Object info;

    @Override
    public String getPatternKey() {
        return getFileMd5();
    }
}
