package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.analytics.AnalyticsMetric;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.AnalyticsTimeRange;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.analytics.ValueStatisticsQueryRequest;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.service.retrieval.AnalyticsQueryEngine;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.RetrievalAccessPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EntityAnalyticsServiceImpl implements EntityAnalyticsService {

    private static final int MAX_ENTITIES = 50;
    private static final int MAX_METRICS = 20;
    private static final int MAX_CRITERIA = 50;
    private static final int MAX_BUCKETS = 1_000;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 100;
    private static final int DEFAULT_CATEGORY_LIMIT = 10;
    private static final Duration MAX_CUSTOM_RANGE = Duration.ofDays(366L * 3);
    private static final DateTimeFormatter BOUNDARY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter SECOND_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> OPERATIONS =
            Set.of("COUNT", "DISTINCT_COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final Set<String> COMPARISONS =
            Set.of("NONE", "PREVIOUS_PERIOD", "YEAR_OVER_YEAR");
    private static final Set<String> GRANULARITIES =
            Set.of("AUTO", "HOUR", "DAY", "WEEK", "MONTH");

    private final AnalyticsQueryEngine queryEngine;
    private final MetaDataService metaDataService;
    private final RetrievalAccessPolicy accessPolicy;

    @Value("${app.retrieval.time-zone:Asia/Shanghai}")
    private String retrievalTimeZone = "Asia/Shanghai";

    public EntityAnalyticsServiceImpl(AnalyticsQueryEngine queryEngine,
                                      MetaDataService metaDataService,
                                      RetrievalAccessPolicy accessPolicy) {
        this.queryEngine = queryEngine;
        this.metaDataService = metaDataService;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public AnalyticsResponse overview(OverviewQueryRequest request) {
        requireRequest(request);
        List<String> entities = requireUniqueEntities(request.entities());
        TimeContext time = resolveTime(request.timeRange(), "TODAY", true);
        String comparison = normalizeComparison(request.comparison());
        validateComparison(time, comparison);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String entityName : entities) {
            ResolvedEntity entity = resolveEntity(entityName);
            AnalyticsQueryEngine.QuerySource source = source(entity, request.timeField(),
                    request.criteriaList(), request.criteriaLogic());
            long allTimeCount = queryEngine.aggregate(source,
                    new AnalyticsQueryEngine.Metric("COUNT", null, null), null).longValue();
            long current = queryEngine.aggregate(source,
                    new AnalyticsQueryEngine.Metric("COUNT", null, null), time.current()).longValue();
            Long comparisonValue = comparisonWindow(time, comparison) == null ? null
                    : queryEngine.aggregate(source,
                    new AnalyticsQueryEngine.Metric("COUNT", null, null),
                    comparisonWindow(time, comparison)).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", entity.entity().getName());
            row.put("label", entity.entity().getLabel());
            row.put("all_time_count", allTimeCount);
            row.put("current_value", current);
            appendComparison(row, current, comparisonValue);
            rows.add(row);
        }
        Map<String, Object> result = tabularResult(
                columns("entity", "label", "all_time_count", "current_value",
                        "comparison_value", "delta", "delta_rate"), rows);
        return response(meta("overview", time, comparison, null, rows.size()), result,
                barOption(rows, "label", List.of(
                        new SeriesDefinition("all_time_count", "累计"),
                        new SeriesDefinition("current_value", "当前周期")), false));
    }

    @Override
    public AnalyticsResponse summary(SummaryQueryRequest request) {
        requireRequest(request);
        ResolvedEntity entity = resolveEntity(requireNonBlank(request.entity(), "实体不能为空"));
        List<AnalyticsMetric> metrics = requireMetrics(request.metrics());
        TimeContext time = resolveTime(request.timeRange(), "ALL_TIME", true);
        String comparison = normalizeComparison(request.comparison());
        validateComparison(time, comparison);
        AnalyticsQueryEngine.QuerySource source = source(entity, request.timeField(),
                request.criteriaList(), request.criteriaLogic());
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> metricNames = new HashSet<>();
        for (int i = 0; i < metrics.size(); i++) {
            ResolvedMetric metric = resolveMetric(entity, metrics.get(i), i);
            if (!metricNames.add(metric.name())) {
                throw unsupported("指标名称不能重复: " + metric.name());
            }
            Number value = queryEngine.aggregate(source, metric.metric(), time.current());
            Number comparisonValue = comparisonWindow(time, comparison) == null ? null
                    : queryEngine.aggregate(source, metric.metric(),
                    comparisonWindow(time, comparison));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metric", metric.name());
            row.put("label", metric.label());
            row.put("operation", metric.metric().operation());
            row.put("field", metric.logicalField());
            row.put("value", value);
            appendComparison(row, value, comparisonValue);
            rows.add(row);
        }
        Map<String, Object> result = tabularResult(
                columns("metric", "label", "operation", "field", "value",
                        "comparison_value", "delta", "delta_rate"), rows);
        return response(meta("summary", time, comparison, null, rows.size()), result,
                barOption(rows, "label", List.of(new SeriesDefinition("value", "指标值")), false));
    }

    @Override
    public AnalyticsResponse trend(TrendQueryRequest request) {
        requireRequest(request);
        validateCriteriaBudget(request.criteriaList(),
                request.series() == null ? List.of() : request.series().stream()
                        .filter(Objects::nonNull)
                        .map(TrendQueryRequest.SeriesMapping::criteriaList)
                        .toList());
        validateTrendMetricBudget(request.series());
        List<ResolvedSeries> series = resolveSeries(request);
        requireUniqueLabels(series.stream().map(ResolvedSeries::label).toList(), "趋势序列");
        TimeContext time = resolveTime(request.timeRange(), "LAST_7_DAYS", false);
        String comparison = normalizeComparison(request.comparison());
        validateComparison(time, comparison);
        String granularity = resolveGranularity(request.granularity(), time.current());
        List<String> buckets = bucketKeys(time.current(), granularity);
        AnalyticsQueryEngine.TimeWindow comparisonWindow = comparisonWindow(time, comparison);
        List<String> comparisonBuckets = comparisonWindow == null
                ? List.of() : bucketKeys(comparisonWindow, granularity);
        List<Map<String, Object>> rows = new ArrayList<>();

        for (ResolvedSeries item : series) {
            List<Map<String, Object>> currentRows =
                    queryEngine.trend(item.source(), item.metric().metric(), time.current(), granularity);
            Map<String, Number> current = indexValues(currentRows);
            Map<String, Number> compared = comparisonWindow == null ? Map.of()
                    : indexValues(queryEngine.trend(item.source(), item.metric().metric(),
                    comparisonWindow, granularity));
            for (int i = 0; i < buckets.size(); i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucket", buckets.get(i));
                row.put("series", item.name());
                row.put("label", item.label());
                Number value = current.getOrDefault(buckets.get(i), 0L);
                row.put("value", value);
                Number comparedValue = i < comparisonBuckets.size()
                        ? compared.getOrDefault(comparisonBuckets.get(i), 0L) : 0L;
                appendComparison(row, value, comparisonWindow == null ? null : comparedValue);
                rows.add(row);
            }
        }
        Map<String, Object> result = tabularResult(
                columns("bucket", "series", "label", "value", "comparison_value",
                        "delta", "delta_rate"), rows);
        return response(meta("trend", time, comparison, granularity, buckets.size()), result,
                multiSeriesOption(rows, buckets, series.stream()
                        .map(item -> item.label()).toList(), false,
                        comparisonWindow != null, "line", false));
    }

    @Override
    public AnalyticsResponse distribution(DistributionQueryRequest request) {
        requireRequest(request);
        validateCriteriaBudget(request.criteriaList(),
                request.mappings() == null ? List.of() : request.mappings().stream()
                        .filter(Objects::nonNull)
                        .map(DistributionQueryRequest.Mapping::criteriaList)
                        .toList());
        int limit = requireLimit(request.limit());
        TimeContext time = resolveTime(request.timeRange(), "ALL_TIME", true);
        List<ResolvedDistribution> mappings = resolveDistributions(request);
        requireUniqueLabels(mappings.stream().map(ResolvedDistribution::label).toList(), "分布映射");
        boolean includeNull = Boolean.TRUE.equals(request.includeNull());
        Set<String> families = mappings.stream()
                .map(item -> typeFamily(item.dimensionType()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (families.size() != 1) {
            throw unsupported("跨实体分组字段类型必须兼容");
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Map<String, Long>> valuesBySeries = new LinkedHashMap<>();
        for (ResolvedDistribution mapping : mappings) {
            List<Map<String, Object>> values = queryEngine.distribution(mapping.source(),
                    time.current(), MAX_TOP_LIMIT, includeNull);
            Map<String, Long> seriesValues = new LinkedHashMap<>();
            for (Map<String, Object> value : values) {
                String bucket = String.valueOf(value.get("bucket"));
                long count = ((Number) value.get("value")).longValue();
                seriesValues.put(bucket, count);
                totals.merge(bucket, count, Long::sum);
            }
            valuesBySeries.put(mapping.label(), seriesValues);
        }
        List<String> buckets = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String bucket : buckets) {
            for (ResolvedDistribution mapping : mappings) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucket", bucket);
                row.put("series", mapping.entity());
                row.put("label", mapping.label());
                row.put("value", valuesBySeries.get(mapping.label()).getOrDefault(bucket, 0L));
                rows.add(row);
            }
        }
        Map<String, Object> result = tabularResult(
                columns("bucket", "series", "label", "value"), rows);
        Map<String, Object> metadata = meta("distribution", time, "NONE", null, buckets.size());
        metadata.put("limit", limit);
        metadata.put("include_null", includeNull);
        return response(metadata, result, multiSeriesOption(rows, buckets,
                mappings.stream().map(ResolvedDistribution::label).toList(),
                true, false, "bar", false));
    }

    @Override
    public AnalyticsResponse valueStatistics(ValueStatisticsQueryRequest request) {
        requireRequest(request);
        validateCriteriaBudget(null,
                request.mappings() == null ? List.of() : request.mappings().stream()
                        .filter(Objects::nonNull)
                        .map(ValueStatisticsQueryRequest.Mapping::criteriaList)
                        .toList());
        String focusValue = requireNonBlank(request.focusValue(), "focus_value不能为空");
        List<ValueStatisticsQueryRequest.Mapping> mappings =
                requireMappings(request.mappings(), "值统计字段映射");
        TimeContext time = resolveTime(request.timeRange(), "ALL_TIME", true);
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0L;
        for (ValueStatisticsQueryRequest.Mapping mapping : mappings) {
            ResolvedEntity entity = resolveEntity(requireNonBlank(mapping.entity(), "映射实体不能为空"));
            List<String> fields = requireStrings(mapping.matchFields(), "match_fields", MAX_METRICS);
            List<String> columns = fields.stream()
                    .map(field -> requireScalar(entity, field).getColumnName())
                    .toList();
            AnalyticsQueryEngine.QuerySource source = source(entity, mapping.timeField(),
                    mapping.criteriaList(), mapping.criteriaLogic());
            long value = queryEngine.countAnyOf(source, columns, focusValue, time.current());
            total += value;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", entity.entity().getName());
            row.put("label", StringUtils.defaultIfBlank(mapping.label(), entity.entity().getLabel()));
            row.put("match_fields", fields);
            row.put("value", focusValue);
            row.put("count", value);
            rows.add(row);
        }
        Map<String, Object> result = tabularResult(
                columns("entity", "label", "match_fields", "value", "count"), rows);
        result.put("focus_value", focusValue);
        result.put("total", total);
        return response(meta("value_statistics", time, "NONE", null, rows.size()), result,
                barOption(rows, "label", List.of(new SeriesDefinition("count", "命中数")), false));
    }

    @Override
    public AnalyticsResponse relations(RelationQueryRequest request) {
        requireRequest(request);
        validateCriteriaBudget(null,
                request.mappings() == null ? List.of() : request.mappings().stream()
                        .filter(Objects::nonNull)
                        .map(RelationQueryRequest.Mapping::criteriaList)
                        .toList());
        String focusValue = requireNonBlank(request.focusValue(), "focus_value不能为空");
        int limit = requireLimit(request.limit());
        TimeContext time = resolveTime(request.timeRange(), "LAST_7_DAYS", false);
        List<ResolvedRelation> resolved = resolveRelations(request.mappings());
        Map<String, Object> relationData = queryEngine.relations(
                resolved.stream().map(ResolvedRelation::source).toList(),
                focusValue, time.current(), limit);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peers =
                (List<Map<String, Object>>) relationData.getOrDefault("peers", List.of());
        Map<String, String> labels = resolved.stream().collect(Collectors.toMap(
                item -> item.source().source().entity(),
                item -> item.source().source().label(),
                (first, second) -> first, LinkedHashMap::new));
        peers.forEach(peer -> enrichEntityLabels(peer, labels));
        Map<String, Object> result = new LinkedHashMap<>(relationData);
        result.put("focus_value", focusValue);
        result.put("mapping_count", resolved.size());
        long matchedMappingCount = peers.stream()
                .flatMap(peer -> {
                    Object raw = peer.get("entities");
                    return raw instanceof List<?> values ? values.stream() : java.util.stream.Stream.empty();
                })
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> String.valueOf(item.get("entity")))
                .distinct()
                .count();
        result.put("matched_mapping_count", matchedMappingCount);
        result.put("columns", columns("value", "total", "inbound", "outbound", "entities"));
        result.put("rows", peers);
        Map<String, Object> metadata = meta("relations", time, "NONE", null, peers.size());
        metadata.put("limit", limit);
        return response(metadata, result, graphOption(focusValue, peers));
    }

    @Override
    public AnalyticsResponse relationTimeline(RelationTimelineQueryRequest request) {
        requireRequest(request);
        validateCriteriaBudget(null,
                request.mappings() == null ? List.of() : request.mappings().stream()
                        .filter(Objects::nonNull)
                        .map(RelationTimelineQueryRequest.Mapping::criteriaList)
                        .toList());
        String focusValue = requireNonBlank(request.focusValue(), "focus_value不能为空");
        TimeContext time = resolveTime(request.timeRange(), "LAST_7_DAYS", false);
        String granularity = resolveGranularity(request.granularity(), time.current());
        int categoryLimit = request.categoryLimit() == null
                ? DEFAULT_CATEGORY_LIMIT : request.categoryLimit();
        if (categoryLimit < 1 || categoryLimit > 20) {
            throw unsupported("category_limit必须为1到20");
        }
        List<AnalyticsQueryEngine.TimelineSource> sources =
                resolveTimelineSources(request.mappings());
        List<Map<String, Object>> rows = queryEngine.relationTimeline(
                sources, focusValue, time.current(), granularity, categoryLimit);
        List<String> buckets = bucketKeys(time.current(), granularity);
        List<String> series = rows.stream()
                .map(row -> row.get("direction") + " / " + row.get("category"))
                .distinct().toList();
        rows.forEach(row -> row.put("series",
                row.get("direction") + " / " + row.get("category")));
        Map<String, Object> result = tabularResult(
                columns("bucket", "direction", "category", "series", "value"), rows);
        result.put("focus_value", focusValue);
        Map<String, Object> metadata =
                meta("relation_timeline", time, "NONE", granularity, buckets.size());
        metadata.put("category_limit", categoryLimit);
        return response(metadata, result, multiSeriesOption(rows, buckets, series,
                false, false, "bar", true));
    }

    private List<ResolvedSeries> resolveSeries(TrendQueryRequest request) {
        List<ResolvedSeries> result = new ArrayList<>();
        if (request.series() != null && !request.series().isEmpty()) {
            if (request.entities() != null && !request.entities().isEmpty()) {
                throw unsupported("entities和series不能同时传入");
            }
            if (request.series().size() > MAX_ENTITIES) {
                throw unsupported("趋势序列不能超过" + MAX_ENTITIES + "个");
            }
            int index = 0;
            for (TrendQueryRequest.SeriesMapping mapping : request.series()) {
                if (mapping == null) {
                    throw empty("趋势序列不能为空");
                }
                ResolvedEntity entity =
                        resolveEntity(requireNonBlank(mapping.entity(), "趋势实体不能为空"));
                ResolvedMetric metric = resolveMetric(entity,
                        mapping.metric() == null
                                ? new AnalyticsMetric(null, "COUNT", null, null)
                                : mapping.metric(), index++);
                AnalyticsQueryEngine.QuerySource source = source(entity, mapping.timeField(),
                        mapping.criteriaList() == null ? request.criteriaList() : mapping.criteriaList(),
                        mapping.criteriaLogic() == null
                                ? request.criteriaLogic() : mapping.criteriaLogic());
                String label = StringUtils.defaultIfBlank(mapping.label(), entity.entity().getLabel());
                result.add(new ResolvedSeries(entity.entity().getName(), label, source, metric));
            }
        } else {
            List<String> entities = requireUniqueEntities(request.entities());
            for (String entityName : entities) {
                ResolvedEntity entity = resolveEntity(entityName);
                ResolvedMetric metric = resolveMetric(entity,
                        new AnalyticsMetric("count", "COUNT", null, "数量"), 0);
                result.add(new ResolvedSeries(entity.entity().getName(), entity.entity().getLabel(),
                        source(entity, null, request.criteriaList(), request.criteriaLogic()), metric));
            }
        }
        return result;
    }

    private List<ResolvedDistribution> resolveDistributions(DistributionQueryRequest request) {
        List<DistributionQueryRequest.Mapping> mappings = request.mappings();
        if (mappings != null && !mappings.isEmpty()) {
            if (StringUtils.isNotBlank(request.entity()) || StringUtils.isNotBlank(request.dimension())) {
                throw unsupported("单实体参数和mappings不能同时传入");
            }
            if (mappings.size() > MAX_ENTITIES) {
                throw unsupported("分布映射不能超过" + MAX_ENTITIES + "个");
            }
            List<ResolvedDistribution> result = new ArrayList<>();
            for (DistributionQueryRequest.Mapping mapping : mappings) {
                if (mapping == null) {
                    throw empty("分布映射不能为空");
                }
                ResolvedEntity entity =
                        resolveEntity(requireNonBlank(mapping.entity(), "分布实体不能为空"));
                DataAttribute dimension =
                        requireScalar(entity, requireNonBlank(mapping.dimension(), "分组字段不能为空"));
                AnalyticsQueryEngine.QuerySource querySource = source(entity, mapping.timeField(),
                        mapping.criteriaList(), mapping.criteriaLogic());
                result.add(new ResolvedDistribution(entity.entity().getName(),
                        StringUtils.defaultIfBlank(mapping.label(), entity.entity().getLabel()),
                        dimension.getColumnType(),
                        new AnalyticsQueryEngine.DistributionSource(querySource,
                                dimension.getColumnName(), dimension.getColumnType())));
            }
            return result;
        }
        ResolvedEntity entity = resolveEntity(requireNonBlank(request.entity(), "实体不能为空"));
        DataAttribute dimension =
                requireScalar(entity, requireNonBlank(request.dimension(), "分组字段不能为空"));
        return List.of(new ResolvedDistribution(entity.entity().getName(),
                StringUtils.defaultIfBlank(request.label(), entity.entity().getLabel()),
                dimension.getColumnType(),
                new AnalyticsQueryEngine.DistributionSource(
                        source(entity, request.timeField(), request.criteriaList(),
                                request.criteriaLogic()),
                        dimension.getColumnName(), dimension.getColumnType())));
    }

    private List<ResolvedRelation> resolveRelations(List<RelationQueryRequest.Mapping> mappings) {
        List<RelationQueryRequest.Mapping> required =
                requireMappings(mappings, "关系字段映射");
        List<ResolvedRelation> result = new ArrayList<>();
        for (RelationQueryRequest.Mapping mapping : required) {
            ResolvedEntity entity =
                    resolveEntity(requireNonBlank(mapping.entity(), "映射实体不能为空"));
            DataAttribute sourceField =
                    requireScalar(entity, requireNonBlank(mapping.sourceField(), "源字段不能为空"));
            DataAttribute targetField =
                    requireScalar(entity, requireNonBlank(mapping.targetField(), "目标字段不能为空"));
            String timeField = StringUtils.defaultIfBlank(
                    mapping.timeField(), MetaDataConstants.INSERT_TIME_ATTRIBUTE);
            requireDistinctFields(entity.entity().getName(), sourceField.getName(),
                    targetField.getName(), timeField);
            AnalyticsQueryEngine.QuerySource querySource = source(entity, timeField,
                    mapping.criteriaList(), mapping.criteriaLogic());
            result.add(new ResolvedRelation(new AnalyticsQueryEngine.RelationSource(
                    querySource, sourceField.getColumnName(), sourceField.getColumnType(),
                    targetField.getColumnName(), targetField.getColumnType())));
        }
        return result;
    }

    private List<AnalyticsQueryEngine.TimelineSource> resolveTimelineSources(
            List<RelationTimelineQueryRequest.Mapping> mappings) {
        List<RelationTimelineQueryRequest.Mapping> required =
                requireMappings(mappings, "关系时间轴映射");
        List<AnalyticsQueryEngine.TimelineSource> result = new ArrayList<>();
        for (RelationTimelineQueryRequest.Mapping mapping : required) {
            ResolvedEntity entity =
                    resolveEntity(requireNonBlank(mapping.entity(), "映射实体不能为空"));
            DataAttribute sourceField =
                    requireScalar(entity, requireNonBlank(mapping.sourceField(), "源字段不能为空"));
            DataAttribute targetField =
                    requireScalar(entity, requireNonBlank(mapping.targetField(), "目标字段不能为空"));
            DataAttribute category =
                    requireScalar(entity, requireNonBlank(mapping.categoryField(), "分类字段不能为空"));
            String timeField = StringUtils.defaultIfBlank(
                    mapping.timeField(), MetaDataConstants.INSERT_TIME_ATTRIBUTE);
            requireDistinctFields(entity.entity().getName(), sourceField.getName(),
                    targetField.getName(), timeField, category.getName());
            AnalyticsQueryEngine.QuerySource querySource = source(entity, timeField,
                    mapping.criteriaList(), mapping.criteriaLogic());
            RelationTimelineQueryRequest.CategoryExtraction extraction =
                    mapping.categoryExtraction();
            String type = extraction == null
                    ? "DIRECT" : StringUtils.upperCase(
                    StringUtils.defaultIfBlank(extraction.type(), "DIRECT"));
            int start = extraction == null || extraction.start() == null ? 1 : extraction.start();
            int length = extraction == null || extraction.length() == null
                    ? 16 : extraction.length();
            if (!Set.of("DIRECT", "SUBSTRING").contains(type)) {
                throw unsupported("category_extraction仅支持DIRECT或SUBSTRING");
            }
            if ("SUBSTRING".equals(type)
                    && (start < 1 || start > 256 || length < 1 || length > 64)) {
                throw unsupported("SUBSTRING的start必须为1到256，length必须为1到64");
            }
            AnalyticsQueryEngine.RelationSource relation =
                    new AnalyticsQueryEngine.RelationSource(
                            querySource, sourceField.getColumnName(), sourceField.getColumnType(),
                            targetField.getColumnName(), targetField.getColumnType());
            result.add(new AnalyticsQueryEngine.TimelineSource(
                    relation, category.getColumnName(), category.getColumnType(),
                    type, start, length));
        }
        return result;
    }

    private AnalyticsQueryEngine.QuerySource source(ResolvedEntity entity, String timeField,
                                                     List<RequestCriteriaDto> criteria,
                                                     String criteriaLogic) {
        DataAttribute time = resolveTimeAttribute(entity, timeField);
        return new AnalyticsQueryEngine.QuerySource(
                entity.entity().getName(), entity.entity().getLabel(),
                entity.entity().getTableName(), time.getColumnName(), time.getColumnType(),
                resolveCriteria(entity, criteria), normalizeCriteriaLogic(criteriaLogic));
    }

    private List<AnalyticsQueryEngine.Criterion> resolveCriteria(
            ResolvedEntity entity, List<RequestCriteriaDto> criteria) {
        if (criteria == null) {
            return List.of();
        }
        if (criteria.size() > MAX_CRITERIA) {
            throw unsupported("查询条件不能超过" + MAX_CRITERIA + "个");
        }
        List<AnalyticsQueryEngine.Criterion> result = new ArrayList<>();
        for (RequestCriteriaDto item : criteria) {
            if (item == null) {
                throw empty("查询条件不能为空");
            }
            DataAttribute attribute = requireScalar(entity,
                    requireNonBlank(item.getAttribute(), "条件字段不能为空"));
            String operator = requireNonBlank(item.getOperator(), "条件操作符不能为空");
            if (metaDataService.getDataOperatorByName(operator) == null) {
                throw unsupported("条件操作符不存在: " + operator);
            }
            if (attribute.getOperators() != null && !attribute.getOperators().isEmpty()
                    && attribute.getOperators().stream()
                    .noneMatch(value -> value.equalsIgnoreCase(operator))) {
                throw unsupported("字段不支持操作符: " + attribute.getName() + "." + operator);
            }
            List<String> values = item.getValueList() == null ? List.of() : item.getValueList();
            if (values.size() > 200) {
                throw unsupported("单个条件值不能超过200个");
            }
            result.add(new AnalyticsQueryEngine.Criterion(attribute.getColumnName(),
                    attribute.getColumnType(), operator, values));
        }
        return result;
    }

    private ResolvedMetric resolveMetric(ResolvedEntity entity, AnalyticsMetric input, int index) {
        if (input == null) {
            throw empty("指标不能为空");
        }
        String operation = StringUtils.upperCase(StringUtils.defaultIfBlank(
                input.operation(), "COUNT"));
        if (!OPERATIONS.contains(operation)) {
            throw unsupported("不支持的指标操作: " + input.operation());
        }
        DataAttribute attribute = null;
        if (!"COUNT".equals(operation)) {
            attribute = requireScalar(entity, requireNonBlank(input.field(), "指标字段不能为空"));
            String type = typeFamily(attribute.getColumnType());
            if (Set.of("SUM", "AVG").contains(operation) && !"number".equals(type)) {
                throw unsupported(operation + "指标仅支持数字字段");
            }
            if (Set.of("MIN", "MAX").contains(operation)
                    && !Set.of("number", "date").contains(type)) {
                throw unsupported(operation + "指标仅支持数字或日期时间字段");
            }
        }
        String fallbackName = operation.toLowerCase(Locale.ROOT)
                + (attribute == null ? "" : "_" + attribute.getName());
        String name = StringUtils.defaultIfBlank(input.name(), fallbackName);
        String label = StringUtils.defaultIfBlank(input.label(),
                attribute == null ? "数量" : attribute.getLabel());
        return new ResolvedMetric(name, label, attribute == null ? null : attribute.getName(),
                new AnalyticsQueryEngine.Metric(operation,
                        attribute == null ? null : attribute.getColumnName(),
                        attribute == null ? null : attribute.getColumnType()));
    }

    private ResolvedEntity resolveEntity(String entityName) {
        DataEntity entity = metaDataService.getDataEntityByName(entityName);
        if (entity == null) {
            throw unsupported("实体不存在: " + entityName);
        }
        accessPolicy.checkRead(entity.getName());
        Map<String, DataAttribute> attributes = metaDataService.getAllDataAttributeByEntity(entity)
                .stream().collect(Collectors.toMap(DataAttribute::getName, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        return new ResolvedEntity(entity, attributes);
    }

    private DataAttribute resolveTimeAttribute(ResolvedEntity entity, String field) {
        String name = StringUtils.defaultIfBlank(field, MetaDataConstants.INSERT_TIME_ATTRIBUTE);
        DataAttribute attribute = entity.attributes().get(name);
        if (attribute == null) {
            throw unsupported("时间字段不存在: " + entity.entity().getName() + "." + name);
        }
        if (!"date".equals(typeFamily(attribute.getColumnType()))) {
            throw unsupported("时间字段必须是Date、Date32、DateTime或DateTime64: " + name);
        }
        return attribute;
    }

    private DataAttribute requireScalar(ResolvedEntity entity, String field) {
        DataAttribute attribute = entity.attributes().get(field);
        if (attribute == null) {
            throw unsupported("字段不存在: " + entity.entity().getName() + "." + field);
        }
        String type = unwrapType(attribute.getColumnType()).toLowerCase(Locale.ROOT);
        if (!isSupportedScalarType(type)) {
            throw unsupported("字段不是可分析的标量类型: "
                    + entity.entity().getName() + "." + field);
        }
        return attribute;
    }

    private boolean isSupportedScalarType(String type) {
        return type.equals("string")
                || type.startsWith("fixedstring(")
                || type.equals("uuid")
                || type.equals("ipv4")
                || type.equals("ipv6")
                || type.equals("bool")
                || type.equals("boolean")
                || type.startsWith("enum8(")
                || type.startsWith("enum16(")
                || type.matches("u?int(8|16|32|64|128|256)")
                || type.matches("float(32|64)")
                || type.startsWith("decimal")
                || type.equals("date")
                || type.equals("date32")
                || type.equals("datetime")
                || type.startsWith("datetime(")
                || type.startsWith("datetime64");
    }

    private TimeContext resolveTime(AnalyticsTimeRange input, String defaultPreset,
                                    boolean allowAllTime) {
        String preset = StringUtils.upperCase(input == null
                ? defaultPreset : StringUtils.defaultIfBlank(input.preset(), defaultPreset));
        ZoneId zone = ZoneId.of(retrievalTimeZone);
        LocalDateTime now = LocalDateTime.now(zone);
        if ("ALL_TIME".equals(preset)) {
            if (!allowAllTime) {
                throw unsupported("当前接口不支持ALL_TIME");
            }
            return new TimeContext(preset, null, null, zone.getId());
        }
        LocalDateTime start;
        LocalDateTime end;
        switch (preset) {
            case "TODAY" -> {
                start = LocalDate.now(zone).atStartOfDay();
                end = start.plusDays(1);
            }
            case "YESTERDAY" -> {
                end = LocalDate.now(zone).atStartOfDay();
                start = end.minusDays(1);
            }
            case "LAST_7_DAYS" -> {
                end = now;
                start = now.minusDays(7);
            }
            case "LAST_30_DAYS" -> {
                end = now;
                start = now.minusDays(30);
            }
            case "THIS_MONTH" -> {
                start = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay();
                end = start.plusMonths(1);
            }
            case "CUSTOM" -> {
                if (input == null) {
                    throw invalidTime("CUSTOM必须提供start_time和end_time");
                }
                start = parseTime(input.startTime(), "start_time");
                end = parseTime(input.endTime(), "end_time");
            }
            default -> throw unsupported("不支持的时间范围: " + preset);
        }
        if (!start.isBefore(end)) {
            throw invalidTime("开始时间必须早于结束时间");
        }
        if (Duration.between(start, end).compareTo(MAX_CUSTOM_RANGE) > 0) {
            throw invalidTime("时间跨度不能超过3年");
        }
        return new TimeContext(preset,
                new AnalyticsQueryEngine.TimeWindow(format(start), format(end)),
                new LocalWindow(start, end), zone.getId());
    }

    private AnalyticsQueryEngine.TimeWindow comparisonWindow(TimeContext time, String comparison) {
        if ("NONE".equals(comparison) || time.local() == null) {
            return null;
        }
        LocalDateTime start;
        LocalDateTime end;
        if ("PREVIOUS_PERIOD".equals(comparison)) {
            Duration duration = Duration.between(time.local().start(), time.local().end());
            end = time.local().start();
            start = end.minus(duration);
        } else {
            start = time.local().start().minusYears(1);
            end = time.local().end().minusYears(1);
        }
        return new AnalyticsQueryEngine.TimeWindow(format(start), format(end));
    }

    private void validateComparison(TimeContext time, String comparison) {
        if (!"NONE".equals(comparison) && time.current() == null) {
            throw unsupported("ALL_TIME不支持比较周期");
        }
    }

    private String resolveGranularity(String input, AnalyticsQueryEngine.TimeWindow window) {
        String value = StringUtils.upperCase(StringUtils.defaultIfBlank(input, "AUTO"));
        if (!GRANULARITIES.contains(value)) {
            throw unsupported("不支持的时间粒度: " + input);
        }
        if (!"AUTO".equals(value)) {
            bucketKeys(window, value);
            return value;
        }
        LocalDateTime start = parseBoundary(window.startTime());
        LocalDateTime end = parseBoundary(window.endTime());
        Duration duration = Duration.between(start, end);
        String resolved;
        if (duration.compareTo(Duration.ofDays(2)) <= 0) {
            resolved = "HOUR";
        } else if (duration.compareTo(Duration.ofDays(120)) <= 0) {
            resolved = "DAY";
        } else if (duration.compareTo(Duration.ofDays(730)) <= 0) {
            resolved = "WEEK";
        } else {
            resolved = "MONTH";
        }
        bucketKeys(window, resolved);
        return resolved;
    }

    private List<String> bucketKeys(AnalyticsQueryEngine.TimeWindow window, String granularity) {
        LocalDateTime start = parseBoundary(window.startTime());
        LocalDateTime end = parseBoundary(window.endTime());
        LocalDateTime cursor = switch (granularity) {
            case "HOUR" -> start.withMinute(0).withSecond(0).withNano(0);
            case "DAY" -> start.toLocalDate().atStartOfDay();
            case "WEEK" -> start.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "MONTH" -> start.toLocalDate().withDayOfMonth(1).atStartOfDay();
            default -> throw unsupported("不支持的时间粒度: " + granularity);
        };
        List<String> result = new ArrayList<>();
        while (cursor.isBefore(end)) {
            result.add("HOUR".equals(granularity)
                    ? cursor.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00"))
                    : cursor.format(DateTimeFormatter.ISO_LOCAL_DATE));
            if (result.size() > MAX_BUCKETS) {
                throw unsupported("时间桶数量不能超过" + MAX_BUCKETS);
            }
            cursor = switch (granularity) {
                case "HOUR" -> cursor.plusHours(1);
                case "DAY" -> cursor.plusDays(1);
                case "WEEK" -> cursor.plusWeeks(1);
                case "MONTH" -> cursor.plusMonths(1);
                default -> cursor;
            };
        }
        return result;
    }

    private Map<String, Number> indexValues(List<Map<String, Object>> rows) {
        Map<String, Number> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("bucket")), (Number) row.get("value"));
        }
        return result;
    }

    private void appendComparison(Map<String, Object> row, Number current, Number compared) {
        row.put("comparison_value", compared);
        if (compared == null) {
            row.put("delta", null);
            row.put("delta_rate", null);
            return;
        }
        BigDecimal currentValue = decimal(current);
        BigDecimal comparedValue = decimal(compared);
        BigDecimal delta = currentValue.subtract(comparedValue);
        row.put("delta", delta);
        row.put("delta_rate", comparedValue.compareTo(BigDecimal.ZERO) == 0
                ? null : delta.divide(comparedValue, MathContext.DECIMAL64));
    }

    private void enrichEntityLabels(Map<String, Object> peer, Map<String, String> labels) {
        Object rawEntities = peer.get("entities");
        if (!(rawEntities instanceof List<?> entities)) {
            return;
        }
        for (Object raw : entities) {
            if (raw instanceof Map<?, ?> row) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entity = (Map<String, Object>) row;
                String name = String.valueOf(entity.get("entity"));
                entity.put("label", labels.getOrDefault(name, name));
            }
        }
    }

    private AnalyticsResponse response(Map<String, Object> meta, Map<String, Object> result,
                                       Map<String, Object> echarts) {
        return new AnalyticsResponse(meta, result, echarts);
    }

    private Map<String, Object> meta(String queryType, TimeContext time, String comparison,
                                     String granularity, int resultCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("query_type", queryType);
        meta.put("time_zone", time.zoneId());
        meta.put("preset", time.preset());
        meta.put("start_time", time.current() == null ? null : time.current().startTime());
        meta.put("end_time", time.current() == null ? null : time.current().endTime());
        meta.put("comparison", comparison);
        meta.put("granularity", granularity);
        meta.put("result_count", resultCount);
        return meta;
    }

    private Map<String, Object> tabularResult(List<Map<String, Object>> columns,
                                              List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    private List<Map<String, Object>> columns(String... names) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("name", name);
            columns.add(column);
        }
        return columns;
    }

    private Map<String, Object> barOption(List<Map<String, Object>> rows, String category,
                                          List<SeriesDefinition> series, boolean horizontal) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("legend", Map.of());
        option.put("dataset", Map.of("source", rows));
        option.put("xAxis", Map.of("type", horizontal ? "value" : "category"));
        option.put("yAxis", Map.of("type", horizontal ? "category" : "value"));
        List<Map<String, Object>> chartSeries = new ArrayList<>();
        for (SeriesDefinition item : series) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", item.label());
            value.put("type", "bar");
            value.put("encode", horizontal
                    ? Map.of("x", item.field(), "y", category)
                    : Map.of("x", category, "y", item.field()));
            chartSeries.add(value);
        }
        option.put("series", chartSeries);
        return Map.of("chart_type", "bar", "option", option);
    }

    private Map<String, Object> multiSeriesOption(List<Map<String, Object>> rows,
                                                  List<String> buckets,
                                                  List<String> seriesLabels,
                                                  boolean horizontal,
                                                  boolean includeComparison,
                                                  String seriesType,
                                                  boolean stacked) {
        LinkedHashSet<String> labels = new LinkedHashSet<>(seriesLabels);
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (String bucket : buckets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", bucket);
            values.put(bucket, row);
        }
        for (Map<String, Object> row : rows) {
            String bucket = String.valueOf(row.get("bucket"));
            Object rawLabel = row.containsKey("label") ? row.get("label") : row.get("series");
            String label = String.valueOf(rawLabel);
            values.computeIfAbsent(bucket, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("bucket", bucket);
                return value;
            }).put(label, row.get("value"));
            if (includeComparison && row.get("comparison_value") != null) {
                values.get(bucket).put(label + "（对比）", row.get("comparison_value"));
            }
        }
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("legend", Map.of());
        option.put("dataset", Map.of("source", new ArrayList<>(values.values())));
        option.put("xAxis", Map.of("type", horizontal ? "value" : "category"));
        option.put("yAxis", Map.of("type", horizontal ? "category" : "value"));
        List<Map<String, Object>> chartSeries = new ArrayList<>();
        for (String label : labels) {
            chartSeries.add(chartSeries(label, horizontal, false, seriesType, stacked));
            if (includeComparison) {
                chartSeries.add(chartSeries(label + "（对比）", horizontal, true,
                        seriesType, stacked));
            }
        }
        option.put("series", chartSeries);
        return Map.of("chart_type", seriesType, "option", option);
    }

    private Map<String, Object> chartSeries(String label, boolean horizontal,
                                            boolean comparison, String seriesType,
                                            boolean stacked) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", label);
        series.put("type", seriesType);
        series.put("encode", horizontal
                ? Map.of("x", label, "y", "bucket")
                : Map.of("x", "bucket", "y", label));
        if (stacked) {
            series.put("stack", "total");
        }
        if (comparison) {
            series.put("lineStyle", Map.of("type", "dashed"));
        }
        return series;
    }

    private void requireUniqueLabels(List<String> labels, String subject) {
        Set<String> seen = new HashSet<>();
        for (String label : labels) {
            if (!seen.add(label)) {
                throw unsupported(subject + "label不能重复: " + label);
            }
        }
    }

    private Map<String, Object> graphOption(String focusValue,
                                            List<Map<String, Object>> peers) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", focusValue, "name", focusValue,
                "value", peers.stream().mapToLong(
                        peer -> ((Number) peer.get("total")).longValue()).sum(),
                "symbolSize", 48));
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> peer : peers) {
            String value = String.valueOf(peer.get("value"));
            long count = ((Number) peer.get("total")).longValue();
            nodes.add(Map.of("id", value, "name", value, "value", count));
            long outbound = ((Number) peer.get("outbound")).longValue();
            long inbound = ((Number) peer.get("inbound")).longValue();
            if (outbound > 0) {
                links.add(Map.of("source", focusValue, "target", value,
                        "value", outbound, "direction", "outbound"));
            }
            if (inbound > 0) {
                links.add(Map.of("source", value, "target", focusValue,
                        "value", inbound, "direction", "inbound"));
            }
        }
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("type", "graph");
        series.put("layout", "force");
        series.put("roam", true);
        series.put("label", Map.of("show", true));
        series.put("data", nodes);
        series.put("links", links);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of());
        option.put("series", List.of(series));
        return Map.of("chart_type", "graph", "option", option);
    }

    private String normalizeComparison(String value) {
        String comparison = StringUtils.upperCase(StringUtils.defaultIfBlank(value, "NONE"));
        if (!COMPARISONS.contains(comparison)) {
            throw unsupported("不支持的比较方式: " + value);
        }
        return comparison;
    }

    private String normalizeCriteriaLogic(String value) {
        if (StringUtils.isBlank(value) || "and".equalsIgnoreCase(value)) {
            return "and";
        }
        if ("or".equalsIgnoreCase(value)) {
            return "or";
        }
        throw unsupported("criteria_logic仅支持and或or");
    }

    private void validateCriteriaBudget(List<RequestCriteriaDto> common,
                                        List<List<RequestCriteriaDto>> mappingCriteria) {
        int total = common == null ? 0 : common.size();
        for (List<RequestCriteriaDto> criteria : mappingCriteria) {
            total += criteria == null ? 0 : criteria.size();
            if (total > MAX_CRITERIA) {
                throw unsupported("查询条件总数不能超过" + MAX_CRITERIA + "个");
            }
        }
        if (total > MAX_CRITERIA) {
            throw unsupported("查询条件总数不能超过" + MAX_CRITERIA + "个");
        }
    }

    private void validateTrendMetricBudget(List<TrendQueryRequest.SeriesMapping> series) {
        if (series == null) {
            return;
        }
        long metricCount = series.stream()
                .filter(Objects::nonNull)
                .map(TrendQueryRequest.SeriesMapping::metric)
                .map(metric -> metric == null
                        ? "COUNT|"
                        : StringUtils.upperCase(
                        StringUtils.defaultIfBlank(metric.operation(), "COUNT"))
                        + "|" + StringUtils.defaultString(metric.field()))
                .distinct()
                .count();
        if (metricCount > MAX_METRICS) {
            throw unsupported("趋势指标不能超过" + MAX_METRICS + "个");
        }
    }

    private List<String> requireUniqueEntities(List<String> values) {
        List<String> result = requireStrings(values, "实体列表", MAX_ENTITIES);
        if (new LinkedHashSet<>(result).size() != result.size()) {
            throw unsupported("实体列表不能重复");
        }
        return result;
    }

    private List<AnalyticsMetric> requireMetrics(List<AnalyticsMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            throw empty("指标列表不能为空");
        }
        if (metrics.size() > MAX_METRICS) {
            throw unsupported("指标不能超过" + MAX_METRICS + "个");
        }
        return metrics;
    }

    private <T> List<T> requireMappings(List<T> mappings, String label) {
        if (mappings == null || mappings.isEmpty()) {
            throw empty(label + "不能为空");
        }
        if (mappings.size() > MAX_ENTITIES) {
            throw unsupported(label + "不能超过" + MAX_ENTITIES + "个");
        }
        return mappings;
    }

    private List<String> requireStrings(List<String> values, String label, int max) {
        if (values == null || values.isEmpty()) {
            throw empty(label + "不能为空");
        }
        if (values.size() > max) {
            throw unsupported(label + "不能超过" + max + "个");
        }
        return values.stream().map(value -> requireNonBlank(value, label + "不能包含空值")).toList();
    }

    private int requireLimit(Integer value) {
        int limit = value == null ? DEFAULT_TOP_LIMIT : value;
        if (limit < 1 || limit > MAX_TOP_LIMIT) {
            throw unsupported("limit必须为1到" + MAX_TOP_LIMIT);
        }
        return limit;
    }

    private void requireDistinctFields(String entity, String... fields) {
        if (new HashSet<>(List.of(fields)).size() != fields.length) {
            throw unsupported("同一映射中的字段不得重复: " + entity);
        }
    }

    private String typeFamily(String columnType) {
        String type = unwrapType(columnType).toLowerCase(Locale.ROOT);
        if (type.matches("u?int(8|16|32|64|128|256)")
                || type.matches("float(32|64)") || type.startsWith("decimal")) {
            return "number";
        }
        if (type.equals("date") || type.equals("date32") || type.equals("datetime")
                || type.startsWith("datetime(")
                || type.startsWith("datetime64")) {
            return "date";
        }
        if (type.equals("bool") || type.equals("boolean")) {
            return "boolean";
        }
        return "string";
    }

    private String unwrapType(String columnType) {
        String current = StringUtils.trimToEmpty(columnType);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String wrapper : List.of("Nullable", "LowCardinality")) {
                String prefix = wrapper + "(";
                if (current.regionMatches(true, 0, prefix, 0, prefix.length())
                        && current.endsWith(")")) {
                    current = current.substring(prefix.length(), current.length() - 1).trim();
                    changed = true;
                }
            }
        }
        return current;
    }

    private LocalDateTime parseTime(String value, String label) {
        String normalized = requireNonBlank(value, label + "不能为空");
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME, SECOND_FORMAT, BOUNDARY_FORMAT)) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted representation.
            }
        }
        throw invalidTime(label + "格式必须为ISO本地时间或yyyy-MM-dd HH:mm:ss[.SSS]");
    }

    private LocalDateTime parseBoundary(String value) {
        return LocalDateTime.parse(value, BOUNDARY_FORMAT);
    }

    private String format(LocalDateTime value) {
        return value.format(BOUNDARY_FORMAT);
    }

    private BigDecimal decimal(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String requireNonBlank(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw empty(message);
        }
        return value.trim();
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw empty("请求体不能为空");
        }
    }

    private ApiException empty(String message) {
        return new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), message);
    }

    private ApiException unsupported(String message) {
        return new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
    }

    private ApiException invalidTime(String message) {
        return new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), message);
    }

    private record ResolvedEntity(DataEntity entity, Map<String, DataAttribute> attributes) {
    }

    private record ResolvedMetric(String name, String label, String logicalField,
                                  AnalyticsQueryEngine.Metric metric) {
    }

    private record ResolvedSeries(String name, String label,
                                  AnalyticsQueryEngine.QuerySource source,
                                  ResolvedMetric metric) {
    }

    private record ResolvedDistribution(String entity, String label, String dimensionType,
                                        AnalyticsQueryEngine.DistributionSource source) {
    }

    private record ResolvedRelation(AnalyticsQueryEngine.RelationSource source) {
    }

    private record LocalWindow(LocalDateTime start, LocalDateTime end) {
    }

    private record TimeContext(String preset, AnalyticsQueryEngine.TimeWindow current,
                               LocalWindow local, String zoneId) {
    }

    private record SeriesDefinition(String field, String label) {
    }
}
