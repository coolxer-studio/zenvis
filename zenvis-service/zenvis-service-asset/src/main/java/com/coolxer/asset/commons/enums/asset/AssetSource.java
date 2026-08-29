package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产来源
 * 手动录入、agent监测、主动探测、三方平台对接、其他
 *
 * @author hunter
 */
@Getter
public enum AssetSource {

    /**
     * 手动录入
     */
    MANUAL("手动录入"),

    /**
     * agent监测
     */
    AGENT("agent监测"),

    /**
     * 主动探测
     */
    PROBE("主动探测"),

    /**
     * 三方平台对接
     */
    THIRD_PARTY("三方平台对接"),

    /**
     * 其他
     */
    OTHER("其他");

    AssetSource(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> sourceMap = null;

    static {
        sourceMap = Arrays.stream(AssetSource.values())
                .collect(Collectors.toMap(AssetSource::name, AssetSource::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetSource source : AssetSource.values()) {
            if (Objects.equals(name, source.name())) {
                return source.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getSourceNameList() {
        return Arrays.stream(AssetSource.values()).map(AssetSource::name).toList();
    }

    public static List<String> getSourceDescriptionList() {
        return Arrays.stream(AssetSource.values()).map(AssetSource::getDescription).toList();
    }
}
