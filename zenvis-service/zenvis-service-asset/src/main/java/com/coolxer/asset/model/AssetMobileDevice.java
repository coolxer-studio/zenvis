package com.coolxer.asset.model;

import com.coolxer.asset.commons.enums.asset.*;
import com.coolxer.asset.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class AssetMobileDevice extends Asset{

    private String areaCode;
    private String country;
    private String province;
    private String city;
    private String county;
    private String netType;
    private String lanIp;
    private String wanIp;
    private String brand;
    private String model;
    private String manufacturer;
    private String systemName;
    private String systemVersion;
    private String androidId;
    private String buildId;
    private String bluetoothMac;
    private String displayInfo;
    private String appMode;
    private String instructionSet1;
    private String deviceFingerprint;
    private String hostname;
    private String gyroscopeInfo;
    private String location;
    private String cellAreaCode;
    private Integer nearbyCellCount;
    private String bootloader;
    private String deviceForm;
    private String screenResolution;
    private String osName;
    private String imei;
    private String imsi;
    private String networkMac;
    private String deviceInfo;
    private String sdkVersion;
    private String systemBoot;
    private String serialNumber;
    private String hardwareName;
    private String instructionSet2;
    private String motherboardInfo;
    private String buildDate;
    private String timezone;
    private String wifiMac;
    private String cellId;
    private String carrierType;
    private Object info;

    @Override
    public String getPatternKey() {
        return this.manufacturer;
    }
}
