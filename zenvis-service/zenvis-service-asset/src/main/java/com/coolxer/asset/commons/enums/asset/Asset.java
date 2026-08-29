package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产类型
 * 服务器设备、移动端设备、PC端设备、IOT设备、探针资产、应用资产、服务资产、API资产、日志、文件
 *
 * @author hunter
 */
@Getter
public enum Asset {

    /**
     * 服务器设备
     */
    HOST("服务器设备"),

    /**
     * 移动端设备
     */
    MOBILE("移动端设备"),

    /**
     * PC端设备
     */
    PC("PC端设备"),

    /**
     * IOT设备
     */
    IOT("IOT设备"),

    /**
     * 探针资产
     */
    PROBE("探针资产"),

    /**
     * 应用资产
     */
    APP("应用资产"),

    /**
     * 服务资产
     */
    SERVICE("服务资产"),

    /**
     * API资产
     */
    API("API资产"),

    /**
     * 日志
     */
    LOG("日志"),

    /**
     * 文件
     */
    FILE("文件");

    Asset(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> assetMap = null;

    static {
        assetMap = Arrays.stream(Asset.values())
                .collect(Collectors.toMap(Asset::name, Asset::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (Asset asset : Asset.values()) {
            if (Objects.equals(name, asset.name())) {
                return asset.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getAssetNameList() {
        return Arrays.stream(Asset.values()).map(Asset::name).toList();
    }

    public static List<String> getAssetDescriptionList() {
        return Arrays.stream(Asset.values()).map(Asset::getDescription).toList();
    }
} 