package com.coolxer.plugin.asset.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetRuleViewTest {

    @Test
    void mapsPersistedValuesToCompatibleView() {
        AssetRuleRecord record = new AssetRuleRecord(
                7L,
                "标记高风险主机",
                "测试规则",
                "HOST",
                "{\"risk\":\"HIGH\"}",
                "MARK",
                "ACTIVE",
                "ok",
                Timestamp.valueOf("2026-01-01 10:00:00"),
                Timestamp.valueOf("2026-01-02 10:00:00")
        );

        AssetRuleView view = AssetRuleView.from(record, new ObjectMapper());

        assertThat(view.getId()).isEqualTo(7L);
        assertThat(view.getAssetDesc()).isEqualTo("服务器设备");
        assertThat(view.getActionDesc()).isEqualTo("打标记");
        assertThat(view.getStatusDesc()).isEqualTo("激活");
        assertThat(view.getConditions()).isEqualTo(Map.of("risk", "HIGH"));
    }
}
