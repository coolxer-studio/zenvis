package com.coolxer.asset.model;

import com.coolxer.asset.commons.constants.ConstantUtil;
import com.coolxer.asset.commons.enums.PlatformEnum;
import com.coolxer.asset.utils.CommonUtil;
import com.coolxer.asset.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Ios设备信息
 *
 * @author yaoqi.li
 */

@Slf4j
@Data
@NoArgsConstructor
public class IosDevice {

  private IosDeviceFact fact;

  private Date serverTime;

  public DeviceModel toDeviceModel(DeviceIdRule.Rule rule) {
    ArrayList<String> unionKeys = new ArrayList();
    for (String attributeName:rule.getKeyList()) {
      String attributeValue = CommonUtil.getAttribute(fact.uuid,attributeName);
      if("idfa".equalsIgnoreCase(attributeName) && "00000000-0000-0000-0000-000000000000".equals(attributeValue)){
        continue;
      }
      if(Objects.nonNull(attributeValue) && attributeValue.length()>0){
        unionKeys.add(String.format(ConstantUtil.FIELD_REDIS_KEY_FORMAT,PlatformEnum.IOS.getType(),attributeName,attributeValue));
      }
    }
    String deviceInfo = null;
    try {
      deviceInfo = JacksonUtil.toJson(this);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return new DeviceModel(rule.getRate(),fact.common.guid,String.valueOf(fact.common.appId), PlatformEnum.IOS,unionKeys,deviceInfo);
  }

  @Data
  @NoArgsConstructor
  private static class IosDeviceFact {
    private String type;
    private String buildDate;
    private Common common;
    private Device device;
    private Screen screen;
    private Cpu cpu;
    private Battery battery;
    private Uuid uuid;
    private Network network;
    private Storage storage;
    private Map<String, Object> sysctl;
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
  private static class Screen {
    private Integer width;
    private Integer height;
    private Double brightness;
  }

  @Data
  @NoArgsConstructor
  private static class Cpu {
    private Integer totalCore;
    private Integer usedCore;
    private String type;
    private String abi;
  }

  @Data
  @NoArgsConstructor
  private static class Storage {
    private Double totalDisk;
    private Double usedDisk;
    private Double totalRam;
    private Double usedRam;
  }

  @Data
  @NoArgsConstructor
  private static class Battery {
    private Double capacity;
    private Double level;
    private Boolean charging;
    private Boolean charged;
  }

  @Data
  @NoArgsConstructor
  private static class Device {
    private String type;
    private String name;
    private Boolean simulator;
    private String systemName;
    private String systemVersion;
    private String buildVersion;
    private String kernelVersion;
    private String modelCode;
    private String countryCode;
    private String language;
    private String timeZone;
    private String currency;
    private Date activationTime;
    private Date startupTime;
    private Date fileCreationTime;
    private Date fileModificationTime;
    private Boolean wireConnected;
  }

  @Data
  @NoArgsConstructor
  private static class Uuid {
    private String cfuuid;
    private String idfa;
    private String idfv;
    private String guid;
  }

  @Data
  @NoArgsConstructor
  private static class Network {
    private Integer simCount;
    private String cellType;
    private String operators;
    private String country;
    private String mcc;
    private String iso;
    private String mnc;
    private String allowsVoip;
    private String lanIp;
    private String mask;
    private String broadcast;
    private String routing;
    private String signal;
    private String type;
  }


}
