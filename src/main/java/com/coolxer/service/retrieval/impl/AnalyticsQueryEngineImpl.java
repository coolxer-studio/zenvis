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
        String operation = normalizeOperation(metric.operation());
        String expression = switch (operation) {
            case "COUNT" -> "count()";
            case "DISTINCT_COUNT" -> "uniqExact(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "SUM" -> "sum(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "AVG" -> "avg(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "MIN" -> "min(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "MAX" -> "max(" + requireIdentifier(metric.column(), "指标字段") + ")";
            default -> throw unsupported("不支持的指标操作: " + metric.operation());
        };
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
        String operation = normalizeOperation(metric.operation());
        String aggregate = switch (operation) {
            case "COUNT" -> "count()";
            case "DISTINCT_COUNT" -> "uniqExact(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "SUM" -> "sum(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "AVG" -> "avg(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "MIN" -> "min(" + requireIdentifier(metric.column(), "指标字段") + ")";
            case "MAX" -> "max(" + requireIdentifier(metric.column(), "指标字段") + ")";
            default -> throw unsupported("不支持的指标操作: " + metric.operation());
        };
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
            case "HOUR" -> "toStartOfHour(" + local + ")";
            case "DAY" -> "toStartOfDay(" + local + ")";
            case "WEEK" -> "toStartOfWeek(" + local + ")";
            case "MONTH" -> "toStartOfMonth(" + local + ")";
            default -> throw unsupported("不支持的时间粒度: " + granularity);
        };
        String format = "HOUR".equals(normalized) ? "%Y-%m-%d %H:00:00" : "%Y-%m-%d";
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
