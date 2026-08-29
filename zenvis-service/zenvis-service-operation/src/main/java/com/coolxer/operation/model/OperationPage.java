package com.coolxer.operation.model;

import lombok.Data;

@Data
public class OperationPage extends Operation {

    private String userId;
    private String startId;
    private String pagePath;
    private String pageName;
    private String referrer;
    private double longitude;
    private double latitude;
    private String country;
    private String province;
    private String city;
    private String county;
    private String netType;
    private String lanIp;
    private String wanIp;
    private String eventTime;


    @Override
    public String getPatternKey() {
        return getId();
    }
}
