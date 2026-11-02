package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import com.coolxer.service.retrieval.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class DashboardQuerySamplerTest {

    private EntityAnalyticsService analyticsService;
    private RetrievalService retrievalService;
    private DashboardQuerySampler sampler;

    @BeforeEach
    void setUp() {
        analyticsService = mock(EntityAnalyticsService.class);
        retrievalService = mock(RetrievalService.class);
        sampler = new DashboardQuerySampler(analyticsService, retrievalService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executesEachDistinctReadOnlyQueryOnceAndTreatsEmptyRowsAsWarnings() {
        when(analyticsService.overview(any())).thenReturn(new AnalyticsResponse(
                Map.of(), Map.of("rows", List.of()), Map.of()));
        DataListVo<Object> records = new DataListVo<>();
        records.setTotal(BigDecimal.ZERO);
        records.setDataList(List.of());
        when(retrievalService.retrievalByCriteria(any())).thenReturn(records);

        DashboardSpec spec = spec();
        spec.getBlocks().add(metrics("metrics-copy", "重复指标"));
        spec.getBlocks().add(records("records", "明细"));

        DashboardQuerySampler.SampleResult result = sampler.sample(spec);

        verify(analyticsService, times(1)).overview(any());
        verify(retrievalService, times(1)).retrievalByCriteria(any());
        assertThat(result.samples()).containsOnlyKeys("metrics", "records");
        assertThat((Map<String, Object>) result.samples().get("metrics"))
                .containsEntry("empty", true);
        assertThat((Map<String, Object>) result.samples().get("records"))
                .containsEntry("empty", true);
        assertThat(result.diagnostics()).extracting(DashboardDiagnostic::getCode)
                .containsExactly("REPRESENTATIVE_QUERY_EMPTY", "REPRESENTATIVE_QUERY_EMPTY");
        assertThat(result.diagnostics()).allMatch(item -> "warning".equals(item.getSeverity()));
    }

    @Test
    void queryFailureBlocksValidationWithoutFabricatingSamples() {
        when(analyticsService.overview(any()))
                .thenThrow(new IllegalStateException("查询服务不可用"));

        DashboardQuerySampler.SampleResult result = sampler.sample(spec());

        assertThat(result.samples()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(item -> {
            assertThat(item.getCode()).isEqualTo("REPRESENTATIVE_QUERY_FAILED");
            assertThat(item.getSeverity()).isEqualTo("error");
            assertThat(item.getMessage()).contains("查询服务不可用");
        });
    }

    private DashboardSpec spec() {
        DashboardSpec spec = new DashboardSpec();
        spec.setDashboardId("user-event-dashboard");
        spec.setName("用户事件大屏");
        spec.setTimeField("server_time");
        spec.setDefaultTimeRange("LAST_7_DAYS");
        spec.setBlocks(new ArrayList<>(List.of(metrics("metrics", "关键指标"))));
        return spec;
    }

    private DashboardSpec.DashboardBlock metrics(String id, String title) {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId(id);
        block.setType("metrics");
        block.setTitle(title);
        DashboardSpec.DashboardQuery query = new DashboardSpec.DashboardQuery();
        query.setTool("entity_overview");
        query.setEntity("user_event");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("entities", List.of("user_event"));
        request.put("time_range", Map.of("preset", "ALL_TIME"));
        query.setRequest(request);
        block.setQuery(query);
        DashboardSpec.DashboardMetricCard card = new DashboardSpec.DashboardMetricCard();
        card.setLabel("事件数");
        card.setField("events");
        block.setMetricCards(new ArrayList<>(List.of(card)));
        return block;
    }

    private DashboardSpec.DashboardBlock records(String id, String title) {
        DashboardSpec.DashboardBlock block = new DashboardSpec.DashboardBlock();
        block.setBlockId(id);
        block.setType("records");
        block.setTitle(title);
        DashboardSpec.DashboardRecords records = new DashboardSpec.DashboardRecords();
        records.setEntity("user_event");
        DashboardSpec.DashboardRecordColumn column = new DashboardSpec.DashboardRecordColumn();
        column.setField("event_type");
        records.setColumns(new ArrayList<>(List.of(column)));
        block.setRecords(records);
        return block;
    }
}
