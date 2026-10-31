package com.coolxer.service.retrieval;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsTimeRangeRulesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 17, 0);

    @Test
    void mapsTheLegacyAllAliasToAllTime() {
        assertThat(AnalyticsTimeRangeRules.normalizePreset("all")).isEqualTo("ALL_TIME");

        AnalyticsTimeRangeRules.Resolved resolved = AnalyticsTimeRangeRules.resolve(
                "ALL", null, null, "LAST_7_DAYS", true, ZONE, NOW);

        assertThat(resolved.preset()).isEqualTo("ALL_TIME");
        assertThat(resolved.start()).isNull();
        assertThat(resolved.end()).isNull();
    }

    @Test
    void rejectsUnsupportedPresetsAndInvalidCustomWindows() {
        assertThat(AnalyticsTimeRangeRules.validate(
                "LAST_3_YEARS", null, null, "LAST_7_DAYS", false, ZONE, NOW))
                .isEqualTo("不支持的时间范围: LAST_3_YEARS");
        assertThat(AnalyticsTimeRangeRules.validate(
                "CUSTOM", "2020-01-01 00:00:00", "2026-12-31 23:59:59",
                "ALL_TIME", true, ZONE, NOW))
                .isEqualTo("时间跨度不能超过3年");
        assertThat(AnalyticsTimeRangeRules.validate(
                "CUSTOM", null, "2026-12-31 23:59:59",
                "ALL_TIME", true, ZONE, NOW))
                .isEqualTo("start_time不能为空");
        assertThat(AnalyticsTimeRangeRules.validate(
                "ALL_TIME", null, null, "LAST_7_DAYS", false, ZONE, NOW))
                .isEqualTo("当前接口不支持ALL_TIME");
    }

    @Test
    void resolvesACustomWindowWithinTheSupportedSpan() {
        AnalyticsTimeRangeRules.Resolved resolved = AnalyticsTimeRangeRules.resolve(
                "CUSTOM", "2024-01-01 00:00:00", "2026-12-31 23:59:59",
                "ALL_TIME", true, ZONE, NOW);

        assertThat(resolved.preset()).isEqualTo("CUSTOM");
        assertThat(resolved.start()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
        assertThat(resolved.end()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
    }
}
