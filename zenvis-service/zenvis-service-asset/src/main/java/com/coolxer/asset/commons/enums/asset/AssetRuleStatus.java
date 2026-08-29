package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产规则状态
 * 未激活、激活、失效、不可用
 *
 * @author hunter
 */
@Getter
public enum AssetRuleStatus {

    /**
     * 未激活
     */
    INACTIVE("未激活"),

    /**
     * 激活
     */
    ACTIVE("激活"),

    /**
     * 失效
     */
    EXPIRED("失效"),

    /**
     * 不可用
     */
    UNAVAILABLE("不可用");

    AssetRuleStatus(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> statusMap = null;

    static {
        statusMap = Arrays.stream(AssetRuleStatus.values())
                .collect(Collectors.toMap(AssetRuleStatus::name, AssetRuleStatus::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetRuleStatus status : AssetRuleStatus.values()) {
            if (Objects.equals(name, status.name())) {
                return status.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getStatusNameList() {
        return Arrays.stream(AssetRuleStatus.values()).map(AssetRuleStatus::name).toList();
    }

    public static List<String> getStatusDescriptionList() {
        return Arrays.stream(AssetRuleStatus.values()).map(AssetRuleStatus::getDescription).toList();
    }
} 