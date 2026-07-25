package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.ColumnCriteria;
import com.coolxer.model.retrieval.query.ColumnCriteriaExpression;
import com.coolxer.model.retrieval.query.DataQuery;
import com.coolxer.model.retrieval.query.DisplayColumn;
import com.coolxer.model.retrieval.query.IpEventTimelineQuerySource;
import com.coolxer.model.retrieval.query.IpRelationQuerySource;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.service.retrieval.QueryEngine;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Slf4j
public class QueryEngineImpl implements QueryEngine {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter TREND_BOUNDARY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern EPOCH_MILLIS_PATTERN = Pattern.compile("-?\\d{11,17}");

    @Value("${app.retrieval.time-zone:Asia/Shanghai}")
    private String retrievalTimeZone = "Asia/Shanghai";

    /**
     * entityManager实现原生查询，unitName是通过clickHouseEntityManagerFactoryBean注入时候指定的名字
     */
    @PersistenceContext(unitName = "clickhouse", type = PersistenceContextType.TRANSACTION)
    private EntityManager entityManager;

    @Transactional
    public void save(String tableName, List<String> columnList, List<String> valueList) {
        String safeTableName = requireIdentifier(tableName, "表名");
        List<String> safeColumnList = columnList.stream()
                .map(column -> requireIdentifier(column, "字段名"))
                .toList();
        // 构建插入SQL
        String insertSql = "insert into " + safeTableName + " (" + StringUtils.join(safeColumnList, ",") + ") values (" + StringUtils.join(valueList, ",") + ")";
        Query query = entityManager.createNativeQuery(insertSql);
        query.executeUpdate();
    }

