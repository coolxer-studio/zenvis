package com.coolxer.service.system.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PushTashServiceImplTest {

    private static final String DATA_SERVICE_URL = "http://vectum.test";
    private static final String BEARER_TOKEN = "vectum-token";

    private PushTashServiceImpl service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "dataServiceUrl", DATA_SERVICE_URL);
        ReflectionTestUtils.setField(customWebConfig, "dataServiceBearerToken", "  " + BEARER_TOKEN + "  ");

        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        service = new PushTashServiceImpl();
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    @Test
    void createAndStartAddsBearerAuthenticationToBothRequests() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/add"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":{\"id\":12}}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/toggle"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        assertThat(service.createAndStart(new PushTaskDto())).isTrue();
        server.verify();
    }

    @Test
    void findAllAddsBearerAuthentication() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/all"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":[{\"id\":12,\"name\":\"test-task\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<PushTaskVo> tasks = service.findAll();

        assertThat(tasks).singleElement()
                .satisfies(task -> {
                    assertThat(task.getId()).isEqualTo(12);
                    assertThat(task.getName()).isEqualTo("test-task");
                });
        server.verify();
    }

    @Test
    void findByIdReadsTaskDetailWithoutListingAllTasks() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/view"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":{\"id\":12,\"source\":\"SYSTEM\",\"mark\":\"source-mark\"}}",
                        MediaType.APPLICATION_JSON
                ));

        PushTaskVo task = service.findById(12);

        assertThat(task.getId()).isEqualTo(12);
        assertThat(task.getSource()).isEqualTo("SYSTEM");
        assertThat(task.getMark()).isEqualTo("source-mark");
        server.verify();
    }

    @Test
    void deleteBySourceMarkAddsBearerAuthenticationToLookupAndDelete() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/all"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":[{\"id\":12,\"source\":\"SYSTEM\",\"mark\":\"source-mark\"}]}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        assertThat(service.deleteBySourceMark("source-mark")).isTrue();
        server.verify();
    }

    @Test
    void getLogReadsVectumSystemLogWithBearerAuthentication() {
        server.expect(once(), requestTo(
                        DATA_SERVICE_URL + "/vectum/api/v1/task/12/log?log_type=system"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "vector failed\nunknown field",
                        MediaType.TEXT_PLAIN
                ));

        assertThat(service.getLog(12, "system"))
                .isEqualTo("vector failed\nunknown field");
        server.verify();
    }

    @Test
    void getLogPropagatesVectumFailureMessage() {
        server.expect(once(), requestTo(
                        DATA_SERVICE_URL + "/vectum/api/v1/task/12/log?log_type=console"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":1,\"msg\":\"task workspace missing\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> service.getLog(12, "console"))
                .hasMessageContaining("task workspace missing");
        server.verify();
    }

    @Test
    void updateAndStartUpdatesThenStartsTask() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/toggle"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        assertThat(service.updateAndStart(12, new PushTaskDto())).isTrue();
        server.verify();
    }

    @Test
    void updateAndStartDoesNotStartWhenUpdateFails() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

        assertThat(service.updateAndStart(12, new PushTaskDto())).isFalse();
        server.verify();
    }

    @Test
    void updateAndStartReturnsFalseWhenStartFails() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/toggle"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));

        assertThat(service.updateAndStart(12, new PushTaskDto())).isFalse();
        server.verify();
    }

    @Test
    void startFailureStillAllowsReadingConsoleLog() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/toggle"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        DATA_SERVICE_URL + "/vectum/api/v1/task/12/log?log_type=console"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":\"configuration error\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(service.updateAndStart(12, new PushTaskDto())).isFalse();
        assertThat(service.getLog(12, "console")).isEqualTo("configuration error");
        server.verify();
    }

    @Test
    void proxyReplacesClientAuthorizationWithVectumBearerAuthentication() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12?force=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(HttpMethod.PUT.name());
        request.setRequestURI("/api/v1/system/push-task/12");
        request.setQueryString("force=true");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer zenvis-client-token");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        assertThat(service.proxy(request)).isEqualTo("{\"status\":0}");
        server.verify();
    }
}
