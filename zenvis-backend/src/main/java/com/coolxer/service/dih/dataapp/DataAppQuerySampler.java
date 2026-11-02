package com.coolxer.service.dih.dataapp;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.retrieval.analytics.AggregateQueryRequest;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.HistogramQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.ScatterQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import com.coolxer.service.retrieval.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executes one bounded, representative real query for every distinct block query. */
@Service
public class DataAppQuerySampler {

    private final EntityAnalyticsService analyticsService;
    private final RetrievalService retrievalService;

    public DataAppQuerySampler(EntityAnalyticsService analyticsService,
                               RetrievalService retrievalService) {
        this.analyticsService = analyticsService;
        this.retrievalService = retrievalService;
    }

    public SampleResult sample(DataAppSpec spec) {
        Map<String, Object> samples = new LinkedHashMap<>();
        List<DataAppDiagnostic> diagnostics = new ArrayList<>();
        Set<String> sampledQueries = new LinkedHashSet<>();
        boolean sampledRecords = false;
        if (spec == null || spec.getPages() == null) {
            return new SampleResult(samples, diagnostics);
        }
        for (DataAppSpec.DataAppPage page : spec.getPages()) {
            if (page.getBlocks() == null) {
                continue;
            }
            for (DataAppSpec.DataAppBlock block : page.getBlocks()) {
                if ("records".equals(block.getType()) && !sampledRecords) {
                    sampledRecords = true;
                    sampleRecords(page, block, samples, diagnostics);
                    continue;
                }
                DataAppSpec.DataAppQuery query = block.getQuery();
                if (query == null || !sampledQueries.add(querySignature(query))) {
                    continue;
                }
                sampleQuery(page, block, samples, diagnostics);
            }
        }
        return new SampleResult(samples, diagnostics);
    }

    private String querySignature(DataAppSpec.DataAppQuery query) {
        return query.getTool() + "\n" + query.getEntity() + "\n"
                + DataAppSupport.digestObject(query.getRequest());
    }

    private void sampleQuery(DataAppSpec.DataAppPage page,
                             DataAppSpec.DataAppBlock block,
                             Map<String, Object> samples,
                             List<DataAppDiagnostic> diagnostics) {
        DataAppSpec.DataAppQuery query = block.getQuery();
        String path = "pages." + page.getPageId() + ".blocks." + block.getBlockId() + ".query";
        try {
            Map<String, Object> request = query.getRequest();
            AnalyticsResponse response = switch (query.getTool()) {
                case "entity_overview" -> analyticsService.overview(convert(
                        request, OverviewQueryRequest.class));
                case "entity_summary" -> analyticsService.summary(convert(
                        request, SummaryQueryRequest.class));
                case "entity_trend" -> analyticsService.trend(convert(
                        request, TrendQueryRequest.class));
                case "entity_distribution" -> analyticsService.distribution(convert(
                        request, DistributionQueryRequest.class));
                case "entity_aggregate" -> analyticsService.aggregate(convert(
                        request, AggregateQueryRequest.class));
                case "entity_histogram" -> analyticsService.histogram(convert(
                        request, HistogramQueryRequest.class));
                case "entity_scatter" -> analyticsService.scatter(convert(
                        request, ScatterQueryRequest.class));
                default -> throw new IllegalArgumentException("不支持的查询工具：" + query.getTool());
            };
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("kind", "analytics");
            sample.put("tool", query.getTool());
            sample.put("entity", query.getEntity());
            Object rows = response != null && response.result() != null
                    ? response.result().get("rows") : null;
            sample.put("rowCount", rows instanceof List<?> list ? list.size() : 0);
            samples.put(block.getBlockId(), sample);
        } catch (RuntimeException e) {
            diagnostics.add(new DataAppDiagnostic(
                    "REPRESENTATIVE_QUERY_FAILED", path,
                    "代表性真实查询失败：" + safeMessage(e), "error"));
        }
    }

    private void sampleRecords(DataAppSpec.DataAppPage page,
                               DataAppSpec.DataAppBlock block,
                               Map<String, Object> samples,
                               List<DataAppDiagnostic> diagnostics) {
        DataAppSpec.DataAppRecords records = block.getRecords();
        String path = "pages." + page.getPageId() + ".blocks." + block.getBlockId() + ".records";
        try {
            RetrievalRequestDto request = new RetrievalRequestDto();
            request.setType("normal");
            request.setEntity(records.getEntity());
            request.setCriteriaLogic("and");
            request.setCriteriaList(List.of());
            RequestDisplayDto display = new RequestDisplayDto();
            display.setEntity(records.getEntity());
            List<String> fields = new ArrayList<>();
            records.getColumns().forEach(column -> fields.add(column.getField()));
            if (!fields.contains("zenvis_id")) {
                fields.add("zenvis_id");
            }
            display.setAttributeList(fields);
            request.setDisplayList(List.of(display));
            request.setPage(1);
            request.setSize(1);
            request.setSortBy(records.getDefaultSortField());
            request.setOrder(records.getDefaultSortDirection());
            DataListVo<?> result = retrievalService.retrievalByCriteria(request);
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("kind", "records");
            sample.put("entity", records.getEntity());
            sample.put("total", result == null ? 0 : result.getTotal());
            sample.put("returned", result == null || result.getDataList() == null
                    ? 0 : result.getDataList().size());
            samples.put(block.getBlockId(), sample);
        } catch (RuntimeException e) {
            diagnostics.add(new DataAppDiagnostic(
                    "REPRESENTATIVE_QUERY_FAILED", path,
                    "代表性真实查询失败：" + safeMessage(e), "error"));
        }
    }

    private <T> T convert(Map<String, Object> request, Class<T> type) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(request, type);
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    public record SampleResult(Map<String, Object> samples,
                               List<DataAppDiagnostic> diagnostics) {
    }
}