    @Transactional
    public void update(String tableName, Map<String, String> mapData, String keyColumn, String keyValue) {
        String safeTableName = requireIdentifier(tableName, "表名");
        String safeKeyColumn = requireIdentifier(keyColumn, "字段名");
        // 构建更新SQL
        List<String> setList = mapData.entrySet().stream()
                .map(entry -> requireIdentifier(entry.getKey(), "字段名") + " = " + entry.getValue())
                .toList();
        if (setList.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "更新字段不能为空");
        }
        String updateSql = "update " + safeTableName + " set " +
                StringUtils.join(setList, " , ")
                + " where " + safeKeyColumn + " = " + quote(keyValue);
        Query query = entityManager.createNativeQuery(updateSql);
        query.executeUpdate();
    }

    @Transactional
    public void delete(String tableName, String keyColumn, String keyValue) {
        String deleteSql = "delete from " + requireIdentifier(tableName, "表名") +
                " where " + requireIdentifier(keyColumn, "字段名") + " = " + quote(keyValue);
        Query query = entityManager.createNativeQuery(deleteSql);
        query.executeUpdate();
    }

    @Transactional
    public void deleteIn(String tableName, String keyColumn, List<String> keyValueList) {
        if (CollectionUtils.isEmpty(keyValueList)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "删除ID不能为空");
        }
        String deleteSql = "delete from " + requireIdentifier(tableName, "表名") +
                " where " + requireIdentifier(keyColumn, "字段名") + " in (" +
                StringUtils.join(keyValueList.stream().map(this::quote).toList(), ",") + ")";
        Query query = entityManager.createNativeQuery(deleteSql);
        query.executeUpdate();
    }

    @Transactional
    public Map<String, Object> findById(String tableName, String keyColumn, String id,
                                        List<DataAttribute> dataAttributes) {
        List<DisplayColumn> displayColumnList = dataAttributes.stream().map(attribute -> new DisplayColumn().fromDisplayColumn(attribute)).toList();
        List<String> selectColumnList = displayColumnList.stream().map(this::convertDisplayColumn).toList();
        List<String> columnList = displayColumnList.stream().map(DisplayColumn::getDisplayName).toList();
        String columnSelectSql = StringUtils.join(selectColumnList, ",");

        String selectSql = "select " + columnSelectSql + " from " + requireIdentifier(tableName, "表名")
                + " where " + requireIdentifier(keyColumn, "字段名") + " = " + quote(id);
        Query query = entityManager.createNativeQuery(selectSql);
        // 执行查询
        List<Object[]> result = query.getResultList();
        if (result == null || result.size() == 0) {
            return null;
        } else if (result.size() > 1) {
            throw new RuntimeException("查询结果有多条");
        } else {
            Object[] row = result.get(0);
            Map<String, Object> resultMap = new HashMap<>();
            for (int i = 0; i < columnList.size(); i++) {
                resultMap.put(columnList.get(i), row[i]);
            }
            return resultMap;
        }
    }

    @Transactional
    public BigDecimal count(String tableName, Map<String, Object> searchMap) {
        String whereClause = " where 1=1";
        if (MapUtils.isNotEmpty(searchMap)) {
            whereClause = " where " + searchMap.entrySet().stream()
                    .map(entry -> requireIdentifier(entry.getKey(), "字段名") + " = " + formatSearchValue(entry.getValue()))
                    .collect(Collectors.joining(" and "));
        }
        return queryCount(tableName, whereClause);
    }

    @Override
    @Transactional
    public BigDecimal countAnyOf(String tableName, List<String> fields, String value) {
        if (CollectionUtils.isEmpty(fields)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "统计字段不能为空");
        }
        if (StringUtils.isBlank(value)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "统计值不能为空");
        }
        List<String> safeFields = fields.stream()
                .map(field -> requireIdentifier(field, "字段名"))
                .distinct()
                .toList();
        String countSql = "select count(*) from " + requireIdentifier(tableName, "表名") + " where "
                + safeFields.stream()
                .map(field -> field + " = :value")
                .collect(Collectors.joining(" or "));
        Query query = entityManager.createNativeQuery(countSql);
        query.setParameter("value", value);
        List<BigDecimal> result = query.getResultList();
        return result.isEmpty() ? BigDecimal.ZERO : result.get(0);
    }

    @Override
    @Transactional
    public BigDecimal countAnyOfInTime(IpRelationQuerySource source, String value,
                                       String startTime, String endTime) {
        ValidatedIpRelationSource safeSource = validateIpRelationSource(source);
        requireRelationQueryValues(value, startTime, endTime);
        String timeStart = relationTimeParameter(":startTime", source.timeColumnType());
        String timeEnd = relationTimeParameter(":endTime", source.timeColumnType());
        String countSql = "select count(*) from " + safeSource.tableName()
                + " where (" + safeSource.sourceColumn() + " = :ip or "
                + safeSource.targetColumn() + " = :ip) and "
                + safeSource.timeColumn() + " >= " + timeStart + " and "
                + safeSource.timeColumn() + " <= " + timeEnd;
        Query query = entityManager.createNativeQuery(countSql);
        bindRelationParameters(query, value, startTime, endTime);
        List<?> result = query.getResultList();
        return result.isEmpty() ? BigDecimal.ZERO : toBigDecimal(result.get(0));
    }

    @Override
    @Transactional
    public Map<String, Object> findIpRelations(List<IpRelationQuerySource> sources, String value,
                                               String startTime, String endTime, int limit) {
        if (CollectionUtils.isEmpty(sources)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "关系查询源不能为空");
        }
        if (!Set.of(20, 50, 100).contains(limit)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "limit仅支持20、50或100");
        }
        requireRelationQueryValues(value, startTime, endTime);
        List<ValidatedIpRelationSource> safeSources = sources.stream()
                .map(this::validateIpRelationSource)
                .toList();
        String relationRowsSql = buildRelationRowsSql(sources, safeSources);
        String topSql = "select peer, total, inbound, outbound, "
                + "sum(total) over () as relation_total, count(*) over () as peer_total from ("
                + "select peer, count(*) as total, "
                + "countIf(relation_direction = 'inbound') as inbound, "
                + "countIf(relation_direction = 'outbound') as outbound from ("
                + relationRowsSql
                + ") relation_rows group by peer"
                + ") peer_totals order by total desc, peer asc limit " + (limit + 1);
        Query topQuery = entityManager.createNativeQuery(topSql);
        bindRelationParameters(topQuery, value, startTime, endTime);
        List<Object[]> topRows = topQuery.getResultList();

        long relationTotal = topRows.isEmpty() ? 0L : toLong(topRows.get(0)[4]);
        long peerTotal = topRows.isEmpty() ? 0L : toLong(topRows.get(0)[5]);
        boolean hasMore = peerTotal > limit || topRows.size() > limit;
        List<Object[]> visibleRows = topRows.size() > limit
                ? topRows.subList(0, limit) : topRows;
        List<Map<String, Object>> peers = new ArrayList<>();
        Map<String, Map<String, Object>> peerByIp = new LinkedHashMap<>();
        for (Object[] row : visibleRows) {
            if (row == null || row.length < 6) {
                throw new IllegalArgumentException("IP关系聚合查询结果字段数量不足");
            }
            Map<String, Object> peer = new LinkedHashMap<>();
            String peerIp = String.valueOf(row[0]);
            peer.put("ip", peerIp);
            peer.put("total", toLong(row[1]));
            peer.put("inbound", toLong(row[2]));
            peer.put("outbound", toLong(row[3]));
            peer.put("entities", new ArrayList<Map<String, Object>>());
            peers.add(peer);
            peerByIp.put(peerIp, peer);
        }

        if (!peers.isEmpty()) {
            List<String> peerIps = new ArrayList<>(peerByIp.keySet());
            List<String> peerParameterNames = new ArrayList<>();
            for (int i = 0; i < peerIps.size(); i++) {
                peerParameterNames.add(":peer" + i);
            }
            String peerParameters = String.join(",", peerParameterNames);
            String breakdownSql = "select peer, relation_entity, count(*) as total, "
                    + "countIf(relation_direction = 'inbound') as inbound, "
                    + "countIf(relation_direction = 'outbound') as outbound from ("
                    + relationRowsSql
                    + ") relation_rows where peer in (" + peerParameters + ") "
                    + "group by peer, relation_entity order by peer, total desc, relation_entity";
            Query breakdownQuery = entityManager.createNativeQuery(breakdownSql);
            bindRelationParameters(breakdownQuery, value, startTime, endTime);
            int peerIndex = 0;
            for (String peer : peerIps) {
                breakdownQuery.setParameter("peer" + peerIndex++, peer);
            }
            List<Object[]> breakdownRows = breakdownQuery.getResultList();
            for (Object[] row : breakdownRows) {
                if (row == null || row.length < 5) {
                    throw new IllegalArgumentException("IP关系实体汇总查询结果字段数量不足");
                }
                Map<String, Object> peer = peerByIp.get(String.valueOf(row[0]));
                if (peer == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entities =
                        (List<Map<String, Object>>) peer.get("entities");
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("entity", String.valueOf(row[1]));
                entity.put("total", toLong(row[2]));
                entity.put("inbound", toLong(row[3]));
                entity.put("outbound", toLong(row[4]));
                entities.add(entity);
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
    @Transactional
    public Map<String, Object> findIpEventTimeline(
            List<IpEventTimelineQuerySource> sources,
            String value,
            String startTime,
            String endTime,
            boolean hourly) {
        if (CollectionUtils.isEmpty(sources)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "事件时间轴查询源不能为空");
        }
        requireRelationQueryValues(value, startTime, endTime);
        List<ValidatedIpEventTimelineSource> safeSources = sources.stream()
                .map(this::validateIpEventTimelineSource)
                .toList();
        String timelineRowsSql = buildEventTimelineRowsSql(sources, safeSources, hourly);
        String timelineSql = "select bucket_time, event_direction, event_type, count(*) as total from ("
                + timelineRowsSql
                + ") timeline_rows group by bucket_time, event_direction, event_type "
                + "order by bucket_time desc, event_direction, total desc, event_type";
        Query query = entityManager.createNativeQuery(timelineSql);
        bindRelationParameters(query, value, startTime, endTime);
        List<Object[]> rows = query.getResultList();

        Map<String, Map<String, Object>> bucketByTime = new LinkedHashMap<>();
        long total = 0L;
        long inboundTotal = 0L;
        long outboundTotal = 0L;
        for (Object[] row : rows) {
            if (row == null || row.length < 4) {
                throw new IllegalArgumentException("IP安全事件时间轴查询结果字段数量不足");
            }
            String bucketTime = String.valueOf(row[0]);
            String direction = String.valueOf(row[1]);
            String eventType = String.valueOf(row[2]);
            long count = toLong(row[3]);
            if (!"inbound".equals(direction) && !"outbound".equals(direction)) {
                throw new IllegalArgumentException("IP安全事件时间轴查询返回了未知方向");
            }

            Map<String, Object> bucket = bucketByTime.computeIfAbsent(
                    bucketTime, this::newEventTimelineBucket);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eventTypes =
                    (List<Map<String, Object>>) bucket.get(direction);
            Map<String, Object> eventTypeTotal = new LinkedHashMap<>();
            eventTypeTotal.put("event_type", eventType);
            eventTypeTotal.put("total", count);
            eventTypes.add(eventTypeTotal);
            bucket.put(direction + "_total",
                    ((Number) bucket.get(direction + "_total")).longValue() + count);

            total += count;
            if ("inbound".equals(direction)) {
                inboundTotal += count;
            } else {
                outboundTotal += count;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("inbound_total", inboundTotal);
        result.put("outbound_total", outboundTotal);
        result.put("buckets", new ArrayList<>(bucketByTime.values()));
        return result;
    }

    private Map<String, Object> newEventTimelineBucket(String bucketTime) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put("time", bucketTime);
        bucket.put("inbound_total", 0L);
        bucket.put("outbound_total", 0L);
        bucket.put("inbound", new ArrayList<Map<String, Object>>());
        bucket.put("outbound", new ArrayList<Map<String, Object>>());
        return bucket;
    }

    private String buildEventTimelineRowsSql(
            List<IpEventTimelineQuerySource> sources,
            List<ValidatedIpEventTimelineSource> safeSources,
            boolean hourly) {
        List<String> selects = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            IpEventTimelineQuerySource source = sources.get(i);
            ValidatedIpEventTimelineSource safe = safeSources.get(i);
            String start = relationTimeParameter(":startTime", source.timeColumnType());
            String end = relationTimeParameter(":endTime", source.timeColumnType());
            String timePredicate = safe.timeColumn() + " >= " + start + " and "
                    + safe.timeColumn() + " <= " + end;
            String bucket = eventTimelineBucketExpression(safe.timeColumn(), hourly);
            String eventType = eventTimelineTypeExpression(
                    safe.eventTypeColumn(), safe.eventTypeStart(), safe.eventTypeLength());
            selects.add("select " + bucket + " as bucket_time, "
                    + "'outbound' as event_direction, " + eventType + " as event_type from "
                    + safe.tableName() + " where " + safe.sourceColumn() + " = :ip and ("
                    + safe.targetColumn() + " is null or " + safe.targetColumn()
                    + " != :ip) and " + timePredicate);
            selects.add("select " + bucket + " as bucket_time, "
                    + "'inbound' as event_direction, " + eventType + " as event_type from "
                    + safe.tableName() + " where " + safe.targetColumn() + " = :ip and ("
                    + safe.sourceColumn() + " is null or " + safe.sourceColumn()
                    + " != :ip) and " + timePredicate);
        }
        return String.join(" union all ", selects);
    }

    private String eventTimelineBucketExpression(String timeColumn, boolean hourly) {
        String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        String localTime = "toTimeZone(" + timeColumn + ", '" + timezone + "')";
        String bucket = hourly
                ? "toStartOfHour(" + localTime + ")"
                : "toStartOfDay(" + localTime + ")";
        return "formatDateTime(" + bucket
                + ", '%Y-%m-%d %H:%i:%S', '" + timezone + "')";
    }

    private String eventTimelineTypeExpression(
            String eventTypeColumn, int eventTypeStart, int eventTypeLength) {
        String extracted = "substring(ifNull(" + eventTypeColumn + ", ''), "
                + eventTypeStart + ", " + eventTypeLength + ")";
        return "if(match(" + extracted + ", '^[0-9]{6}$'), "
                + extracted + ", 'unknown')";
    }

    private ValidatedIpEventTimelineSource validateIpEventTimelineSource(
            IpEventTimelineQuerySource source) {
        if (source == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "事件时间轴查询源不能为空");
        }
        String entity = requireIdentifier(source.entity(), "实体名");
        String tableName = requireIdentifier(source.tableName(), "表名");
        String sourceColumn = requireIdentifier(source.sourceColumn(), "源IP字段");
        String targetColumn = requireIdentifier(source.targetColumn(), "目的IP字段");
        String timeColumn = requireIdentifier(source.timeColumn(), "时间字段");
        String eventTypeColumn = requireIdentifier(source.eventTypeColumn(), "事件分类字段");
        if (new HashSet<>(List.of(
                sourceColumn, targetColumn, timeColumn, eventTypeColumn)).size() != 4) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "事件时间轴查询字段不得重复");
        }
        if (!isScalarStringColumn(source.sourceColumnType())
                || !isScalarStringColumn(source.targetColumnType())
                || !isScalarStringColumn(source.eventTypeColumnType())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "事件时间轴源IP、目的IP和事件分类字段必须是标量String");
        }
        relationTimeParameter(":time", source.timeColumnType());
        if (source.eventTypeStart() < 1 || source.eventTypeStart() > 64) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "事件分类起始位置必须在1到64之间");
        }
        if (source.eventTypeLength() < 1 || source.eventTypeLength() > 16) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "事件分类长度必须在1到16之间");
        }
        return new ValidatedIpEventTimelineSource(
                entity,
                tableName,
                sourceColumn,
                targetColumn,
                timeColumn,
                eventTypeColumn,
                source.eventTypeStart(),
                source.eventTypeLength());
    }

    private boolean isScalarStringColumn(String columnType) {
        return "String".equalsIgnoreCase(unwrapColumnType(columnType));
    }

    private String buildRelationRowsSql(List<IpRelationQuerySource> sources,
                                        List<ValidatedIpRelationSource> safeSources) {
        List<String> selects = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            IpRelationQuerySource source = sources.get(i);
            ValidatedIpRelationSource safe = safeSources.get(i);
            String start = relationTimeParameter(":startTime", source.timeColumnType());
            String end = relationTimeParameter(":endTime", source.timeColumnType());
            String timePredicate = safe.timeColumn() + " >= " + start + " and "
                    + safe.timeColumn() + " <= " + end;
            selects.add("select trim(" + safe.targetColumn() + ") as peer, "
                    + quote(safe.entity()) + " as relation_entity, 'outbound' as relation_direction from "
                    + safe.tableName() + " where " + safe.sourceColumn() + " = :ip and "
                    + safe.targetColumn() + " is not null and notEmpty(trim(" + safe.targetColumn()
                    + ")) and trim(" + safe.targetColumn() + ") != :ip and " + timePredicate);
            selects.add("select trim(" + safe.sourceColumn() + ") as peer, "
                    + quote(safe.entity()) + " as relation_entity, 'inbound' as relation_direction from "
                    + safe.tableName() + " where " + safe.targetColumn() + " = :ip and "
                    + safe.sourceColumn() + " is not null and notEmpty(trim(" + safe.sourceColumn()
                    + ")) and trim(" + safe.sourceColumn() + ") != :ip and " + timePredicate);
        }
        return String.join(" union all ", selects);
    }

    private ValidatedIpRelationSource validateIpRelationSource(IpRelationQuerySource source) {
        if (source == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "关系查询源不能为空");
        }
        String entity = requireIdentifier(source.entity(), "实体名");
        String tableName = requireIdentifier(source.tableName(), "表名");
        String sourceColumn = requireIdentifier(source.sourceColumn(), "源IP字段");
        String targetColumn = requireIdentifier(source.targetColumn(), "目的IP字段");
        String timeColumn = requireIdentifier(source.timeColumn(), "时间字段");
        if (sourceColumn.equals(targetColumn)
                || sourceColumn.equals(timeColumn)
                || targetColumn.equals(timeColumn)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "关系查询字段不得重复");
        }
        relationTimeParameter(":time", source.timeColumnType());
        return new ValidatedIpRelationSource(
                entity, tableName, sourceColumn, targetColumn, timeColumn);
    }

    private void requireRelationQueryValues(String value, String startTime, String endTime) {
        if (StringUtils.isBlank(value)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "IP不能为空");
        }
        if (StringUtils.isBlank(startTime) || StringUtils.isBlank(endTime)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "查询时间不能为空");
        }
    }

    private void bindRelationParameters(Query query, String value,
                                        String startTime, String endTime) {
        query.setParameter("ip", value);
        query.setParameter("startTime", startTime);
        query.setParameter("endTime", endTime);
    }

    private String relationTimeParameter(String parameter, String columnType) {
        String type = unwrapColumnType(columnType).toLowerCase(Locale.ROOT);
        String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        if (type.startsWith("datetime64(")) {
            return "toDateTime64(" + parameter + ", 3, '" + timezone + "')";
        }
        if ("datetime".equals(type)) {
            return "toDateTime(" + parameter + ", '" + timezone + "')";
        }
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                "关系查询时间字段必须是DateTime或DateTime64");
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.longValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private record ValidatedIpRelationSource(
            String entity,
            String tableName,
            String sourceColumn,
            String targetColumn,
            String timeColumn) {
    }

    private record ValidatedIpEventTimelineSource(
            String entity,
            String tableName,
            String sourceColumn,
            String targetColumn,
            String timeColumn,
            String eventTypeColumn,
            int eventTypeStart,
            int eventTypeLength) {
    }

    @Transactional
    public BigDecimal countToday(String tableName, Map<String, Object> searchMap) {
        String whereClause = " where 1=1";
        if (MapUtils.isNotEmpty(searchMap)) {
            whereClause = " where " + searchMap.entrySet().stream()
                    .map(entry -> requireIdentifier(entry.getKey(), "字段名") + " = " + formatSearchValue(entry.getValue()))
                    .collect(Collectors.joining(" and "));
        }
        // 补充时间条件
        whereClause += " and " + MetaDataConstants.INSERT_TIME_COLUMN + " >= toStartOfDay(now())";
        return queryCount(tableName, whereClause);
    }

    @Override
    @Transactional
    public Map<String, Object> countByDateOfWeek(String tableName, String timeField) {
        String safeTimeField = requireIdentifier(timeField, "字段名");
        String countSql = "SELECT toStartOfDay(" + safeTimeField + ") AS group_key, COUNT(*) AS count FROM " +
                requireIdentifier(tableName, "表名") + " WHERE " + safeTimeField + " >= today() - INTERVAL 1 WEEK GROUP BY toStartOfDay(" + safeTimeField + ") ORDER BY group_key";
        Query query = entityManager.createNativeQuery(countSql);
        // 执行查询
        Map<String, Object> resultMap = new HashMap<>();
        List<Object[]> result = query.getResultList();
        result.forEach(row -> {
            if (row.length < 2) {
                // 日志记录或抛出自定义异常
                throw new IllegalArgumentException("查询结果字段数量不足，期望至少2个字段");
            }
            resultMap.put(String.valueOf(row[0]), row[1]);
        });
        return resultMap;
    }

    @Override
    @Transactional
    public Map<String, Long> countByTimeRange(String tableName,
                                              String timeField,
                                              String columnType,
                                              String timeUnit,
                                              Date startTime,
                                              Date endTime,
                                              boolean hourly) {
        if (startTime == null || endTime == null || !startTime.before(endTime)) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), "趋势统计时间范围不合法");
        }
        String safeTable = requireIdentifier(tableName, "表名");
        String safeField = requireIdentifier(timeField, "字段名");
        String timeExpression = trendTimeExpression(safeField, columnType, timeUnit);
        String bucketExpression = hourly
                ? "formatDateTime(toStartOfHour(" + timeExpression + "), '%H:00')"
                : "formatDateTime(toStartOfDay(" + timeExpression + "), '%F')";
        String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        String sql = "SELECT " + bucketExpression + " AS group_key, COUNT(*) AS count FROM "
                + safeTable + " WHERE " + timeExpression + " >= toDateTime64(:startTime, 3, '" + timezone + "') AND "
                + timeExpression + " < toDateTime64(:endTime, 3, '" + timezone
                + "') GROUP BY group_key ORDER BY group_key";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startTime", formatTrendBoundary(startTime));
        query.setParameter("endTime", formatTrendBoundary(endTime));
        Map<String, Long> resultMap = new LinkedHashMap<>();
        List<Object[]> result = query.getResultList();
        for (Object[] row : result) {
            if (row == null || row.length < 2) {
                throw new IllegalArgumentException("趋势统计查询结果字段数量不足");
            }
            Object count = row[1];
            long value = count instanceof Number number
                    ? number.longValue() : Long.parseLong(String.valueOf(count));
            resultMap.put(String.valueOf(row[0]), value);
        }
        return resultMap;
    }

    private String formatTrendBoundary(Date value) {
        return TREND_BOUNDARY_FORMATTER.format(value.toInstant().atZone(ZoneId.of(retrievalTimeZone)));
    }

    private String trendTimeExpression(String safeField, String columnType, String timeUnit) {
        String baseType = unwrapColumnType(columnType).toLowerCase(Locale.ROOT);
        String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
        if (baseType.startsWith("datetime")) {
            return "toTimeZone(" + safeField + ", '" + timezone + "')";
        }
        if (baseType.startsWith("date")) {
            return "toDateTime(" + safeField + ", '" + timezone + "')";
        }
        if (isNumericTrendType(baseType)) {
            String normalizedUnit = StringUtils.lowerCase(StringUtils.trim(timeUnit), Locale.ROOT);
            if ("seconds".equals(normalizedUnit)) {
                return "toDateTime(" + safeField + ", '" + timezone + "')";
            }
            if ("milliseconds".equals(normalizedUnit)) {
                return "toDateTime(" + safeField + " / 1000, '" + timezone + "')";
            }
        }
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "趋势时间字段类型或单位不受支持");
    }

    private String unwrapColumnType(String columnType) {
        String current = StringUtils.trimToEmpty(columnType);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String wrapper : List.of("Nullable", "LowCardinality")) {
                String prefix = wrapper + "(";
                if (current.regionMatches(true, 0, prefix, 0, prefix.length()) && current.endsWith(")")) {
                    current = current.substring(prefix.length(), current.length() - 1).trim();
                    changed = true;
                }
            }
        }
        return current;
    }

    private boolean isNumericTrendType(String columnType) {
        return columnType.matches("u?int(8|16|32|64|128|256)")
                || columnType.matches("float(32|64)")
                || columnType.startsWith("decimal");
    }

    @Override
    @Transactional
    public Map<String, Object> countByField(String tableName, String field) {
        String safeField = requireIdentifier(field, "字段名");
        String countSql = "select " + safeField + " as group_key, count(*) as count from " +
                requireIdentifier(tableName, "表名") + " GROUP BY " + safeField;
        Query query = entityManager.createNativeQuery(countSql);
        // 执行查询
        Map<String, Object> resultMap = new HashMap<>();
        List<Object[]> result = query.getResultList();
        result.forEach(row -> {
            if (row.length < 2) {
                // 日志记录或抛出自定义异常
                throw new IllegalArgumentException("查询结果字段数量不足，期望至少2个字段");
            }
            resultMap.put(String.valueOf(row[0]), row[1]);
        });
        return resultMap;
    }

    public Map<String, Object> findByPage(String tableName, Map<String, Object> searchMap, RetrievalPageable pageable, List<DataAttribute> dataAttributes) {
        List<DisplayColumn> displayColumnList = dataAttributes.stream().map(attribute -> new DisplayColumn().fromDisplayColumn(attribute)).toList();
        List<String> selectColumnList = displayColumnList.stream().map(this::convertDisplayColumn).toList();
        String columnSelectSql = StringUtils.join(selectColumnList, ",");
        // 获取有效的查询参数
        Map<String, DataAttribute> attributeMap = dataAttributes.stream()
                .collect(Collectors.toMap(DataAttribute::getName, attribute -> attribute, (first, second) -> first));
        Map<String, DataAttribute> columnMap = dataAttributes.stream()
                .collect(Collectors.toMap(DataAttribute::getColumnName, attribute -> attribute, (first, second) -> first));
        List<String> validSearchItemList = searchMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && StringUtils.isNotEmpty(entry.getValue().toString()))
                .filter(entry -> attributeMap.containsKey(entry.getKey()) || columnMap.containsKey(entry.getKey()))
                .map(entry -> {
                    DataAttribute dataAttribute = attributeMap.getOrDefault(entry.getKey(), columnMap.get(entry.getKey()));
                    Object value = entry.getValue();
                    String columnName = requireIdentifier(dataAttribute.getColumnName(), "字段名");
                    if (isArrayType(dataAttribute.getColumnType())) {
                        return " has(%s, %s) ".formatted(columnName, formatSingleValue(value.toString(), dataAttribute.getColumnType(), null));
                    } else {
                        return " %s = %s ".formatted(columnName, formatSingleValue(value.toString(), dataAttribute.getColumnType(), null));
                    }
                })
                .toList();
        String whereClause = " where 1=1";
        if (CollectionUtils.isNotEmpty(validSearchItemList)) {
            whereClause = " where " + validSearchItemList.stream().collect(Collectors.joining(" and "));
        }
        String pageClause = buildPage(pageable);
        String querySql = "select " + columnSelectSql + " from " + requireIdentifier(tableName, "表名") + whereClause + pageClause;
        log.info("get sql {}", querySql);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("total", queryCount(tableName, whereClause));
        resultMap.put("data", queryResultList(querySql, displayColumnList));
        return resultMap;
    }

    @Transactional
    public List<String> getDistinct(String tableName, String attribute) {
        String selectSql = "select DISTINCT " + requireIdentifier(attribute, "字段名") + " from " +
                requireIdentifier(tableName, "表名") + " limit 50";
        Query query = entityManager.createNativeQuery(selectSql);
        // 执行查询
        List<String> resultList = new ArrayList<>();
        List<String> result = query.getResultList();
        result.forEach(row -> {
            resultList.add(row);
        });
        return resultList;
    }

    @Transactional
    public List<String> getDistinctForArray(String tableName, String attribute) {
        String safeAttribute = requireIdentifier(attribute, "字段名");
        String selectSql = "select DISTINCT arrayJoin(%s) from %s WHERE %s IS NOT NULL AND %s != [] limit 50"
                .formatted(safeAttribute, requireIdentifier(tableName, "表名"), safeAttribute, safeAttribute);
        Query query = entityManager.createNativeQuery(selectSql);
        // 执行查询
        List<String> resultList = new ArrayList<>();
        List<String> result = query.getResultList();
        result.forEach(row -> {
            resultList.add(row);
        });
        return resultList;
    }

    @Transactional
    public List<String> getLike(String tableName, String attribute, String searchTerm) {
        String safeAttribute = requireIdentifier(attribute, "字段名");
        String selectSql = "select DISTINCT " + safeAttribute + " from " + requireIdentifier(tableName, "表名") +
                " where " + safeAttribute + " like " + likeQuote(searchTerm) + " limit 50";
        Query query = entityManager.createNativeQuery(selectSql);
        // 执行查询
        List<String> resultList = new ArrayList<>();
        List<String> result = query.getResultList();
        result.forEach(row -> {
            resultList.add(row);
        });
        return resultList;
    }

    @Override
    public Map<String, Object> queryWithRetrieval(DataQuery dataQuery, RetrievalPageable pageable) {
        if (dataQuery == null || CollectionUtils.isEmpty(dataQuery.getDisplayColumnList())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
        String tableName = requireIdentifier(dataQuery.getTableName(), "表名");
        List<ColumnCriteria> columnCriteria = dataQuery.getColumnCriteria();
        String whereClause = "";
        String sql = dataQuery.getSql();
        if (StringUtils.isNotBlank(sql)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "自由SQL检索已禁用，请使用受限where表达式");
        } else if (Objects.nonNull(dataQuery.getCriteriaExpression())) {
            whereClause += " where " + buildCriteriaExpressionSql(dataQuery.getCriteriaExpression());
        } else if (CollectionUtils.isNotEmpty(columnCriteria)) {
            String logic = normalizeLogic(dataQuery.getCriteriaLogic());
            whereClause += " where " + columnCriteria.stream().map(this::buildCriteriaSql)
                    .collect(Collectors.joining(" " + logic + " "));
        }
        String pageClause = buildPage(pageable);
        List<String> selectColumnList = dataQuery.getDisplayColumnList().stream().map(this::convertDisplayColumnWithAs).toList();

        String columnSelectSql = StringUtils.join(selectColumnList, ",");
        String querySql = "select " + columnSelectSql + " from " + tableName + whereClause + pageClause;
        log.debug("retrieval sql={}", querySql);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("total", queryCount(tableName, whereClause));
        resultMap.put("data", queryResultList(querySql, dataQuery.getDisplayColumnList()));
        return resultMap;
    }

    @Transactional
    private BigDecimal queryCount(String tableName, String whereClause) {
        String countSql = "select count(*) from " + requireIdentifier(tableName, "表名") + whereClause;
        BigDecimal total = BigDecimal.valueOf(0);
        Query query = entityManager.createNativeQuery(countSql);
        // 执行查询
        List<BigDecimal> result = query.getResultList();
        if (result.size() > 0) {
            total = result.get(0);
        }
        return total;
    }

    private String convertDisplayColumnWithAs(DisplayColumn displayColumn) {
        String columnName = requireIdentifier(displayColumn.getColumnName(), "字段名");
        String displayName = requireIdentifier(displayColumn.getDisplayName(), "字段别名");
        String displayType = displayColumn.getDisplayType();
        if (StringUtils.isBlank(displayType)) {
            return columnName + " as " + displayName;
        }
        return switch (displayType) {
            case "json" -> "toJSONString(" + columnName + ") as " + displayName;
            case "array" -> "arrayStringConcat(" + columnName + ",',') as " + displayName;
            default -> columnName + " as " + displayName;
        };
    }

    private String convertDisplayColumn(DisplayColumn displayColumn) {
        String columnName = requireIdentifier(displayColumn.getColumnName(), "字段名");
        String displayType = displayColumn.getDisplayType();
        if (StringUtils.isBlank(displayType)) {
            return columnName;
        }
        return switch (displayType) {
            case "json" -> "toJSONString(" + columnName + ")";
            case "array" -> "arrayStringConcat(" + columnName + ",',') ";
            default -> columnName;
        };
    }

    private String buildPage(RetrievalPageable pageable) {
        int page = pageable != null && Objects.nonNull(pageable.getPage()) ? pageable.getPage() : 1;
        int size = pageable != null && Objects.nonNull(pageable.getSize()) ? pageable.getSize() : DEFAULT_PAGE_SIZE;
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "分页参数不合法");
        }

        String pageStr = "";
        if (pageable != null && StringUtils.isNotBlank(pageable.getSortBy())) {
            String sortBy = pageable.getSortBy();
            String order = pageable.getOrder();
            if (StringUtils.isNotBlank(order) && !StringUtils.equalsAnyIgnoreCase(order, "asc", "desc")) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "排序方向仅支持asc或desc");
            }
            String orderStr = StringUtils.equalsIgnoreCase(order, "asc") ? "asc" : "desc";
            pageStr = " order by " + requireIdentifier(sortBy, "排序字段") + " " + orderStr;
        }
        pageStr += " limit " + ((long) (page - 1) * size) + "," + size;
        return pageStr;
    }

    private String buildCriteriaSql(ColumnCriteria columnCriteria) {
        String columnName = requireIdentifier(columnCriteria.getColumnName(), "字段名");
        String operatorName = columnCriteria.getOperatorName();
        List<String> valueList = columnCriteria.getValueList() == null ? Collections.emptyList() : columnCriteria.getValueList();
        if (StringUtils.isNotBlank(columnCriteria.getRetrievalType())) {
            valueList = valueList.stream()
                    .map(value -> convertValueList(value, columnCriteria.getRetrievalType(), columnCriteria.getColumnType()))
                    .toList();
        }
        if (isNullOperator(operatorName)) {
            return buildNullCriteriaSql(columnName, operatorName, columnCriteria.getColumnType());
        }
        if (CollectionUtils.isEmpty(valueList)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件值不能为空");
        }
        if (isArrayType(columnCriteria.getColumnType())) {
            return buildArrayCriteriaSql(columnName, operatorName, valueList, columnCriteria.getColumnType());
        }
        return switch (operatorName) {
            case "equal" -> columnName + " = " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "notequal" -> columnName + " != " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "match" -> columnName + " like " + likeQuote(valueList.get(0));
            case "greatthan" -> columnName + " > " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "lessthan" -> columnName + " < " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "greatequalthan" -> columnName + " >= " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "lessequalthan" -> columnName + " <= " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "between" -> columnName + " between " + formatSingleValue(valueList.get(0), columnCriteria.getColumnType(), columnCriteria.getRetrievalType()) +
                    " and " + formatSingleValue(valueList.get(1), columnCriteria.getColumnType(), columnCriteria.getRetrievalType());
            case "in" -> columnName + " in " + "(" + StringUtils.join(valueList.stream()
                    .map(value -> formatSingleValue(value, columnCriteria.getColumnType(), columnCriteria.getRetrievalType())).toList(), ",") + ")";
            default -> throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "不支持的检索操作符: " + operatorName);
        };
    }

    private boolean isNullOperator(String operatorName) {
        return "isnull".equals(operatorName) || "isnotnull".equals(operatorName);
    }

    private String buildNullCriteriaSql(String columnName, String operatorName, String columnType) {
        boolean checksEmptyValue = isArrayType(columnType) || isStringType(columnType);
        if ("isnull".equals(operatorName)) {
            if (checksEmptyValue) {
                return "(" + columnName + " is null or length(" + columnName + ") = 0)";
            }
            return columnName + " is null";
        }
        if ("isnotnull".equals(operatorName)) {
            if (checksEmptyValue) {
                return "(" + columnName + " is not null and length(" + columnName + ") > 0)";
            }
            return columnName + " is not null";
        }
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "不支持的检索操作符: " + operatorName);
    }

    private String buildCriteriaExpressionSql(ColumnCriteriaExpression expression) {
        if (expression == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件不能为空");
        }
        if ("condition".equals(expression.getType())) {
            return buildCriteriaSql(expression.getCriteria());
        }
        if (!"group".equals(expression.getType()) || CollectionUtils.isEmpty(expression.getChildren())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件不能为空");
        }
        String logic = normalizeLogic(expression.getLogic());
        return "(" + expression.getChildren().stream()
                .map(this::buildCriteriaExpressionSql)
                .collect(Collectors.joining(" " + logic + " ")) + ")";
    }

    private String convertValueList(String origin, String retrievalType, String columnType) {
        switch (retrievalType) {
            case "date":
                if (isTemporalType(columnType)) {
                    return normalizeTemporalDateValue(origin, columnType);
                }
                long epoch = LocalDateTime.parse(origin, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.of(retrievalTimeZone)).toInstant().toEpochMilli();
                return Long.toString(epoch);
            default:
                return origin;
        }
    }

    private String normalizeTemporalDateValue(String origin, String columnType) {
        if (!EPOCH_MILLIS_PATTERN.matcher(origin).matches()) {
            return origin;
        }
        try {
            Instant instant = Instant.ofEpochMilli(Long.parseLong(origin));
            DateTimeFormatter formatter = unwrapColumnType(columnType)
                    .toLowerCase(Locale.ROOT)
                    .startsWith("datetime64")
                    ? TREND_BOUNDARY_FORMATTER
                    : DATE_TIME_FORMATTER;
            return formatter.format(instant.atZone(ZoneId.of(retrievalTimeZone)));
        } catch (NumberFormatException | DateTimeException e) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "毫秒时间戳条件不合法: " + origin);
        }
    }

    private String buildArrayCriteriaSql(String columnName, String operatorName, List<String> valueList, String columnType) {
        return switch (operatorName) {
            case "equal" -> "has(" + columnName + ", " + formatSingleValue(valueList.get(0), columnType, null) + ")";
            case "notequal" -> "not has(" + columnName + ", " + formatSingleValue(valueList.get(0), columnType, null) + ")";
            case "in" -> "hasAny(" + columnName + ", [" + StringUtils.join(valueList.stream()
                    .map(value -> formatSingleValue(value, columnType, null)).toList(), ",") + "])";
            case "match" -> "arrayStringConcat(" + columnName + ", ',') like " + likeQuote(valueList.get(0));
            default -> throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "数组字段不支持当前操作符: " + operatorName);
        };
    }

    private String normalizeLogic(String logic) {
        return StringUtils.equalsIgnoreCase(logic, "or") ? "or" : "and";
    }

    private String requireIdentifier(String identifier, String fieldLabel) {
        if (StringUtils.isBlank(identifier) || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), fieldLabel + "不合法: " + identifier);
        }
        return identifier;
    }

    private String formatSearchValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return quote(String.valueOf(value));
    }

    private String formatSingleValue(String value, String columnType, String retrievalType) {
        if (StringUtils.equalsIgnoreCase(retrievalType, "date") && isTemporalType(columnType)) {
            return temporalLiteral(value, columnType);
        }
        if (StringUtils.equalsIgnoreCase(retrievalType, "date") || isNumericType(columnType)) {
            return requireNumberLiteral(value);
        }
        return quote(value);
    }

    private String temporalLiteral(String value, String columnType) {
        String baseType = unwrapColumnType(columnType).toLowerCase(Locale.ROOT);
        if (baseType.startsWith("datetime64")) {
            String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
            return "toDateTime64(" + quote(value) + ", 3, '" + timezone + "')";
        }
        if (baseType.startsWith("datetime")) {
            String timezone = ZoneId.of(retrievalTimeZone).getId().replace("'", "''");
            return "toDateTime(" + quote(value) + ", '" + timezone + "')";
        }
        return "toDate(" + quote(value) + ")";
    }

    private String requireNumberLiteral(String value) {
        if (StringUtils.isBlank(value) || !value.matches("-?\\d+(\\.\\d+)?")) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "数值条件不合法: " + value);
        }
        return value;
    }

    private boolean isNumericType(String columnType) {
        if (StringUtils.isBlank(columnType)) {
            return false;
        }
        String lowerType = columnType.toLowerCase(Locale.ROOT);
        return lowerType.contains("int")
                || lowerType.contains("float")
                || lowerType.contains("decimal")
                || lowerType.contains("double");
    }

    private boolean isTemporalType(String columnType) {
        return unwrapColumnType(columnType).toLowerCase(Locale.ROOT).startsWith("date");
    }

    private boolean isStringType(String columnType) {
        return StringUtils.containsIgnoreCase(columnType, "String");
    }

    private boolean isArrayType(String columnType) {
        return StringUtils.startsWithIgnoreCase(columnType, "Array");
    }

    private String quote(String value) {
        return "'" + StringUtils.defaultString(value).replace("'", "''") + "'";
    }

    private String likeQuote(String value) {
        return quote("%" + escapeLikeValue(value) + "%");
    }

    private String escapeLikeValue(String value) {
        return StringUtils.defaultString(value)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Transactional
    private List<Map<String, Object>> queryResultList(String sql, List<DisplayColumn> columnList) {
        List<Map<String, Object>> resultMapList = new ArrayList<>();
        Query query = entityManager.createNativeQuery(sql);
        // 执行查询
        List<?> result = query.getResultList();
        result.forEach(row -> {
            Object[] rowValues = columnList.size() == 1 ? new Object[]{row} : (Object[]) row;
            Map<String, Object> resultMap = new HashMap<>();
            for (int i = 0; i < columnList.size(); i++) {
                DisplayColumn displayColumn = columnList.get(i);
                if (rowValues[i] == null) {
                    resultMap.put(displayColumn.getDisplayName(), null);
                    continue;
                }
                if ("json".equals(displayColumn.getDisplayType())) {
                    String jsonString = rowValues[i].toString();
                    if (jsonString.startsWith("[") && jsonString.endsWith("]")) {
                        List<Object> jsonList = JacksonUtil.toList(jsonString, new TypeReference<List<Object>>() {
                        });
                        resultMap.put(displayColumn.getDisplayName(), jsonList);
                    } else {
                        Map<String, Object> jsonMap = JacksonUtil.toMap(jsonString, new TypeReference<>() {
                        });
                        resultMap.put(displayColumn.getDisplayName(), jsonMap);
                    }
                } else {
                    resultMap.put(displayColumn.getDisplayName(), rowValues[i]);
                }
            }
            resultMapList.add(resultMap);
        });
        return resultMapList;
    }

}
