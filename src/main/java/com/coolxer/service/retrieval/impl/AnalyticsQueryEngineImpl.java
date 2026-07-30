package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.service.retrieval.AnalyticsQueryEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import jakarta.persistence.Query;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AnalyticsQueryEngineImpl implements AnalyticsQueryEngine {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final int QUERY_TIMEOUT_MILLIS = 60_000;

    @Value("${app.retrieval.time-zone:Asia/Shanghai}")
    private String retrievalTimeZone = "Asia/Shanghai";

    @PersistenceContext(unitName = "clickhouse", type = PersistenceContextType.TRANSACTION)
    private EntityManager entityManager;

    @Override
    public Number aggregate(QuerySource source, Metric metric, TimeWindow window) {
        String expression = metricExpression(metric);
        SqlFragment where = buildWhere(source, window, "a");
        Query query = createQuery("select " + expression + " from "
                + requireIdentifier(source.tableName(), "表名") + where.sql());
        bind(query, where.params());
        Object value = query.getSingleResult();
        return toNumber(value);
    }

    @Override
    public List<Map<String, Object>> trend(QuerySource source, Metric metric, TimeWindow window,
                                           String granularity) {
        if (window == null || window.allTime()) {
            throw unsupported("趋势查询必须指定时间范围");
        }
        String timeColumn = requireIdentifier(source.timeColumn(), "时间字段");
        String bucket = bucketExpression(timeColumn, source.timeColumnType(), granularity);
        String aggregate = metricExpression(metric);
        SqlFragment where = buildWhere(source, window, "t");
        String sql = "select " + bucket + " as bucket, " + aggregate + " as value from "
                + requireIdentifier(source.tableName(), "表名") + where.sql()
                + " group by bucket order by bucket";
        Query query = createQuery(sql);
        bind(query, where.params());
        List<?> result = query.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : result) {
            Object[] row = requireRow(raw, 2, "趋势");
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("bucket", String.valueOf(row[0]));
            point.put("value", toNumber(row[1]));
            rows.add(point);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> distribution(DistributionSource source, TimeWindow window,
                                                  int limit, boolean includeNull) {
        requireTopLimit(limit);
        String dimension = requireIdentifier(source.dimensionColumn(), "分组字段");
        SqlFragment where = buildWhere(source.source(), window, "d");
        String nullPredicate = includeNull ? "" : (where.sql().isEmpty() ? " where " : " and ")
                + dimension + " is not null and notEmpty(trim(toString(" + dimension + ")))";
        String sql = "select ifNull(toString(" + dimension + "), '') as bucket, count() as value from "
                + requireIdentifier(source.source().tableName(), "表名")
                + where.sql() + nullPredicate
                + " group by bucket order by value desc, bucket asc limit " + limit;
        Query query = createQuery(sql);
        bind(query, where.params());
        List<?> result = query.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : result) {
            Object[] row = requireRow(raw, 2, "分布");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket", String.valueOf(row[0]));
            item.put("value", toLong(row[1]));
            rows.add(item);
        }
        return rows;
    }

    @Override
    public long countAnyOf(QuerySource source, List<String> columns, String focusValue,
                           TimeWindow window) {
        if (CollectionUtils.isEmpty(columns)) {
            return 0L;
        }
        SqlFragment where = buildWhere(source, window, "v");
        List<String> matches = columns.stream()
                .map(column -> "toString(" + requireIdentifier(column, "匹配字段") + ") = :focusValue")
                .toList();
        String matchSql = (where.sql().isEmpty() ? " where " : " and ")
                + "(" + String.join(" or ", matches) + ")";
        Query query = createQuery("select count() from "
                + requireIdentifier(source.tableName(), "表名") + where.sql() + matchSql);
        bind(query, where.params());
        query.setParameter("focusValue", focusValue);
        return toLong(query.getSingleResult());
    }

    @Override
    public Map<String, Object> relations(List<RelationSource> sources, String focusValue,
                                         TimeWindow window, int limit) {
        requireTopLimit(limit);
        if (CollectionUtils.isEmpty(sources)) {
            throw empty("关系字段映射不能为空");
        }
        if (window == null || window.allTime()) {
            throw unsupported("关系查询必须指定时间范围");
        }
        UnionSql union = buildRelationUnion(sources, window);
        String topSql = "select peer, total, inbound, outbound, "
                + "sum(total) over () as relation_total, count() over () as peer_total from ("
                + "select peer, count() as total, countIf(direction='inbound') as inbound, "
                + "countIf(direction='outbound') as outbound from (" + union.sql()
                + ") relation_rows group by peer) totals order by total desc, peer asc limit "
                + (limit + 1);
        Query topQuery = createQuery(topSql);
        bind(topQuery, union.params());
        topQuery.setParameter("focusValue", focusValue);
        List<?> rawRows = topQuery.getResultList();
        long relationTotal = rawRows.isEmpty() ? 0L : toLong(requireRow(rawRows.get(0), 6, "关系")[4]);
        long peerTotal = rawRows.isEmpty() ? 0L : toLong(requireRow(rawRows.get(0), 6, "关系")[5]);
        boolean hasMore = rawRows.size() > limit || peerTotal > limit;
        List<?> visibleRows = rawRows.size() > limit ? rawRows.subList(0, limit) : rawRows;

        List<Map<String, Object>> peers = new ArrayList<>();
        Map<String, Map<String, Object>> peerIndex = new LinkedHashMap<>();
        for (Object raw : visibleRows) {
            Object[] row = requireRow(raw, 6, "关系");
            String value = String.valueOf(row[0]);
            Map<String, Object> peer = new LinkedHashMap<>();
            peer.put("value", value);
            peer.put("total", toLong(row[1]));
            peer.put("inbound", toLong(row[2]));
            peer.put("outbound", toLong(row[3]));
            peer.put("entities", new ArrayList<Map<String, Object>>());
            peers.add(peer);
            peerIndex.put(value, peer);
        }

        if (!peerIndex.isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            int index = 0;
            for (String ignored : peerIndex.keySet()) {
                placeholders.add(":peer" + index++);
            }
            String breakdownSql = "select peer, relation_entity, count() as total, "
                    + "countIf(direction='inbound') as inbound, countIf(direction='outbound') as outbound "
                    + "from (" + union.sql() + ") relation_rows where peer in ("
                    + String.join(",", placeholders)
                    + ") group by peer, relation_entity order by peer, total desc, relation_entity";
            Query breakdown = createQuery(breakdownSql);
            bind(breakdown, union.params());
            breakdown.setParameter("focusValue", focusValue);
            index = 0;
            for (String peer : peerIndex.keySet()) {
                breakdown.setParameter("peer" + index++, peer);
            }
            for (Object raw : breakdown.getResultList()) {
                Object[] row = requireRow(raw, 5, "关系实体明细");
                Map<String, Object> peer = peerIndex.get(String.valueOf(row[0]));
                if (peer == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entities =
                        (List<Map<String, Object>>) peer.get("entities");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entity", String.valueOf(row[1]));
                item.put("total", toLong(row[2]));
                item.put("inbound", toLong(row[3]));
                item.put("outbound", toLong(row[4]));
                entities.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relation_total", relationTotal);
        result.put("peer_total", peerTotal);
        result.put("peer_count", peers.size());
        result.put("has_more", hasMore);
        result.put("peers", peers);
        return result;
    }

    @Override
    public List<Map<String, Object>> relationTimeline(List<TimelineSource> sources,
                                                      String focusValue, TimeWindow window,
                                                      String granularity, int categoryLimit) {
        if (CollectionUtils.isEmpty(sources)) {
            throw empty("关系时间轴映射不能为空");
        }
        if (window == null || window.allTime()) {
            throw unsupported("关系时间轴必须指定时间范围");
        }
        if (categoryLimit < 1 || categoryLimit > 20) {
            throw unsupported("category_limit必须为1到20");
        }
        UnionSql union = buildTimelineUnion(sources, window, granularity);
        String categorySql = "select category, count() as total from (" + union.sql()
                + ") timeline_rows group by category order by total desc, category asc limit "
                + categoryLimit;
        Query categoryQuery = createQuery(categorySql);
        bind(categoryQuery, union.params());
        categoryQuery.setParameter("focusValue", focusValue);
        List<String> categories = new ArrayList<>();
        for (Object raw : categoryQuery.getResultList()) {
            categories.add(String.valueOf(requireRow(raw, 2, "时间轴分类")[0]));
        }
        if (categories.isEmpty()) {
            return List.of();
        }

        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            placeholders.add(":category" + i);
        }
        String sql = "select bucket, direction, category, count() as value from (" + union.sql()
                + ") timeline_rows where category in (" + String.join(",", placeholders)
                + ") group by bucket, direction, category order by bucket, direction, category";
        Query query = createQuery(sql);
        bind(query, union.params());
        query.setParameter("focusValue", focusValue);
        for (int i = 0; i < categories.size(); i++) {
            query.setParameter("category" + i, categories.get(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : query.getResultList()) {
            Object[] row = requireRow(raw, 4, "关系时间轴");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket", String.valueOf(row[0]));
            item.put("direction", String.valueOf(row[1]));
            item.put("category", String.valueOf(row[2]));
            item.put("value", toLong(row[3]));
            rows.add(item);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> aggregateGroups(GroupQuery query, TimeWindow window) {
        if (query == null || query.source() == null || CollectionUtils.isEmpty(query.metrics())) {
            throw empty("聚合查询和指标不能为空");
        }
        List<String> selects = new ArrayList<>();
        List<String> groupAliases = new ArrayList<>();
        Map<String, String> orderAliases = new LinkedHashMap<>();
        List<String> nonNullPredicates = new ArrayList<>();

        for (int i = 0; i < query.dimensions().size(); i++) {
            GroupDimension dimension = query.dimensions().get(i);
            String alias = "d" + i;
            String column = requireIdentifier(dimension.column(), "维度字段");
            String expression;
            if ("TIME".equalsIgnoreCase(dimension.kind())) {
                expression = bucketExpression(column, dimension.columnType(), dimension.granularity());
            } else {
                expression = "ifNull(toString(" + column + "), '')";
                if (!dimension.includeNull()) {
                    nonNullPredicates.add(column
                            + " is not null and notEmpty(trim(toString(" + column + ")))");
                }
            }
            selects.add(expression + " as " + alias);
            groupAliases.add(alias);
            orderAliases.put(dimension.name(), alias);
        }
        for (int i = 0; i < query.metrics().size(); i++) {
            GroupMetric metric = query.metrics().get(i);
            String alias = "m" + i;
            selects.add(metricExpression(metric.metric()) + " as " + alias);
            orderAliases.put(metric.name(), alias);
        }

        SqlFragment where = buildWhere(query.source(), window, "g");
        String whereSql = where.sql();
        for (String predicate : nonNullPredicates) {
            whereSql = conditionSuffix(whereSql, predicate);
        }
        StringBuilder sql = new StringBuilder("select ")
                .append(String.join(", ", selects))
                .append(" from ")
                .append(requireIdentifier(query.source().tableName(), "表名"))
                .append(whereSql);
        if (!groupAliases.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", groupAliases));
        }
        GroupOrder order = query.orderBy();
        String orderAlias = order == null ? null : orderAliases.get(order.field());
        if (order != null && orderAlias == null) {
            throw unsupported("排序字段必须引用维度或指标名称");
        }
        if (orderAlias == null) {
            orderAlias = !groupAliases.isEmpty() ? groupAliases.get(0) : "m0";
        }
        String direction = order == null
                ? (!groupAliases.isEmpty()
                && "TIME".equalsIgnoreCase(query.dimensions().get(0).kind()) ? "asc" : "desc")
                : normalizeSortDirection(order.direction());
        sql.append(" order by ").append(orderAlias).append(" ").append(direction)
                .append(" limit ").append(query.limit());

        Query nativeQuery = createQuery(sql.toString());
        bind(nativeQuery, where.params());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : nativeQuery.getResultList()) {
            int columnCount = query.dimensions().size() + query.metrics().size();
            Object[] values = columnCount == 1 && !(raw instanceof Object[])
                    ? new Object[]{raw}
                    : requireRow(raw, columnCount, "多维聚合");
            Map<String, Object> row = new LinkedHashMap<>();
            int cursor = 0;
            for (GroupDimension dimension : query.dimensions()) {
                row.put(dimension.name(), values[cursor++]);
            }
            for (GroupMetric metric : query.metrics()) {
                row.put(metric.name(), toNumber(values[cursor++]));
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public HistogramResult histogram(HistogramSource source, TimeWindow window) {
        if (source == null || source.source() == null) {
            throw empty("直方图查询不能为空");
        }
        String column = requireIdentifier(source.column(), "直方图字段");
        SqlFragment baseWhere = buildWhere(source.source(), window, "h");
        String whereSql = conditionSuffix(baseWhere.sql(), column + " is not null");
        Map<String, Object> params = new LinkedHashMap<>(baseWhere.params());
        if (source.min() != null) {
            whereSql = conditionSuffix(whereSql, "toFloat64(" + column + ") >= :histLower");
            params.put("histLower", source.min());
        }
        if (source.max() != null) {
            whereSql = conditionSuffix(whereSql, "toFloat64(" + column + ") <= :histUpper");
            params.put("histUpper", source.max());
        }
        Query rangeQuery = createQuery("select min(toFloat64(" + column + ")), "
                + "max(toFloat64(" + column + ")), count() from "
                + requireIdentifier(source.source().tableName(), "表名") + whereSql);
        bind(rangeQuery, params);
        Object[] range = requireRow(rangeQuery.getSingleResult(), 3, "直方图范围");
        long total = toLong(range[2]);
        BigDecimal min = source.min() == null ? decimalValue(range[0]) : source.min();
        BigDecimal max = source.max() == null ? decimalValue(range[1]) : source.max();
        if (total == 0L || min == null || max == null) {
            return new HistogramResult(List.of(), min, max, total);
        }
        if (min.compareTo(max) == 0) {
            return new HistogramResult(List.of(Map.of("bucket", 0, "value", total)),
                    min, max, total);
        }

        BigDecimal width = max.subtract(min)
                .divide(BigDecimal.valueOf(source.bins()), 16, java.math.RoundingMode.HALF_UP);
        String bucket = "least(" + (source.bins() - 1)
                + ", greatest(0, toInt64(floor((toFloat64(" + column
                + ") - :histMin) / :histWidth))))";
        Query histogramQuery = createQuery("select " + bucket
                + " as bucket, count() as value from "
                + requireIdentifier(source.source().tableName(), "表名") + whereSql
                + " group by bucket order by bucket");
        Map<String, Object> histogramParams = new LinkedHashMap<>(params);
        histogramParams.put("histMin", min);
        histogramParams.put("histWidth", width);
        bind(histogramQuery, histogramParams);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : histogramQuery.getResultList()) {
            Object[] values = requireRow(raw, 2, "直方图");
            rows.add(Map.of("bucket", ((Number) values[0]).intValue(),
                    "value", toLong(values[1])));
        }
        return new HistogramResult(rows, min, max, total);
    }

    @Override
    public ScatterResult scatter(ScatterSource source, TimeWindow window) {
        if (source == null || source.source() == null) {
            throw empty("散点图查询不能为空");
        }
        String xColumn = requireIdentifier(source.xColumn(), "X轴字段");
        String yColumn = requireIdentifier(source.yColumn(), "Y轴字段");
        String sizeExpression = source.sizeColumn() == null
                ? "NULL" : "toFloat64(" + requireIdentifier(source.sizeColumn(), "气泡大小字段") + ")";
        String categoryExpression = source.categoryColumn() == null
                ? "NULL" : "ifNull(toString("
                + requireIdentifier(source.categoryColumn(), "分类字段") + "), '')";
        String labelExpression = source.labelColumn() == null
                ? "NULL" : "ifNull(toString("
                + requireIdentifier(source.labelColumn(), "标签字段") + "), '')";
        SqlFragment where = buildWhere(source.source(), window, "s");
        String whereSql = conditionSuffix(where.sql(), xColumn + " is not null");
        whereSql = conditionSuffix(whereSql, yColumn + " is not null");
        String sortColumn = requireIdentifier(source.sortColumn(), "排序字段");
        String sql = "select toFloat64(" + xColumn + "), toFloat64(" + yColumn + "), "
                + sizeExpression + ", " + categoryExpression + ", " + labelExpression
                + " from " + requireIdentifier(source.source().tableName(), "表名")
                + whereSql + " order by " + sortColumn + " "
                + normalizeSortDirection(source.sortDirection())
                + ", " + xColumn + " asc, " + yColumn + " asc"
                + " limit " + (source.limit() + 1);
        Query query = createQuery(sql);
        bind(query, where.params());
        List<?> rawRows = query.getResultList();
        boolean hasMore = rawRows.size() > source.limit();
        List<?> visibleRows = hasMore ? rawRows.subList(0, source.limit()) : rawRows;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object raw : visibleRows) {
            Object[] values = requireRow(raw, 5, "散点图");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("x", toNumber(values[0]));
            row.put("y", toNumber(values[1]));
            row.put("size", values[2] == null ? null : toNumber(values[2]));
            row.put("category", values[3]);
            row.put("label", values[4]);
            rows.add(row);
        }
        return new ScatterResult(rows, hasMore);
    }

    private UnionSql buildRelationUnion(List<RelationSource> sources, TimeWindow window) {
        List<String> selects = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int index = 0;
        for (RelationSource relation : sources) {
            QuerySource source = relation.source();
            SqlFragment where = buildWhere(source, window, "r" + index);
            params.putAll(where.params());
            String sourceColumn = requireIdentifier(relation.sourceColumn(), "源字段");
            String targetColumn = requireIdentifier(relation.targetColumn(), "目标字段");
            String table = requireIdentifier(source.tableName(), "表名");
            String base = where.sql();
            String outbound = conditionSuffix(base,
                    "toString(" + sourceColumn + ") = :focusValue and "
                            + targetColumn + " is not null and notEmpty(trim(toString("
                            + targetColumn + "))) and toString(" + targetColumn + ") != :focusValue");
            String inbound = conditionSuffix(base,
                    "toString(" + targetColumn + ") = :focusValue and "
                            + sourceColumn + " is not null and notEmpty(trim(toString("
                            + sourceColumn + "))) and toString(" + sourceColumn + ") != :focusValue");
            selects.add("select trim(toString(" + targetColumn + ")) as peer, "
                    + quote(source.entity()) + " as relation_entity, 'outbound' as direction from "
                    + table + outbound);
            selects.add("select trim(toString(" + sourceColumn + ")) as peer, "
                    + quote(source.entity()) + " as relation_entity, 'inbound' as direction from "
                    + table + inbound);
            index++;
        }
        return new UnionSql(String.join(" union all ", selects), params);
    }

    private UnionSql buildTimelineUnion(List<TimelineSource> sources, TimeWindow window,
                                        String granularity) {
        List<String> selects = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        int index = 0;
        for (TimelineSource timeline : sources) {
            RelationSource relation = timeline.relation();
            QuerySource source = relation.source();
            SqlFragment where = buildWhere(source, window, "l" + index);
            params.putAll(where.params());
            String sourceColumn = requireIdentifier(relation.sourceColumn(), "源字段");
            String targetColumn = requireIdentifier(relation.targetColumn(), "目标字段");
            String categoryColumn = requireIdentifier(timeline.categoryColumn(), "分类字段");
            String table = requireIdentifier(source.tableName(), "表名");
            String bucket = bucketExpression(requireIdentifier(source.timeColumn(), "时间字段"),
                    source.timeColumnType(), granularity);
            String category = categoryExpression(categoryColumn, timeline);
            String outbound = conditionSuffix(where.sql(),
                    "toString(" + sourceColumn + ") = :focusValue and ("
                            + targetColumn + " is null or toString(" + targetColumn + ") != :focusValue)");
            String inbound = conditionSuffix(where.sql(),
                    "toString(" + targetColumn + ") = :focusValue and ("
                            + sourceColumn + " is null or toString(" + sourceColumn + ") != :focusValue)");
            selects.add("select " + bucket + " as bucket, 'outbound' as direction, "
                    + category + " as category from " + table + outbound);
            selects.add("select " + bucket + " as bucket, 'inbound' as direction, "
                    + category + " as category from " + table + inbound);
            index++;
        }
        return new UnionSql(String.join(" union all ", selects), params);
    }

    private String categoryExpression(String column, TimelineSource source) {
        String value = "ifNull(toString(" + column + "), '')";
        if ("SUBSTRING".equalsIgnoreCase(source.extractionType())) {
            value = "substring(" + value + ", " + source.extractionStart()
                    + ", " + source.extractionLength() + ")";
        }
        return "if(empty(" + value + "), 'unknown', " + value + ")";
    }

    private SqlFragment buildWhere(QuerySource source, TimeWindow window, String prefix) {
        List<String> predicates = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (window != null && !window.allTime()) {
            String timeColumn = requireIdentifier(source.timeColumn(), "时间字段");
            predicates.add(timeColumn + " >= " + timeParameter(":" + prefix + "Start",
                    source.timeColumnType()));
            predicates.add(timeColumn + " < " + timeParameter(":" + prefix + "End",
                    source.timeColumnType()));
            params.put(prefix + "Start", window.startTime());
            params.put(prefix + "End", window.endTime());
        }
        List<Criterion> criteria = source.criteria() == null ? List.of() : source.criteria();
        if (!criteria.isEmpty()) {
            List<String> criteriaSql = new ArrayList<>();
            for (int i = 0; i < criteria.size(); i++) {
                criteriaSql.add(buildCriterion(criteria.get(i), prefix + "c" + i, params));
            }
            String logic = "or".equalsIgnoreCase(source.criteriaLogic()) ? " or " : " and ";
            predicates.add("(" + String.join(logic, criteriaSql) + ")");
        }
        return new SqlFragment(predicates.isEmpty() ? "" : " where "
                + String.join(" and ", predicates), params);
    }

    private String buildCriterion(Criterion criterion, String prefix, Map<String, Object> params) {
        String column = requireIdentifier(criterion.column(), "条件字段");
        String operator = StringUtils.upperCase(StringUtils.trimToEmpty(criterion.operator()));
        List<String> values = criterion.values() == null ? List.of() : criterion.values();
        return switch (operator) {
            case "ISNULL" -> column + " is null";
            case "ISNOTNULL" -> column + " is not null";
            case "EQUAL" -> binary(column, "=", values, prefix, params);
            case "NOTEQUAL" -> binary(column, "!=", values, prefix, params);
            case "GREATTHAN" -> binary(column, ">", values, prefix, params);
            case "GREATEQUALTHAN" -> binary(column, ">=", values, prefix, params);
            case "LESSTHAN" -> binary(column, "<", values, prefix, params);
            case "LESSEQUALTHAN" -> binary(column, "<=", values, prefix, params);
            case "MATCH", "CONTAINS" -> {
                String value = requireValue(values, 1, operator).get(0);
                params.put(prefix, value);
                yield "positionCaseInsensitive(toString(" + column + "), :" + prefix + ") > 0";
            }
            case "BETWEEN" -> {
                List<String> required = requireValue(values, 2, operator);
                params.put(prefix + "Start", required.get(0));
                params.put(prefix + "End", required.get(1));
                yield column + " between :" + prefix + "Start and :" + prefix + "End";
            }
            case "IN" -> {
                if (values.isEmpty()) {
                    throw unsupported("IN条件值不能为空");
                }
                List<String> names = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    String name = prefix + "v" + i;
                    names.add(":" + name);
                    params.put(name, values.get(i));
                }
                yield column + " in (" + String.join(",", names) + ")";
            }
            default -> throw unsupported("不支持的条件操作符: " + criterion.operator());
        };
    }

    private String binary(String column, String operation, List<String> values,
                          String prefix, Map<String, Object> params) {
        String value = requireValue(values, 1, operation).get(0);
        params.put(prefix, value);
        return column + " " + operation + " :" + prefix;
    }

    private List<String> requireValue(List<String> values, int count, String operator) {
        if (values.size() != count || values.stream().anyMatch(StringUtils::isBlank)) {
            throw unsupported(operator + "条件必须包含" + count + "个非空值");
        }
        return values;
    }

    private String bucketExpression(String timeColumn, String timeColumnType,
                                    String granularity) {
        String zone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        String unwrappedType = unwrap(timeColumnType).toLowerCase(Locale.ROOT);
        String dateTime = unwrappedType.equals("date") || unwrappedType.equals("date32")
                ? "toDateTime(" + timeColumn + ", '" + zone + "')"
                : timeColumn;
        String local = "toTimeZone(" + dateTime + ", '" + zone + "')";
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(granularity, "DAY"));
        String bucket = switch (normalized) {
            case "MINUTE" -> "toStartOfMinute(" + local + ")";
            case "FIVE_MINUTES" -> "toStartOfInterval(" + local + ", INTERVAL 5 MINUTE)";
            case "FIFTEEN_MINUTES" -> "toStartOfInterval(" + local + ", INTERVAL 15 MINUTE)";
            case "HOUR" -> "toStartOfHour(" + local + ")";
            case "DAY" -> "toStartOfDay(" + local + ")";
            case "WEEK" -> "toStartOfWeek(" + local + ", 1)";
            case "MONTH" -> "toStartOfMonth(" + local + ")";
            case "QUARTER" -> "toStartOfQuarter(" + local + ")";
            case "YEAR" -> "toStartOfYear(" + local + ")";
            default -> throw unsupported("不支持的时间粒度: " + granularity);
        };
        String format = switch (normalized) {
            case "MINUTE", "FIVE_MINUTES", "FIFTEEN_MINUTES" -> "%Y-%m-%d %H:%i:00";
            case "HOUR" -> "%Y-%m-%d %H:00:00";
            default -> "%Y-%m-%d";
        };
        return "formatDateTime(" + bucket + ", '" + format + "', '" + zone + "')";
    }

    private String timeParameter(String parameter, String columnType) {
        String type = unwrap(columnType).toLowerCase(Locale.ROOT);
        String zone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        if (type.startsWith("datetime64")) {
            return "toDateTime64(" + parameter + ", 3, '" + zone + "')";
        }
        if (type.equals("datetime") || type.startsWith("datetime(")
                || type.equals("date") || type.equals("date32")) {
            return "parseDateTimeBestEffort(" + parameter + ", '" + zone + "')";
        }
        throw unsupported("时间字段必须是Date、Date32、DateTime或DateTime64");
    }

    private Query createQuery(String sql) {
        Query query = entityManager.createNativeQuery(sql);
        try {
            query.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLIS);
        } catch (IllegalArgumentException ignored) {
            // The ClickHouse provider may not expose the standard timeout hint.
        }
        return query;
    }

    private void bind(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private String conditionSuffix(String existingWhere, String condition) {
        return existingWhere + (existingWhere.isEmpty() ? " where " : " and ") + condition;
    }

    private String normalizeOperation(String value) {
        return StringUtils.upperCase(StringUtils.defaultIfBlank(value, "COUNT"));
    }

    private String metricExpression(Metric metric) {
        String operation = normalizeOperation(metric.operation());
        String column = "COUNT".equals(operation)
                ? null : requireIdentifier(metric.column(), "指标字段");
        return switch (operation) {
            case "COUNT" -> "count()";
            case "DISTINCT_COUNT" -> "uniqExact(" + column + ")";
            case "SUM" -> "sum(" + column + ")";
            case "AVG" -> "avg(" + column + ")";
            case "MIN" -> "min(" + column + ")";
            case "MAX" -> "max(" + column + ")";
            case "PERCENTILE" -> {
                double percentile = metric.percentile() == null ? 0.5D : metric.percentile();
                if (!Double.isFinite(percentile) || percentile <= 0D || percentile >= 1D) {
                    throw unsupported("PERCENTILE的percentile必须大于0且小于1");
                }
                yield "quantileExact(" + BigDecimal.valueOf(percentile).stripTrailingZeros()
                        .toPlainString() + ")(" + column + ")";
            }
            default -> throw unsupported("不支持的指标操作: " + metric.operation());
        };
    }

    private String normalizeSortDirection(String value) {
        String normalized = StringUtils.lowerCase(StringUtils.defaultIfBlank(value, "desc"));
        if (!Set.of("asc", "desc").contains(normalized)) {
            throw unsupported("排序方向仅支持asc或desc");
        }
        return normalized;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw unsupported("数值结果格式不正确");
        }
    }

    private void requireTopLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw unsupported("limit必须为1到100");
        }
    }

    private String requireIdentifier(String value, String label) {
        if (StringUtils.isBlank(value) || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw unsupported(label + "不合法");
        }
        return value;
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String unwrap(String columnType) {
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

    private Object[] requireRow(Object value, int length, String label) {
        if (!(value instanceof Object[] row) || row.length < length) {
            throw new IllegalArgumentException(label + "查询结果字段数量不足");
        }
        return row;
    }

    private Number toNumber(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number number) {
            return number;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private long toLong(Object value) {
        return toNumber(value).longValue();
    }

    private ApiException empty(String message) {
        return new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), message);
    }

    private ApiException unsupported(String message) {
        return new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
    }

    private record SqlFragment(String sql, Map<String, Object> params) {
    }

    private record UnionSql(String sql, Map<String, Object> params) {
    }
}
