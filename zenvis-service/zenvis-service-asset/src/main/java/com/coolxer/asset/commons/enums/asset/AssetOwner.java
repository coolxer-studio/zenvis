package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产属主
 * 企业、终端客户
 *
 * @author hunter
 */
@Getter
public enum AssetOwner {

    /**
     * 企业
     */
    ENTERPRISE("企业"),

    /**
     * 终端客户
     */
    CUSTOMER("终端客户");

    AssetOwner(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> ownerMap = null;

    static {
        ownerMap = Arrays.stream(AssetOwner.values())
                .collect(Collectors.toMap(AssetOwner::name, AssetOwner::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetOwner owner : AssetOwner.values()) {
            if (Objects.equals(name, owner.name())) {
                return owner.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getOwnerNameList() {
        return Arrays.stream(AssetOwner.values()).map(AssetOwner::name).toList();
    }

    public static List<String> getOwnerDescriptionList() {
        return Arrays.stream(AssetOwner.values()).map(AssetOwner::getDescription).toList();
    }
} 