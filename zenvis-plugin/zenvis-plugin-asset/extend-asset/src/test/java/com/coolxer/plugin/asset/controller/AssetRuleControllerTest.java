package com.coolxer.plugin.asset.controller;

import com.coolxer.plugin.asset.api.ResponseWrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetRuleControllerTest {

    @Test
    void returnsAllAssetTypesAsSelectOptions() {
        AssetRuleController controller = new AssetRuleController(null);

        ResponseWrap<?> response = controller.assets();
        Map<?, ?> data = (Map<?, ?>) response.getData();
        List<?> options = (List<?>) data.get("options");

        assertThat(options).hasSize(10);
        assertThat(options).anySatisfy(option ->
                assertThat(option).isEqualTo(Map.of("label", "服务器设备", "value", "HOST")));
        assertThat(options).anySatisfy(option ->
                assertThat(option).isEqualTo(Map.of("label", "API资产", "value", "API")));
        assertThat(options).anySatisfy(option ->
                assertThat(option).isEqualTo(Map.of("label", "文件", "value", "FILE")));
    }
}
