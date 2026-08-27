package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataAccessDemoResourceContractTest {

    private static final String ROOT = "demo/data-access/";

    @Test
    void serviceLoadsAllPresetContentFromClasspathResources() throws Exception {
        String requirement = resource("user-event-requirement.md");
        String meta = resource("user-event-meta.json");
        String push = resource("user-event-push.toml");
        DataAccessDemoResponseService service = new DataAccessDemoResponseService();

        assertThat(DataAccessDemoResponseService.USER_EVENT_EXAMPLE_PROMPT)
                .isEqualTo(requirement)
                .contains("| 实体英文名 | user_event |")
                .doesNotContain("| 实体英文名 | user-event |");
        assertThat(ReflectionTestUtils.getField(service, "DEMO_META_CONFIG"))
                .isEqualTo(meta);
        assertThat(ReflectionTestUtils.getField(service, "DEMO_PUSH_CONFIG"))
                .isEqualTo(push);
    }

    @Test
    void metaAndPushResourcesKeepTheUserEventContract() throws Exception {
        JsonNode meta = JacksonConfig.OBJECT_MAPPER.readTree(
                resource("user-event-meta.json"));
        List<String> attributes = meta.path("attribute").findValuesAsText("name");
        String push = resource("user-event-push.toml");

        assertThat(meta.path("entity").path(0).path("name").asText())
                .isEqualTo("user_event");
        assertThat(meta.path("entity").path(0).path("label").asText())
                .isEqualTo("用户事件数据");
        assertThat(attributes).containsExactly(
                "event_id",
                "procid",
                "user",
                "event_type",
                "reliability",
                "detail",
                "tags",
                "server_time");
        assertThat(push)
                .contains(
                        "[sources.generator_demo_logs]",
                        "[transforms.parse_json]",
                        "[sinks.my_clickhouse_sink]",
                        "table = \"msg_user_event\"",
                        ".event_id = uuid_v4()",
                        ".server_time = format_timestamp!");
    }

    private String resource(String fileName) throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(ROOT + fileName)) {
            assertThat(stream).as(fileName).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
