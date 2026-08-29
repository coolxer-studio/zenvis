package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产风险等级
 * 无风险、低风险、中风险、高风险、极高风险
 *
 * @author hunter
 */
@Getter
public enum AssetRiskLevel {

    /**
     * 无风险
     */
    NONE("无风险"),

    /**
     * 低风险
     */
    LOW("低风险"),

    /**
     * 中风险
     */
    MEDIUM("中风险"),

    /**
     * 高风险
     */
    HIGH("高风险"),

    /**
     * 极高风险
     */
    EXTREME("极高风险");

    AssetRiskLevel(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> riskLevelMap = null;

    static {
        riskLevelMap = Arrays.stream(AssetRiskLevel.values())
                .collect(Collectors.toMap(AssetRiskLevel::name, AssetRiskLevel::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetRiskLevel riskLevel : AssetRiskLevel.values()) {
            if (Objects.equals(name, riskLevel.name())) {
                return riskLevel.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getRiskLevelNameList() {
        return Arrays.stream(AssetRiskLevel.values()).map(AssetRiskLevel::name).toList();
    }

    public static List<String> getRiskLevelDescriptionList() {
        return Arrays.stream(AssetRiskLevel.values()).map(AssetRiskLevel::getDescription).toList();
    }
} 