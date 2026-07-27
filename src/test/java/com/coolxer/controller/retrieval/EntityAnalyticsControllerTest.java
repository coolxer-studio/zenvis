package com.coolxer.controller.retrieval;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntityAnalyticsControllerTest {

    private EntityAnalyticsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EntityAnalyticsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EntityAnalyticsController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        JacksonConfig.OBJECT_MAPPER))
                .build();
        when(service.overview(any())).thenReturn(response("overview", "bar"));
        when(service.summary(any())).thenReturn(response("summary", "bar"));
        when(service.trend(any())).thenReturn(response("trend", "line"));
        when(service.distribution(any())).thenReturn(response("distribution", "bar"));
        when(service.valueStatistics(any())).thenReturn(response("value_statistics", "bar"));
        when(service.relations(any())).thenReturn(response("relations", "graph"));
        when(service.relationTimeline(any()))
                .thenReturn(response("relation_timeline", "bar"));
    }

    @Test
    void exposesAllSevenPostEndpointsWithUnifiedEchartsEnvelope() throws Exception {
        List<String> endpoints = List.of(
                "overview", "summary", "trend", "distribution",
                "value-statistics", "relations", "relation-timeline");
        for (String endpoint : endpoints) {
            mockMvc.perform(post("/api/v1/entity/" + endpoint + "/query")
                            .contentType(APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(0))
                    .andExpect(jsonPath("$.data.meta.query_type").exists())
                    .andExpect(jsonPath("$.data.result.columns").isArray())
                    .andExpect(jsonPath("$.data.result.rows").isArray())
                    .andExpect(jsonPath("$.data.echarts.chart_type").exists())
                    .andExpect(jsonPath("$.data.echarts.option").isMap());
        }
    }

    @Test
    void distributionBindsSnakeCaseGenericFieldMappings() throws Exception {
        mockMvc.perform(post("/api/v1/entity/distribution/query")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "mappings": [{
                                    "entity": "traffic",
                                    "dimension": "addr_src",
                                    "time_field": "event_time"
                                  }],
                                  "time_range": {"preset": "LAST_7_DAYS"},
                                  "criteria_list": [],
                                  "limit": 100,
                                  "include_null": false
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<DistributionQueryRequest> captor =
                ArgumentCaptor.forClass(DistributionQueryRequest.class);
        verify(service).distribution(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(100);
        assertThat(captor.getValue().mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.dimension()).isEqualTo("addr_src");
            assertThat(mapping.timeField()).isEqualTo("event_time");
        });
    }

    @Test
    void relationTimelineUsesGenericNamesAndExtractionContract() throws Exception {
        mockMvc.perform(post("/api/v1/entity/relation-timeline/query")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "focus_value": "192.0.2.1",
                                  "time_range": {
                                    "preset": "CUSTOM",
                                    "start_time": "2026-07-01T00:00:00",
                                    "end_time": "2026-07-02T00:00:00"
                                  },
                                  "mappings": [{
                                    "entity": "traffic",
                                    "source_field": "addr_src",
                                    "target_field": "addr_dst",
                                    "time_field": "event_time",
                                    "category_field": "event_code",
                                    "category_extraction": {
                                      "type": "SUBSTRING",
                                      "start": 2,
                                      "length": 6
                                    }
                                  }]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<RelationTimelineQueryRequest> captor =
                ArgumentCaptor.forClass(RelationTimelineQueryRequest.class);
        verify(service).relationTimeline(captor.capture());
        assertThat(captor.getValue().focusValue()).isEqualTo("192.0.2.1");
        assertThat(captor.getValue().mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.sourceField()).isEqualTo("addr_src");
            assertThat(mapping.categoryExtraction().type()).isEqualTo("SUBSTRING");
        });
    }

    @Test
    void retiredGetEndpointsAreNotMapped() throws Exception {
        for (String endpoint : List.of(
                "count", "count-increase", "trend", "statistics", "ip-statistics")) {
            mockMvc.perform(get("/api/v1/entity/" + endpoint))
                    .andExpect(status().is4xxClientError());
        }
    }

    private AnalyticsResponse response(String type, String chartType) {
        return new AnalyticsResponse(
                Map.of("query_type", type),
                Map.of("columns", List.of(), "rows", List.of()),
                Map.of("chart_type", chartType, "option", Map.of()));
    }
}
