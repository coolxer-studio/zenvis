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
import java.util.Objects;

/**
 * 微信小程序设备的信息
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
public class WechatDevice {

    private WechatDeviceFact fact;

    private Date serverTime;

    public DeviceModel toDeviceModel(DeviceIdRule.Rule rule) {
        ArrayList<String> unionKeys = new ArrayList();
        for (String attributeName:rule.getKeyList()) {
            String attributeValue = CommonUtil.getAttribute(fact.uuid,attributeName);
            if(Objects.nonNull(attributeValue) && attributeValue.length()>0){
                unionKeys.add(String.format(ConstantUtil.FIELD_REDIS_KEY_FORMAT,PlatformEnum.WECHAT.getType(),attributeName,attributeValue));
            }
        }
        String deviceInfo = null;
        try {
            deviceInfo = JacksonUtil.toJson(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new DeviceModel(rule.getRate(),fact.common.guid,String.valueOf(fact.common.appId), PlatformEnum.WECHAT,unionKeys,deviceInfo);

    }

    @Data
    @NoArgsConstructor
    private static class WechatDeviceFact{
        private String type;
        private String buildDate;
        private Common common;
        private Device device;
        private Screen screen;
        private SystemSetting systemSetting;
        private AppBaseInfo appBaseInfo;
        private AppAuthorizeSetting appAuthorizeSetting;
        private Battery battery;
        private Uuid uuid;
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
    public static class Device{
        private String abi;
        private String deviceAbi;
        private Integer benchmarkLevel;
        private String brand;
        private String model;
        private String system;
        private String platform;
        private String cpuType;
        private String memorySize;
    }

    @Data
    @NoArgsConstructor
    public static class Screen{
        private Integer pixelRatio;
        private Integer screenHeight;
        private Integer screenWidth;
        private Integer windowHeight;
        private Integer windowWidth;
        private Integer statusBarHeight;
        private Integer screenTop;
    }

    @Data
    @NoArgsConstructor
    public static class SystemSetting{
        private Boolean bluetoothEnabled;
        private String deviceOrientation;
        private Boolean locationEnabled;
        private Boolean wifiEnabled;
    }

    @Data
    @NoArgsConstructor
    public static class AppBaseInfo{
        private String SDKVersion;
        private Boolean enableDebug;
        private String language;
        private String version;
        private Integer fontSizeScaleFactor;
        private Integer fontSizeSetting;
    }

    @Data
    @NoArgsConstructor
    public static class AppAuthorizeSetting{
        private String albumAuthorized;
        private String bluetoothAuthorized;
        private String cameraAuthorized;
        private String locationAuthorized;
        private String locationReducedAccuracy;
        private String microphoneAuthorized;
        private String notificationAlertAuthorized;
        private String notificationAuthorized;
        private String notificationBadgeAuthorized;
        private String notificationSoundAuthorized;
        private String phoneCalendarAuthorized;
    }

    @Data
    @NoArgsConstructor
    public static class Battery{
        private String level;
        private Boolean isCharging;
    }

    @Data
    @NoArgsConstructor
    public static class Uuid{
        private String GUID;
    }

}
