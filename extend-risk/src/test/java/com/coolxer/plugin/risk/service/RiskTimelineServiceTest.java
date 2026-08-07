package com.coolxer.plugin.risk.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskTimelineServiceTest {

    @Test
    void buildsTimelineWithDefaultTitleAndAlignedSeries() {
        Map<String, Object> result = new RiskTimelineService().timeline(null);

        assertThat(result.get("title_name")).isEqualTo("全部风险");
        List<?> xAxis = (List<?>) result.get("x_axis");
        List<?> series = (List<?>) result.get("series");
        assertThat(xAxis).hasSizeGreaterThan(2_000);
        assertThat(series).hasSameSizeAs(xAxis);
    }
}
