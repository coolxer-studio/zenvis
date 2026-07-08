package com.coolxer.lubinsun.client;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.config.LubinsunPlatformProperties;
import com.coolxer.lubinsun.model.LubinsunPlatformRunRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LubinsunPlatformClientTest {

    @Test
    void createRunSendsExpectedUrlHeadersAndBody() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LubinsunPlatformClient client = new LubinsunPlatformClient(restTemplate, properties(), JacksonConfig.OBJECT_MAPPER);

        server.expect(once(), requestTo("https://agent.example/api/platform/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Integration-Token", "lub-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "skill": "alert-auto-disposition",
                          "input": {"event": {"id": "a1"}},
                          "metadata": {"source_system": "zenvis"},
                          "external_id": "ext-1",
                          "task_type": "skill_run",
                          "agent": "ops"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"run_id":"run-1","status":"accepted"}
                        """, MediaType.APPLICATION_JSON));

        JsonNode response = client.createRun(new LubinsunPlatformRunRequest()
                .setSkill("alert-auto-disposition")
                .setInput(JacksonConfig.OBJECT_MAPPER.readTree("{\"event\":{\"id\":\"a1\"}}"))
                .setMetadata(JacksonConfig.OBJECT_MAPPER.readTree("{\"source_system\":\"zenvis\"}"))
                .setExternalId("ext-1")
                .setTaskType("skill_run")
                .setAgent("ops"));

        assertThat(response.path("run_id").asText()).isEqualTo("run-1");
        server.verify();
    }

    @Test
    void getEventsUsesAfterAndLimit() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LubinsunPlatformClient client = new LubinsunPlatformClient(restTemplate, properties(), JacksonConfig.OBJECT_MAPPER);

        server.expect(once(), requestTo("https://agent.example/api/platform/runs/run-1/events?after=12&limit=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Integration-Token", "lub-token"))
                .andRespond(withSuccess("""
                        [{"seq":13,"type":"platform.run.received"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getEvents("run-1", 12, 200))
                .hasSize(1)
                .first()
                .extracting(node -> node.path("seq").asLong())
                .isEqualTo(13L);
        server.verify();
    }

    @Test
    void missingTokenFailsBeforeHttpCall() {
        RestTemplate restTemplate = new RestTemplate();
        LubinsunPlatformProperties properties = properties();
        properties.setIntegrationToken("");
        LubinsunPlatformClient client = new LubinsunPlatformClient(restTemplate, properties, JacksonConfig.OBJECT_MAPPER);

        assertThatThrownBy(() -> client.getRun("run-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Integration Token");
    }

    @Test
    void serverErrorIsWrapped() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LubinsunPlatformClient client = new LubinsunPlatformClient(restTemplate, properties(), JacksonConfig.OBJECT_MAPPER);

        server.expect(once(), requestTo("https://agent.example/api/platform/runs/run-1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getRun("run-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("调用 Lubinsun 平台失败");
        server.verify();
    }

    private static LubinsunPlatformProperties properties() {
        LubinsunPlatformProperties properties = new LubinsunPlatformProperties();
        properties.setBaseUrl("https://agent.example/api");
        properties.setIntegrationToken("lub-token");
        properties.setEventLimit(200);
        return properties;
    }
}
