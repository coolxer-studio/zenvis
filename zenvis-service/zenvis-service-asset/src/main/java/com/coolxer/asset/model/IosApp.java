package com.coolxer.asset.model;

import com.coolxer.asset.commons.enums.PlatformEnum;
import com.coolxer.asset.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

/**
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
public class IosApp {

    private IosAppFact fact;

    private Date serverTime;

    @Data
    @NoArgsConstructor
    private static class IosAppFact {
        private String type;
        private String buildDate;
        private Common common;
        private ArrayList<App> installed = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    private static class Common {
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

    @Data
    @NoArgsConstructor
    private static class App{
        private String appName;
        private String packageName;
        private String versionName;
        private String versionCode;
        private String md5;
        private String certMd5;
        private String certIssuer;
        private Date installTime;
        private Date updateTime;
    }

    public AppModel toAppModel(){
        String appInfoJsonString = null;
        try {
            if(Objects.nonNull(fact.installed) && fact.installed.size() > 0){
                appInfoJsonString = JacksonUtil.toJson(fact.installed);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        // TODO unionKeys 暂时不需要用，后续使用做相似度判断
        return new AppModel(fact.common.guid,String.valueOf(fact.common.appId), PlatformEnum.IOS,null,appInfoJsonString);
    }
}
