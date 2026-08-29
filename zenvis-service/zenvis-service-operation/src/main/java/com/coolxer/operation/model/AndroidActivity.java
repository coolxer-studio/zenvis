package com.coolxer.operation.model;

import com.coolxer.operation.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
public class AndroidActivity {

    private AndroidActivityFact fact;

    private Date serverTime;

    @Data
    @NoArgsConstructor
    public static class AndroidActivityFact {
        private String type;
        private String buildDate;
        private Common common;
        private Activity base;
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

    @Data
    public static class Activity{
        public static List<String> ACTIVITY_KEYS;

        static {
            try {
                ACTIVITY_KEYS = JacksonUtil.toStringList("[\"status\",\"time\",\"className\",\"title\",\"intent\"]");
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        private String status;
        private String time;
        private String className;
        private String title;
        private String intent;
    }

}
