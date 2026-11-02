package com.coolxer.service.dih.dashboard;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardSpec;
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
import com.coolxer.service.retrieval.AnalyticsTimeRangeRules;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executes one bounded, real read-only query for every distinct dashboard query. */
@Service
public class DashboardQuerySampler {

    private static final DateTimeFormatter BOUNDARY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final EntityAnalyticsService analyticsService;
    private final RetrievalService retrievalService;

    public DashboardQuerySampler(EntityAnalyticsService analyticsService,
                                 RetrievalService retrievalService) {
        this.analyticsService = analyticsService;
        this.retrievalService = retrievalService;
    }

    public SampleResult sample(DashboardSpec spec) {
        Map<String, Object> samples = new LinkedHashMap<>();
        List<DashboardDiagnostic> diagnostics = new ArrayList<>();
        if (spec == null || spec.getBlocks() == null) {
            return new SampleResult(samples, diagnostics);
        }
        Set<String> sampled = new LinkedHashSet<>();
        for (DashboardSpec.DashboardBlock block : spec.getBlocks()) {
            if ("records".equals(block.getType())) {
                String signature = recordsSignature(spec, block);
                if (sampled.add(signature)) {
                    sampleRecords(spec, block, samples, diagnostics);
                }
                continue;
            }
            if (block.getQuery() == null) {
                continue;
            }
            String signature = querySignature(spec, block.getQuery());
            if (sampled.add(signature)) {
                sampleQuery(spec, block, samples, diagnostics);
            }
        }
        return new SampleResult(samples, diagnostics);
    }

    private String querySignature(DashboardSpec spec, DashboardSpec.DashboardQuery query) {
        return query.getTool() + "\n" + query.getEntity() + "\n"
                + DashboardSupport.digestObject(withTimeDefaults(spec, query));
    }

    private String recordsSignature(DashboardSpec spec, DashboardSpec.DashboardBlock block) {
        DashboardSpec.DashboardRecords records = block.getRecords();
        return "records\n" + (records == null ? "" : records.getEntity()) + "\n"
                + DashboardSupport.digestObject(records) + "\n" + spec.getTimeField();
    }

    private void sampleQuery(DashboardSpec spec,
                             DashboardSpec.DashboardBlock block,
                             Map<String, Object> samples,
                             List<DashboardDiagnostic> diagnostics) {
        DashboardSpec.DashboardQuery query = block.getQuery();
        String path = "blocks." + block.getBlockId() + ".query";
        try {
            Map<String, Object> request = withTimeDefaults(spec, query);
            AnalyticsResponse response = switch (query.getTool()) {
                case "entity_overview" -> analyticsService.overview(convert(request, OverviewQueryRequest.class));
                case "entity_summary" -> analyticsService.summary(convert(request, SummaryQueryRequest.class));
                case "entity_trend" -> analyticsService.trend(convert(request, TrendQueryRequest.class));
                case "entity_distribution" -> analyticsService.distribution(convert(request, DistributionQueryRequest.class));
                case "entity_aggregate" -> analyticsService.aggregate(convert(request, AggregateQueryRequest.class));
                case "entity_histogram" -> analyticsService.histogram(convert(request, HistogramQueryRequest.class));
                case "entity_scatter" -> analyticsService.scatter(convert(request, ScatterQueryRequest.class));
                default -> throw new IllegalArgumentException("不支持的查询工具：" + query.getTool());
            };
            List<?> rows = analyticsRows(response);
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("kind", "analytics");
            sample.put("tool", query.getTool());
            sample.put("entity", query.getEntity());
            sample.put("rowCount", rows.size());
            sample.put("empty", rows.isEmpty());
            samples.put(block.getBlockId(), sample);
            if (rows.isEmpty()) {
                warning(diagnostics, "REPRESENTATIVE_QUERY_EMPTY", path,
                        "代表性真实查询返回空结果；大屏仍可发布，但该区块将展示空态");
            }
        } catch (RuntimeException e) {
            error(diagnostics, "REPRESENTATIVE_QUERY_FAILED", path,
                    "代表性真实查询失败：" + safeMessage(e));
        }
    }

