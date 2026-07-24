package com.coolxer.controller.retrieval;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.service.retrieval.EntityCoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntityCountControllerTest {

    private EntityCoreService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EntityCoreService.class);
        EntityCountController controller = new EntityCountController();
        ReflectionTestUtils.setField(controller, "entityCoreService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void ipStatisticsSupportsCommaSeparatedEntities() throws Exception {
        when(service.ipStatistics(List.of("traffic", "domain"), "192.0.2.1"))
                .thenReturn(Map.of("ip", "192.0.2.1", "total", 3L));

        mockMvc.perform(get("/api/v1/entity/ip-statistics")
                        .param("entities", "traffic,domain")
                        .param("ip", "192.0.2.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.ip").value("192.0.2.1"))
                .andExpect(jsonPath("$.data.total").value(3));

        verify(service).ipStatistics(List.of("traffic", "domain"), "192.0.2.1");
    }

    @Test
    void ipStatisticsSupportsRepeatedEntityParameters() throws Exception {
        when(service.ipStatistics(List.of("traffic", "domain"), "2001:db8::1"))
                .thenReturn(Map.of("ip", "2001:db8::1", "total", 2L));

        mockMvc.perform(get("/api/v1/entity/ip-statistics")
                        .param("entities", "traffic", "domain")
                        .param("ip", "2001:db8::1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0));

        verify(service).ipStatistics(List.of("traffic", "domain"), "2001:db8::1");
    }

    @Test
    void ipRelationsAcceptsExplicitLogicalFieldMappings() throws Exception {
        when(service.ipRelations(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("ip", "192.0.2.1", "peer_count", 1));

        mockMvc.perform(post("/api/v1/entity/ip-relations/query")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ip": "192.0.2.1",
                                  "startTime": "2026-07-18 00:00:00",
                                  "endTime": "2026-07-24 15:30:00",
                                  "limit": 50,
                                  "entities": ["traffic"],
                                  "relationMappings": [{
                                    "entity": "traffic",
                                    "sourceField": "src_ip",
                                    "targetField": "dst_ip",
                                    "timeField": "found_time"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.ip").value("192.0.2.1"))
                .andExpect(jsonPath("$.data.peer_count").value(1));

        org.mockito.ArgumentCaptor<com.coolxer.model.retrieval.query.IpRelationQueryRequest> request =
                org.mockito.ArgumentCaptor.forClass(
                        com.coolxer.model.retrieval.query.IpRelationQueryRequest.class);
        verify(service).ipRelations(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().relationMappings())
                .singleElement()
                .satisfies(mapping -> {
                    org.assertj.core.api.Assertions.assertThat(mapping.sourceField()).isEqualTo("src_ip");
                    org.assertj.core.api.Assertions.assertThat(mapping.targetField()).isEqualTo("dst_ip");
                    org.assertj.core.api.Assertions.assertThat(mapping.timeField()).isEqualTo("found_time");
                });
    }

    @Test
    void ipEventTimelineAcceptsSnakeCaseRequestWithLogicalFieldMappings() throws Exception {
        EntityCountController controller = new EntityCountController();
        ReflectionTestUtils.setField(controller, "entityCoreService", service);
        MockMvc snakeCaseMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        JacksonConfig.OBJECT_MAPPER))
                .build();
        when(service.ipEventTimeline(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(
                        "ip", "2001:db8::1",
                        "granularity", "hour",
                        "total", 2L));

        snakeCaseMockMvc.perform(post("/api/v1/entity/ip-event-timeline/query")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ip": "2001:db8::1",
                                  "start_time": "2026-07-23 15:30:00",
                                  "end_time": "2026-07-25 15:30:00",
                                  "event_mappings": [{
                                    "entity": "traffic",
                                    "source_field": "src_ip",
                                    "target_field": "dst_ip",
                                    "time_field": "found_time",
                                    "event_type_field": "event_id",
                                    "event_type_start": 2,
                                    "event_type_length": 6
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.ip").value("2001:db8::1"))
                .andExpect(jsonPath("$.data.granularity").value("hour"))
                .andExpect(jsonPath("$.data.total").value(2));

        org.mockito.ArgumentCaptor<com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest>
                request = org.mockito.ArgumentCaptor.forClass(
                        com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest.class);
        verify(service).ipEventTimeline(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().startTime())
                .isEqualTo("2026-07-23 15:30:00");
        org.assertj.core.api.Assertions.assertThat(request.getValue().eventMappings())
                .singleElement()
                .satisfies(mapping -> {
                    org.assertj.core.api.Assertions.assertThat(mapping.sourceField())
                            .isEqualTo("src_ip");
                    org.assertj.core.api.Assertions.assertThat(mapping.targetField())
                            .isEqualTo("dst_ip");
                    org.assertj.core.api.Assertions.assertThat(mapping.timeField())
                            .isEqualTo("found_time");
                    org.assertj.core.api.Assertions.assertThat(mapping.eventTypeField())
                            .isEqualTo("event_id");
                    org.assertj.core.api.Assertions.assertThat(mapping.eventTypeStart())
                            .isEqualTo(2);
                    org.assertj.core.api.Assertions.assertThat(mapping.eventTypeLength())
                            .isEqualTo(6);
                });
    }
}
