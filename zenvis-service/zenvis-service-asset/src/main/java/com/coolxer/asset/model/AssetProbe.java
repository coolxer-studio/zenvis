package com.coolxer.asset.model;

import lombok.Data;

import java.util.List;

@Data
public class AssetProbe extends Asset{

    private String probeName;
    private String probeVersion;
    private String probeType;
    private String language;
    private String framework;
    private List<String> compatibleVersions;
    private List<String> dataCollectionTypes;
    private String encryptionMethod;
    private String authenticationMethod;
    private String dataTransmissionProtocol;
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
