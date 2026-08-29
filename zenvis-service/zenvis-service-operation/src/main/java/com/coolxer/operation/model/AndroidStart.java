package com.coolxer.operation.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
public class AndroidStart {

    private AndroidStartFact fact;

    private Date serverTime;

    @Data
    @NoArgsConstructor
    public static class AndroidStartFact{
        private String type;
        private String buildDate;
        private Common common;
        private Map<String,Object> config;
    }

    @Data
    @NoArgsConstructor
    public static class Common {
        private String userId;
        private String guid;
        private long startId;
        private String sdkVersion;
        private int appId;
        private String appName;
        private String appPackage;
        private String appVersion;
        private String platform;
        private String manufacturer;
        private String model;
        private String system;
        private String systemVersion;
        private String netType;
        private String lanIp;
        private String wanIp;
        private double latitude;
        private double longitude;
        private String country;
        private String province;
        private String city;
        private String county;
        private String thoroughfare;
        private String clientTime;
    }

}
