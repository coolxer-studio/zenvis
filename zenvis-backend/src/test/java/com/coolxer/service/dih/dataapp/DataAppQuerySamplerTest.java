package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import com.coolxer.service.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataAppQuerySamplerTest {

    @Test
    void samplesEachDistinctQueryEvenWhenBlocksShareTheSameTool() {
        EntityAnalyticsService analyticsService = mock(EntityAnalyticsService.class);
        RetrievalService retrievalService = mock(RetrievalService.class);
        when(analyticsService.trend(any())).thenReturn(new AnalyticsResponse(
                Map.of(), Map.of("rows", List.of(Map.of("bucket", "2026-01-01"))), Map.of()));
        DataAppQuerySampler sampler = new DataAppQuerySampler(analyticsService, retrievalService);

        DataAppSpec spec = spec(
                chart("user-event-trend", "user_event"),
                chart("order-trend", "order"));
        DataAppQuerySampler.SampleResult result = sampler.sample(spec);

        verify(analyticsService, times(2)).trend(any());
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.samples()).containsOnlyKeys("user-event-trend", "order-trend");
    }

    private DataAppSpec spec(DataAppSpec.DataAppBlock... blocks) {
        DataAppSpec.DataAppPage page = new DataAppSpec.DataAppPage();
        page.setPageId("overview");
        page.setTitle("总览");
        page.setBlocks(new ArrayList<>(List.of(blocks)));
        DataAppSpec spec = new DataAppSpec();
        spec.setAppId("sample-app");
        spec.setName("采样应用");
        spec.setHomePageId("overview");
        spec.setPages(new ArrayList<>(List.of(page)));
        return spec;
    }

    private DataAppSpec.DataAppBlock chart(String id, String entity) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entity", entity);
        request.put("time_range", Map.of("preset", "LAST_7_DAYS"));
        request.put("series", List.of(Map.of(
                "entity", entity,
                "label", "记录数",
                "metric", Map.of("operation", "COUNT"),
                "time_field", "server_time")));
        DataAppSpec.DataAppQuery query = new DataAppSpec.DataAppQuery();
        query.setTool("entity_trend");
        query.setEntity(entity);
        query.setRequest(request);
        DataAppSpec.DataAppBlock block = new DataAppSpec.DataAppBlock();
        block.setBlockId(id);
        block.setType("chart");
        block.setTitle(id);
        block.setQuery(query);
        return block;
    }
}
