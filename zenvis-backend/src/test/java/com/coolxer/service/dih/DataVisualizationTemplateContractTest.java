package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataVisualizationTemplateContractTest {

    private static final String ROOT = "demo/data-visualization/";

    @Test
    void jsonTemplatesAreValidAndUseCanonicalEntity() throws Exception {
        List<String> files = List.of(
                "chart-amis.json",
                "user-event-page.json",
                "user-event-app-site.json",
                "user-event-app-home.json",
                "user-event-app-trend.json",
                "user-event-dashboard.json");

        for (String file : files) {
            String content = resource(file);
            JacksonConfig.OBJECT_MAPPER.readTree(content);
            assertThat(content)
                    .as(file)
                    .doesNotContain("/entity/user-event/")
                    .doesNotContain("example.com");
        }
        assertThat(resource("user-event-page.json"))
                .contains("/entity/user_event/")
                .contains("\"tpl\": \"<span class='label label-info'>${tags}</span>\"")
                .contains("\"type\": \"json\"");
        assertThat(resource("user-event-dashboard.json"))
                .contains("LAST_24_HOURS")
                .contains("/entity/overview/query")
                .contains("/entity/summary/query")
                .contains("/entity/aggregate/query")
                .contains("/entity/histogram/query");
    }

    @Test
    void htmlTemplatesUseRealApisAndExposeOperationalStates()
            throws Exception {
        for (String file : List.of(
                "user-event-page.html",
                "user-event-dashboard.html")) {
            String content = resource(file);
            assertThat(content)
                    .as(file)
                    .contains("user_event")
                    .contains("/zenvis/api/v1")
                    .contains("/api/v1")
                    .contains("暂无数据")
                    .doesNotContain("Math.random")
                    .doesNotContain("example.com")
                    .doesNotContain("/entity/user-event/");
        }
        assertThat(resource("user-event-page.html"))
                .contains("数据请求失败")
                .contains("row.comparison_value")
                .contains("normalizeTags(row.tags)")
                .contains("Number(row.reliability).toFixed(2)")
                .contains("JSON.stringify(detail ?? {}, null, 2)");
        assertThat(resource("user-event-dashboard.html"))
                .contains("数据刷新失败")
                .contains("REFRESH_INTERVAL_MS = 60000")
                .contains("updatedAt")
                .contains("retry")
                .contains("visibilitychange")
                .contains("LAST_24_HOURS");
    }

    private String resource(String fileName) throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(ROOT + fileName)) {
            assertThat(stream).as(fileName).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