    private void sampleRecords(DashboardSpec spec,
                               DashboardSpec.DashboardBlock block,
                               Map<String, Object> samples,
                               List<DashboardDiagnostic> diagnostics) {
        DashboardSpec.DashboardRecords records = block.getRecords();
        if (records == null) {
            return;
        }
        String path = "blocks." + block.getBlockId() + ".records";
        try {
            RetrievalRequestDto request = new RetrievalRequestDto();
            request.setType("normal");
            request.setEntity(records.getEntity());
            request.setCriteriaLogic("and");
            request.setCriteriaList(timeCriteria(spec, records.getEntity()));
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
            sample.put("empty", result == null || result.getTotal() == null
                    || result.getTotal().signum() == 0);
            samples.put(block.getBlockId(), sample);
            if (Boolean.TRUE.equals(sample.get("empty"))) {
                warning(diagnostics, "REPRESENTATIVE_QUERY_EMPTY", path,
                        "明细表代表性查询返回空结果；大屏仍可发布，但该区块将展示空态");
            }
        } catch (RuntimeException e) {
            error(diagnostics, "REPRESENTATIVE_QUERY_FAILED", path,
                    "代表性真实查询失败：" + safeMessage(e));
        }
    }

    private Map<String, Object> withTimeDefaults(DashboardSpec spec,
                                                 DashboardSpec.DashboardQuery query) {
        Map<String, Object> request = new LinkedHashMap<>(query.getRequest() == null
                ? Map.of() : query.getRequest());
        String entity = StringUtils.defaultIfBlank(query.getEntity(), text(request.get("entity")));
        if (StringUtils.isNotBlank(entity) && !request.containsKey("entity")) {
            request.put("entity", entity);
        }
        if (StringUtils.isNotBlank(spec.getTimeField())) {
            if (List.of("entity_overview", "entity_summary", "entity_distribution",
                    "entity_aggregate", "entity_histogram", "entity_scatter").contains(query.getTool())
                    && StringUtils.isBlank(text(request.get("time_field")))) {
                request.put("time_field", spec.getTimeField());
            }
            if ("entity_trend".equals(query.getTool())) {
                List<?> series = request.get("series") instanceof List<?> list ? list : List.of();
                List<Object> copiedSeries = new ArrayList<>();
                for (Object raw : series) {
                    if (raw instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) map);
                        item.putIfAbsent("time_field", spec.getTimeField());
                        copiedSeries.add(item);
                    } else {
                        copiedSeries.add(raw);
                    }
                }
                request.put("series", copiedSeries);
            }
        }
        if (!request.containsKey("time_range")) {
            request.put("time_range", Map.of(
                    "preset", spec.getDefaultTimeRange(), "start_time", null, "end_time", null));
        }
        return request;
    }

    private List<?> analyticsRows(AnalyticsResponse response) {
        if (response == null || response.result() == null) {
            return List.of();
        }
        Object rows = response.result().get("rows");
        return rows instanceof List<?> list ? list : List.of();
    }

    private List<com.coolxer.model.retrieval.dto.RequestCriteriaDto> timeCriteria(
            DashboardSpec spec, String entity) {
        if (StringUtils.isBlank(spec.getTimeField()) || "ALL_TIME".equals(spec.getDefaultTimeRange())) {
            return List.of();
        }
        AnalyticsTimeRangeRules.Resolved resolved = AnalyticsTimeRangeRules.resolve(
                spec.getDefaultTimeRange(), null, null, "ALL_TIME", true,
                ZoneId.systemDefault(), LocalDateTime.now());
        if (resolved.start() == null || resolved.end() == null) {
            return List.of();
        }
        com.coolxer.model.retrieval.dto.RequestCriteriaDto criteria =
                new com.coolxer.model.retrieval.dto.RequestCriteriaDto();
        criteria.setAttribute(spec.getTimeField());
        criteria.setOperator("between");
        criteria.setValueList(List.of(
                resolved.start().format(BOUNDARY_FORMAT),
                resolved.end().format(BOUNDARY_FORMAT)));
        return List.of(criteria);
    }

    private <T> T convert(Map<String, Object> request, Class<T> type) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(request, type);
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void warning(List<DashboardDiagnostic> diagnostics,
                         String code,
                         String path,
                         String message) {
        diagnostics.add(new DashboardDiagnostic(code, path, message, "warning"));
    }

    private void error(List<DashboardDiagnostic> diagnostics,
                       String code,
                       String path,
                       String message) {
        diagnostics.add(new DashboardDiagnostic(code, path, message, "error"));
    }

    public record SampleResult(Map<String, Object> samples,
                               List<DashboardDiagnostic> diagnostics) {
    }
}
