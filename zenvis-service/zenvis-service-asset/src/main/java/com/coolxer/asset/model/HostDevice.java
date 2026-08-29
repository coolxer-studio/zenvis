package com.coolxer.asset.model;

import com.coolxer.asset.commons.constants.ConstantUtil;
import com.coolxer.asset.commons.enums.PlatformEnum;
import com.coolxer.asset.utils.CommonUtil;
import com.coolxer.asset.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Host平台设备的信息
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
public class HostDevice {

    private HostDeviceFact fact;

    private Date serverTime;

    public DeviceModel toDeviceModel(DeviceIdRule.Rule rule) {
        ArrayList<String> unionKeys = new ArrayList();
        for (String attributeName:rule.getKeyList()) {
            String attributeValue = CommonUtil.getAttribute(fact.uuid,attributeName);
            if(Objects.nonNull(attributeValue) && attributeValue.length()>0){
                unionKeys.add(String.format(ConstantUtil.FIELD_REDIS_KEY_FORMAT,PlatformEnum.HOST.getType(),attributeName,attributeValue));
            }
        }
        String deviceInfo = null;
        try {
            deviceInfo = JacksonUtil.toJson(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new DeviceModel(rule.getRate(),fact.common.guid,String.valueOf(fact.common.appId), PlatformEnum.HOST,unionKeys,deviceInfo);

    }

    @Data
    @NoArgsConstructor
    private static class HostDeviceFact{
        private String type;
        private String buildDate;
        private Common common;
        private Device device;
        private Screen screen;
        private Cpu cpu;
        private Map<String,Object> battery;
        private Uuid uuid;
        private Network network;
        private Storage storage;
        private Map<String,Object> opengl;
        private Map<String,Object> prop;
        private Map<String,Object> build;
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
    public static class Screen{
        private Integer width;
        private Integer height;
        private Double brightness;
        private Double density;
        private Integer rotation;
    }
    @Data
    @NoArgsConstructor
    public static class Cpu{
        private Integer totalCore;
        private Integer usedCore;
        private String type;
        private String abi;
    }
    @Data
    @NoArgsConstructor
    public static class Storage{
        private Double totalDisk;
        private Double usedDisk;
        private Double totalRam;
        private Double usedRam;
    }


    @Data
    @NoArgsConstructor
    public static class Device{
        private String type;
        private String name;
        private String systemName;
        private String systemVersion;
        private String buildId;
        private String modelCode;
        private String countryCode;
        private String language;
        private String timeZone;
        private String currency;
        private Date startElapsedTime;
    }
    @Data
    @NoArgsConstructor
    public static class Uuid{
        private String macEth0;
        private String serial;
        private String bootId;
        private String guid;

    }

    @Data
    @NoArgsConstructor
    public static class Network{
        private String lanIp;
        private String mask;
        private String broadcast;
        private String routing;
        private String signal;
        private String type;
    }
}
