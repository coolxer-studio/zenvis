package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ZenvisBusinessServiceClientTest {

    private ZenvisBusinessServiceClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ZenvisBusinessServiceProperties properties = new ZenvisBusinessServiceProperties();
        properties.setBaseUrl("http://zenvis.test:11001/");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new ZenvisBusinessServiceClient(restTemplate, properties);
    }

    @Test
    void sendsHeartbeatWithExactPublicPathAndSnakeCaseBody() {
        server.expect(requestTo("http://zenvis.test:11001/api/v1/public/business-services/heartbeat"))
                .andExpect(method(POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "service_code": "synap-server",
                          "service_name": "Synap Server",
                          "instance_id": "synap-server-pod-1-11099",
                          "status": "UP",
                          "status_message": "ready",
                          "version": "1.0.0",
                          "environment": "test",
                          "host": "pod-1",
                          "port": 11099,
                          "management_url": "http://pod-1:11099/manage",
                          "heartbeat_time": "2026-07-18 10:20:30",
                          "metadata": {"zone": "az-1"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status": 0,
                          "msg": "请求成功",
                          "data": {
                            "service_code": "synap-server",
                            "instance_id": "synap-server-pod-1-11099",
                            "registered": true,
                            "received_at": "2026-07-18 10:20:31",
                            "effective_status": "UP",
                            "offline_after_seconds": 90
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        boolean result = client.reportHeartbeat(new BusinessServiceHeartbeatRequest(
                "synap-server",
                "Synap Server",
                "synap-server-pod-1-11099",
                BusinessServiceStatus.UP,
                "ready",
                "1.0.0",
                "test",
                "pod-1",
                11099,
                "http://pod-1:11099/manage",
                "2026-07-18 10:20:30",
                Map.of("zone", "az-1")));

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void sendsEventWithExactPublicPathAndSnakeCaseBody() {
        server.expect(requestTo("http://zenvis.test:11001/api/v1/public/business-services/events"))
                .andExpect(method(POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().json("""
                        {
                          "event_id": "01234567-89ab-cdef-0123-456789abcdef",
                          "service_code": "synap-server",
                          "instance_id": "synap-server-pod-1-11099",
                          "event_type": "RULE_EXECUTION_FAILED",
                          "severity": "ERROR",
                          "title": "规则执行失败",
                          "message": "脚本不存在",
                          "occurred_at": "2026-07-18 10:21:30",
                          "trace_id": "trace-1",
                          "data": {"script_name": "AndroidStartFactRule.groovy"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status": 0,
                          "msg": "请求成功",
                          "data": {
                            "event_id": "01234567-89ab-cdef-0123-456789abcdef",
                            "accepted_at": "2026-07-18 10:21:31",
                            "duplicate": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        boolean result = client.reportEvent(new BusinessServiceEventRequest(
                "01234567-89ab-cdef-0123-456789abcdef",
                "synap-server",
                "synap-server-pod-1-11099",
                "RULE_EXECUTION_FAILED",
                BusinessServiceEventSeverity.ERROR,
                "规则执行失败",
                "脚本不存在",
                "2026-07-18 10:21:30",
                "trace-1",
                Map.of("script_name", "AndroidStartFactRule.groovy")));

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void treatsTransportBusinessAndInvalidResponseFailuresAsFalse() {
        server.expect(requestTo("http://zenvis.test:11001/api/v1/public/business-services/heartbeat"))
                .andRespond(withSuccess(
                        "{\"status\":400,\"msg\":\"参数错误\",\"data\":null}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.reportHeartbeat(minimalHeartbeat())).isFalse();
        server.verify();

        RestTemplate transportTemplate = new RestTemplate();
        MockRestServiceServer transportServer = MockRestServiceServer.bindTo(transportTemplate).build();
        ZenvisBusinessServiceProperties properties = new ZenvisBusinessServiceProperties();
        properties.setBaseUrl("http://zenvis.test:11001");
        ZenvisBusinessServiceClient transportClient =
                new ZenvisBusinessServiceClient(transportTemplate, properties);
        transportServer
                .expect(requestTo("http://zenvis.test:11001/api/v1/public/business-services/heartbeat"))
                .andRespond(withException(new IOException("connection reset")));
        assertThat(transportClient.reportHeartbeat(minimalHeartbeat())).isFalse();
        transportServer.verify();

        RestTemplate invalidTemplate = new RestTemplate();
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidTemplate).build();
        ZenvisBusinessServiceClient invalidClient =
                new ZenvisBusinessServiceClient(invalidTemplate, properties);
        invalidServer
                .expect(requestTo("http://zenvis.test:11001/api/v1/public/business-services/heartbeat"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));
        assertThat(invalidClient.reportHeartbeat(minimalHeartbeat())).isFalse();
        invalidServer.verify();
    }

    private BusinessServiceHeartbeatRequest minimalHeartbeat() {
        return new BusinessServiceHeartbeatRequest(
                "synap-server",
                "Synap Server",
                "synap-server-localhost-11099",
                BusinessServiceStatus.UP,
                "ready",
                null,
                null,
                "localhost",
                11099,
                null,
                "2026-07-18 10:20:30",
                null);
    }
}
