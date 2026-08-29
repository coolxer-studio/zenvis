package com.coolxer.asset.commons.enums.asset;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产规则动作
 * 合并、打标记
 *
 * @author hunter
 */
@Getter
public enum AssetRuleAction {

    /**
     * 合并
     */
    MERGE("合并"),

    /**
     * 打标记
     */
    MARK("打标记");

    AssetRuleAction(String description) {
        this.description = description;
    }

    private final String description;

    public static Map<String, String> actionMap = null;

    static {
        actionMap = Arrays.stream(AssetRuleAction.values())
                .collect(Collectors.toMap(AssetRuleAction::name, AssetRuleAction::getDescription));
    }

    public static String getDescriptionByName(String name) {
        for (AssetRuleAction action : AssetRuleAction.values()) {
            if (Objects.equals(name, action.name())) {
                return action.getDescription();
            }
        }
        return "unknown";
    }

    public static List<String> getActionNameList() {
        return Arrays.stream(AssetRuleAction.values()).map(AssetRuleAction::name).toList();
    }

    public static List<String> getActionDescriptionList() {
        return Arrays.stream(AssetRuleAction.values()).map(AssetRuleAction::getDescription).toList();
    }
} 