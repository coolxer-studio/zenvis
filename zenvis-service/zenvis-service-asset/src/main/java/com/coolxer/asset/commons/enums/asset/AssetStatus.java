package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产状态
 * 在网、停用、下线、删除
 *
 * @author hunter
 */
@Getter
public enum AssetStatus {

    /**
     * 在网
     */
    ONLINE("在网"),

    /**
     * 停用
     */
    DISABLED("停用"),

    /**
     * 下线
     */
    OFFLINE("下线"),

    /**
     * 删除
     */
    DELETED("删除");

    AssetStatus(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> statusMap = null;

    static {
        statusMap = Arrays.stream(AssetStatus.values())
                .collect(Collectors.toMap(AssetStatus::name, AssetStatus::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetStatus status : AssetStatus.values()) {
            if (Objects.equals(name, status.name())) {
                return status.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getStatusNameList() {
        return Arrays.stream(AssetStatus.values()).map(AssetStatus::name).toList();
    }

    public static List<String> getStatusDescriptionList() {
        return Arrays.stream(AssetStatus.values()).map(AssetStatus::getDescription).toList();
    }
} 