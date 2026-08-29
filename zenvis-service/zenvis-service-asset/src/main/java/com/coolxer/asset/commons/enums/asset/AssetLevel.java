package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产等级
 * 辅助资产、一般资产、次要资产、重要资产、关键资产
 *
 * @author hunter
 */
@Getter
public enum AssetLevel {

    /**
     * 辅助资产
     */
    AUXILIARY("辅助资产"),

    /**
     * 一般资产
     */
    GENERAL("一般资产"),

    /**
     * 次要资产
     */
    MINOR("次要资产"),

    /**
     * 重要资产
     */
    IMPORTANT("重要资产"),

    /**
     * 关键资产
     */
    CRITICAL("关键资产");

    AssetLevel(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> levelMap = null;

    static {
        levelMap = Arrays.stream(AssetLevel.values())
                .collect(Collectors.toMap(AssetLevel::name, AssetLevel::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetLevel level : AssetLevel.values()) {
            if (Objects.equals(name, level.name())) {
                return level.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getLevelNameList() {
        return Arrays.stream(AssetLevel.values()).map(AssetLevel::name).toList();
    }

    public static List<String> getLevelDescriptionList() {
        return Arrays.stream(AssetLevel.values()).map(AssetLevel::getDescription).toList();
    }
} 