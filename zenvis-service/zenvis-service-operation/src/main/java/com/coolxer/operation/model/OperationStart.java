package com.coolxer.operation.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class OperationStart extends Operation {

    private String userId;
    private String deviceId;
    private String deviceModel;
    private String deviceOs;
    private String appId;
    private String appName;
    private String packageName;
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
