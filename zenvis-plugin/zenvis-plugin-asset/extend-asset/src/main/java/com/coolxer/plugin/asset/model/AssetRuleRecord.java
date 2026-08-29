package com.coolxer.plugin.asset.model;

import java.sql.Timestamp;

public record AssetRuleRecord(
        Long id,
        String name,
        String description,
        String asset,
        String conditions,
        String action,
        String status,
        String result,
        Timestamp createTime,
        Timestamp updateTime
) {
}
