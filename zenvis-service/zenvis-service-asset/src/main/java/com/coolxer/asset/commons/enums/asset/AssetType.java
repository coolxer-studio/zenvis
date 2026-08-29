package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产类型
 * 业务类、支撑类
 *
 * @author hunter
 */
@Getter
public enum AssetType {

    /**
     * 业务类
     */
    BUSINESS("业务类"),

    /**
     * 支撑类
     */
    SUPPORT("支撑类");

    AssetType(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> typeMap = null;

    static {
        typeMap = Arrays.stream(AssetType.values())
                .collect(Collectors.toMap(AssetType::name, AssetType::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetType type : AssetType.values()) {
            if (Objects.equals(name, type.name())) {
                return type.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getTypeNameList() {
        return Arrays.stream(AssetType.values()).map(AssetType::name).toList();
    }

    public static List<String> getTypeDescriptionList() {
        return Arrays.stream(AssetType.values()).map(AssetType::getDescription).toList();
    }
} 