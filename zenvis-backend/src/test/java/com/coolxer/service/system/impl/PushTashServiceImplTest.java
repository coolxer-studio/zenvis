package com.coolxer.service.system.impl;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.dto.PushTaskQueryDto;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.service.system.PushTaskConfigValidationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PushTashServiceImplTest {

    private static final String DATA_SERVICE_URL = "http://vectum.test";
    private static final String BEARER_TOKEN = "vectum-token";

    private PushTashServiceImpl service;
    private PushTaskConfigValidationService validationService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        CustomWebConfig customWebConfig = new CustomWebConfig();
        ReflectionTestUtils.setField(customWebConfig, "dataServiceUrl", DATA_SERVICE_URL);
        ReflectionTestUtils.setField(customWebConfig, "dataServiceBearerToken", "  " + BEARER_TOKEN + "  ");

        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        service = new PushTashServiceImpl();
        validationService = mock(PushTaskConfigValidationService.class);
        when(validationService.validate(any(PushTaskValidationRequest.class)))
                .thenReturn(validValidation());
        ReflectionTestUtils.setField(service, "customWebConfig", customWebConfig);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "validationService", validationService);
    }

    @Test
    void createAndStartUsesAddThenStartAndReturnsReadBackMetadata() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/add"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0,\"data\":{\"id\":12}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/start"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();

        PushTaskOperationVo result = service.createAndStartTask(validatedTask());

        assertThat(result.getAction()).isEqualTo("create_and_start");
        assertThat(result.getTaskId()).isEqualTo(12);
        assertThat(result.getCurrentRunId()).isEqualTo("run-1");
        server.verify();
    }

    @Test
    void pluginInstallationCanCreateWithoutAgentDigests() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/add"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0,\"data\":{\"id\":12}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();

        PushTaskDto pluginTask = validatedTask();
        pluginTask.setMark("com.coolxer.plugin.sentinel");
        pluginTask.setCandidateDigest(null);
        pluginTask.setMetaDigest(null);
        pluginTask.setValidationDigest(null);

        assertThat(service.createAndStartForPlugin(pluginTask)).isTrue();
        server.verify();
    }

    @Test
    void queryMapsFiltersAndParsesPageWithoutConfiguration() {
        server.expect(once(), requestTo(DATA_SERVICE_URL
                        + "/vectum/api/v1/task/query?source=MCP&mark=mark-1&status=running&offset=0&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"items":[{"id":12,"name":"task","source":"MCP",
                        "mark":"mark-1","format":"yaml","status":"running"}],
                        "total":1,"offset":0,"limit":100,"hasMore":false}}
                        """, MediaType.APPLICATION_JSON));
        PushTaskQueryDto query = new PushTaskQueryDto();
        query.setSource("MCP");
        query.setSourceMark("mark-1");
        query.setStatus("running");
        query.setOffset(0);
        query.setLimit(100);

        PushTaskPageVo page = service.query(query);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getFormat()).isEqualTo("yaml"));
        server.verify();
    }

    @Test
    void lifecycleEndpointsReturnStructuredOperations() {
        expectTaskView();
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();
        expectTaskView();
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/stop"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();
        expectTaskView();
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/restart"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();

        assertThat(service.startTask(12).getAction()).isEqualTo("start");
        assertThat(service.stopTask(12).getAction()).isEqualTo("stop");
        assertThat(service.restartTask(12).getAction()).isEqualTo("restart");
        server.verify();
    }

    @Test
    void updateAndRestartUsesPutThenRestart() {
        expectTaskView();
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/restart"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));
        expectTaskView();

        PushTaskOperationVo result = service.updateAndRestartTask(12, validatedTask());

        assertThat(result.getAction()).isEqualTo("update_and_restart");
        server.verify();
    }

    @Test
    void deleteUsesExactTaskIdOnly() {
        expectTaskView();
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        PushTaskOperationVo result = service.deleteTask(12);

        assertThat(result.getAction()).isEqualTo("delete");
        assertThat(result.getStatus()).isEqualTo("deleted");
        server.verify();
    }

    @Test
    void getLogUnwrapsVectumResponseAndValidationFailureIsFailClosed() {
        server.expect(once(), requestTo(DATA_SERVICE_URL
                        + "/vectum/api/v1/task/12/log?log_type=system"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":0,\"data\":\"=== Vectum run run-1\\nstarted\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(service.getLog(12, "system")).contains("run-1", "started");

        when(validationService.validate(any(PushTaskValidationRequest.class)))
                .thenReturn(PushTaskValidationVo.builder().passed(false).blocked(true).build());
        assertThatThrownBy(() -> service.createAndStart(validatedTask()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("校验服务不可用");
        server.verify();
    }

    @Test
    void createRejectsFailedValidationAndStaleDigestsBeforeAnyHttpRequest() {
        when(validationService.validate(any(PushTaskValidationRequest.class)))
                .thenReturn(PushTaskValidationVo.builder()
                        .passed(false)
                        .blocked(false)
                        .diagnostics(List.of(PushTaskDiagnosticVo.builder()
                                .code("VECTOR_SCHEMA_ARRAY_AS_MAP")
                                .severity("ERROR")
                                .source("VECTOR")
                                .path("$sinks")
                                .message("sinks 必须是映射")
                                .repairHint("改为组件 ID 映射")
                                .build()))
                        .build());
        assertThatThrownBy(() -> service.createAndStart(validatedTask()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VECTOR_SCHEMA_ARRAY_AS_MAP");

        PushTaskDto stale = validatedTask();
        when(validationService.validate(any(PushTaskValidationRequest.class)))
                .thenReturn(validValidation());
        stale.setValidationDigest("stale");
        assertThatThrownBy(() -> service.createAndStart(stale))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("校验结果已变化");
        server.verify();
    }

    @Test
    void proxyReplacesClientAuthorizationWithVectumBearerAuthentication() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/toggle?force=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(HttpMethod.POST.name());
        request.setRequestURI("/api/v1/system/push-task/12/toggle");
        request.setQueryString("force=true");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer zenvis-client-token");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        assertThat(service.proxy(request)).isEqualTo("{\"status\":0}");
        server.verify();
    }

    @Test
    void proxyRejectsUnvalidatedTaskCreationBeforeForwarding() throws Exception {
        PushTaskDto requestDto = validatedTask();
        requestDto.setCandidateDigest(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(HttpMethod.POST.name());
        request.setRequestURI("/api/v1/system/push-task/add");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(JacksonConfig.OBJECT_MAPPER.writeValueAsBytes(requestDto));

        assertThatThrownBy(() -> service.proxy(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少候选配置摘要");
        server.verify();
    }

    private void expectTaskView() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/12/view"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"id":12,"name":"task","source":"SYSTEM",
                        "mark":"data-push:test","format":"yaml","status":"running","pid":123,
                        "current_run_id":"run-1","run_started_at":"2026-10-30T10:00:00Z"}}
                        """, MediaType.APPLICATION_JSON));
    }

    private PushTaskDto validatedTask() {
        PushTaskDto request = new PushTaskDto();
        request.setName("校验任务");
        request.setConfig("sources: {}\nsinks: {}");
        request.setSource("SYSTEM");
        request.setMark("data-push:test");
        request.setFormat("yaml");
        request.setCandidateDigest("candidate-digest");
        request.setMetaDigest("meta-digest");
        request.setValidationDigest("validation-digest");
        return request;
    }

    private PushTaskValidationVo validValidation() {
        return PushTaskValidationVo.builder()
                .passed(true)
                .blocked(false)
                .format("yaml")
                .candidateDigest("candidate-digest")
                .metaDigest("meta-digest")
                .validationDigest("validation-digest")
                .vectorVersion("vector 0.55.0")
                .build();
    }
}
