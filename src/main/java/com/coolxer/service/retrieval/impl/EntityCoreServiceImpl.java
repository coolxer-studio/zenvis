package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest;
import com.coolxer.model.retrieval.query.IpEventTimelineQuerySource;
import com.coolxer.model.retrieval.query.IpRelationQueryRequest;
import com.coolxer.model.retrieval.query.IpRelationQuerySource;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EntityCoreServiceImpl implements EntityCoreService {

    private static final List<String> IP_FIELD_NAMES = List.of("src_ip", "dst_ip", "dest_ip");
    private static final Set<Integer> IP_RELATION_LIMITS = Set.of(20, 50, 100);
    private static final Duration MAX_IP_RELATION_RANGE = Duration.ofDays(90);
    private static final Duration HOURLY_EVENT_TIMELINE_RANGE = Duration.ofHours(48);
    private static final DateTimeFormatter IP_RELATION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    @Autowired
    private MetaDataService metaDataService;

    @Autowired
    private QueryEngine queryEngine;

    @Value("${app.retrieval.time-zone:Asia/Shanghai}")
    private String retrievalTimeZone = "Asia/Shanghai";

    @Override
    public boolean add(String entityName, Map<String, Object> mapDto) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            List<String> columnList = new ArrayList<>();
            List<String> valueList = new ArrayList<>();
            getColumnValueMap(entityName, mapDto).entrySet().stream().forEach(entry -> {
                columnList.add(entry.getKey());
                valueList.add(entry.getValue());
            });
            queryEngine.save(dataEntity.getTableName(), columnList, valueList);
            return true;
        }

        return false;
    }

    @Override
    public boolean delete(String entityName, String id) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            queryEngine.delete(dataEntity.getTableName(), MetaDataConstants.RECORD_ID_COLUMN,
                    requireRecordId(id));
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteALL(String entityName, List<String> ids) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            queryEngine.deleteIn(dataEntity.getTableName(), MetaDataConstants.RECORD_ID_COLUMN,
                    requireRecordIds(ids));
            return true;
        }
        return false;
    }

    @Override
    public boolean update(String entityName, String id, Map<String, Object> mapDto) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            String recordId = requireRecordId(id);
            Map<String, String> columnValueMap = getColumnValueMap(entityName, mapDto);
            // 剔除orderBy的主键字段
            if (dataEntity.getAutoCreate() != null) {
                dataEntity.getAutoCreate().getOrderBy().forEach(orderBy -> {
                    columnValueMap.remove(orderBy);
                });
            }
            // 平台内置字段不可更新；写入校验之外再做一次防御性过滤。
            columnValueMap.remove(MetaDataConstants.RECORD_ID_COLUMN);
            columnValueMap.remove(MetaDataConstants.INSERT_TIME_COLUMN);
            // json类型的字段暂不支持更新
            metaDataService.getAllDataAttributeByEntity(dataEntity).stream().forEach(
                    dataAttribute -> {
                        if (dataAttribute.getColumnType().equalsIgnoreCase("json")) {
                            columnValueMap.remove(dataAttribute.getColumnName());
                        }
                    }
            );
            queryEngine.update(dataEntity.getTableName(), columnValueMap,
                    MetaDataConstants.RECORD_ID_COLUMN, recordId);
            return true;
        }
        return false;
    }

    @Override
    public boolean updateALL(String entityName, List<String> ids, Map<String, Object> mapDto) {
        List<String> recordIds = requireRecordIds(ids);
        if (metaDataService.getDataEntityByName(entityName) == null) {
            return false;
        }
        for (String recordId : recordIds) {
            update(entityName, recordId, mapDto);
        }
        return true;
    }

    @Override
    public Map<String, Object> getOne(String entityName, String id) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            List<DataAttribute> dataAttributes = metaDataService.getAllDataAttributeByEntity(dataEntity);
            Map<String, Object> result = queryEngine.findById(
                    dataEntity.getTableName(), MetaDataConstants.RECORD_ID_COLUMN,
                    requireRecordId(id), dataAttributes);
            return result;
        }
        return null;
    }

    @Override
    public PageRowsVo<Map<String, Object>> getPageList(String entityName, Map<String, Object> searchMapDto) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            List<DataAttribute> dataAttributes = metaDataService.getAllDataAttributeByEntity(dataEntity);
            // searchMapDto 提取pageable 参数
            int page = parseIntOrDefault(searchMapDto.remove("page"), 1);
            int perPage = parseIntOrDefault(removeCompatibleParam(searchMapDto, "perPage", "per_page"), 10);
            String orderBy = compatibleStringParam(searchMapDto, "orderBy", "sort_by");
            String orderDir = compatibleStringParam(searchMapDto, "orderDir", "order", "sort_order");
            if (orderBy != null) {
                Map<String, DataAttribute> attributeMap = dataAttributes.stream()
                        .collect(Collectors.toMap(DataAttribute::getName, Function.identity(), (first, second) -> first));
                DataAttribute sortAttribute = attributeMap.get(orderBy);
                orderBy = sortAttribute == null ? null : sortAttribute.getColumnName();
            }
            RetrievalPageable pageable = new RetrievalPageable(page, perPage, orderBy, orderDir);
            Map<String, Object> byPage = queryEngine.findByPage(dataEntity.getTableName(), searchMapDto, pageable, dataAttributes);
            return new PageRowsVo<>((List<Map<String, Object>>) byPage.get("data"), ((BigDecimal) byPage.get("total")).longValue());
        }
        return null;
    }

    private Object removeCompatibleParam(Map<String, Object> params, String primaryKey, String compatibleKey) {
        Object primaryValue = params.remove(primaryKey);
        Object compatibleValue = params.remove(compatibleKey);
        return primaryValue != null ? primaryValue : compatibleValue;
    }

    private String compatibleStringParam(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.remove(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private int parseIntOrDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Map<String, Object> getAttributeMapping(String entityName, String attribute) {
        DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, attribute);
        if (dataAttribute != null) {
            return dataAttribute.getMapping();
        }
        return null;
    }

    @Override
    public List<String> getDistinctAttributes(String entityName, String attribute) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, attribute);
            if (dataAttribute == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + attribute);
            }
            if (dataAttribute.getColumnType().startsWith("Array")) {
                return queryEngine.getDistinctForArray(dataEntity.getTableName(), dataAttribute.getColumnName());
            } else {
                return queryEngine.getDistinct(dataEntity.getTableName(), dataAttribute.getColumnName());
            }
        }
        return null;
    }

    @Override
    public List<String> getSimilarAttributes(String entityName, String attribute, String term) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, attribute);
            if (dataAttribute == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + attribute);
            }
            return queryEngine.getLike(dataEntity.getTableName(), dataAttribute.getColumnName(), term);
        }
        return null;
    }

    @Override
    public long countTotal(String entityName, Map<String, Object> searchMapDto) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            return queryEngine.count(dataEntity.getTableName(), searchMapDto).longValue();
        }
        return 0;
    }

    @Override
    public Map<String, Object> count(List<String> entities) {
        Map<String, Object> countData = new HashMap<>();
        for (String entityName : entities) {
            DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
            if (dataEntity != null) {
                countData.put(entityName, queryEngine.countToday(dataEntity.getTableName(), null).longValue());
            }
        }
        return countData;
    }

    @Override
    public Map<String, Object> countToady(List<String> entities) {
        Map<String, Object> countData = new HashMap<>();
        for (String entityName : entities) {
            DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
            if (dataEntity != null) {
                countData.put(entityName, queryEngine.count(dataEntity.getTableName(), null).longValue());
            }
        }
        return countData;
    }

    @Override
    public Map<String, Object> trend(List<String> entities) {
        List<String> assetNames = new ArrayList<>();
        List<String> assetLabels = new ArrayList<>();
        List<Map<String, Object>> trendDataList = new ArrayList<>();
        for (String entityName : entities) {
            DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
            if (dataEntity != null) {
                assetNames.add(dataEntity.getName());
                assetLabels.add(dataEntity.getLabel());
                Map<String, Object> result = queryEngine.countByDateOfWeek(
                        dataEntity.getTableName(), MetaDataConstants.INSERT_TIME_COLUMN);
                trendDataList.add(result);
            }
        }
        // 获取最近7天日期（格式：yyyy-MM-dd 00:00:00）
        List<String> dateList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00.0");
        for (int i = 6; i >= 0; i--) {
            dateList.add(today.minusDays(i).format(formatter));
        }
        // 组装series数据
        Map<String, List<Long>> seriesMap = new LinkedHashMap<>();
        for (int i = 0; i < assetNames.size(); i++) {
            String key = assetNames.get(i);
            Map<String, Object> trendData = trendDataList.get(i);
            List<Long> values = new ArrayList<>();
            for (String date : dateList) {
                Object v = trendData.getOrDefault(date, 0L);
                values.add(Long.parseLong(String.valueOf(v)));
            }
            seriesMap.put(key, values);
        }
        // 组装返回结构
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("legend_data", assetLabels);
        result.put("xaxis_data", dateList);
        // 每个资产类型一组series
        for (int i = 0; i < assetLabels.size(); i++) {
            result.put("series_data_" + assetNames.get(i), seriesMap.get(assetNames.get(i)));
        }
        return result;
    }

    @Override
    public Map<String, Object> statistics(List<String> entities, String field) {
        Map<String, Object> statisticsData = new HashMap<>();
        List<String> assetNames = new ArrayList<>();
        List<String> assetLabels = new ArrayList<>();
        Set<String> keySet = new HashSet<>();
        List<Map<String, Object>> levelDataList = new ArrayList<>();
        for (String entityName : entities) {
            DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
            if (dataEntity != null) {
                DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, field);
                if (dataAttribute == null) {
                    throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "统计字段不存在: " + field);
                }
                assetNames.add(dataEntity.getName());
                assetLabels.add(dataEntity.getLabel());
                Map<String, Object> result = queryEngine.countByField(dataEntity.getTableName(), dataAttribute.getColumnName());
                keySet.addAll(result.keySet());
                levelDataList.add(result);
            }
        }
        statisticsData.put("yaxis_data", assetLabels);

        // 按资产类型遍历，填充每个等级的数量
        for (Map<String, Object> resultMap : levelDataList) {
            for (String key : keySet) {
                Long value = ((BigDecimal) resultMap.getOrDefault(key, BigDecimal.valueOf(0))).longValue();
                List<Long> series = (List<Long>) statisticsData.getOrDefault("series_data_" + key, new ArrayList<Long>());
                series.add(value);
                statisticsData.put("series_data_" + key, series);
            }
        }
        return statisticsData;
    }

    @Override
    public Map<String, Object> ipStatistics(List<String> entities, String ip) {
        String normalizedIp = ip == null ? null : ip.trim();
        if (normalizedIp == null || normalizedIp.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "IP不能为空");
        }
        LinkedHashSet<String> uniqueEntities = entities == null ? new LinkedHashSet<>() : entities.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(entity -> !entity.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueEntities.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "实体列表不能为空");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> xaxisData = new ArrayList<>();
        List<Long> seriesData = new ArrayList<>();
        long total = 0L;
        int matchedEntityCount = 0;
        for (String entityName : uniqueEntities) {
            DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
            if (dataEntity == null) {
                continue;
            }
            Map<String, DataAttribute> attributesByName = metaDataService.getAllDataAttributeByEntity(dataEntity)
                    .stream()
                    .collect(Collectors.toMap(DataAttribute::getName, Function.identity(), (first, second) -> first));
            List<String> logicalFields = IP_FIELD_NAMES.stream()
                    .filter(attributesByName::containsKey)
                    .toList();
            List<String> columns = logicalFields.stream()
                    .map(field -> attributesByName.get(field).getColumnName())
                    .toList();
            long entityTotal = columns.isEmpty()
                    ? 0L : queryEngine.countAnyOf(dataEntity.getTableName(), columns, normalizedIp).longValue();
            if (entityTotal > 0) {
                matchedEntityCount++;
            }
            total += entityTotal;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", dataEntity.getName());
            row.put("label", dataEntity.getLabel());
            row.put("fields", logicalFields);
            row.put("total", entityTotal);
            rows.add(row);
            xaxisData.add(dataEntity.getLabel());
            seriesData.add(entityTotal);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", normalizedIp);
        result.put("total", total);
        result.put("entity_count", rows.size());
        result.put("matched_entity_count", matchedEntityCount);
        result.put("rows", rows);
        result.put("xaxis_data", xaxisData);
        result.put("series_data", seriesData);
        return result;
    }

    @Override
    public Map<String, Object> ipRelations(IpRelationQueryRequest request) {
        if (request == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "请求体不能为空");
        }
        String normalizedIp = requireNonBlank(request.ip(), "IP不能为空");
        LocalDateTime startTime = parseRelationTime(request.startTime(), "开始时间");
        LocalDateTime endTime = parseRelationTime(request.endTime(), "结束时间");
        if (startTime.isAfter(endTime)) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), "开始时间不得晚于结束时间");
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_IP_RELATION_RANGE) > 0) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), "查询时间跨度不得超过90天");
        }
        String normalizedStartTime = startTime.format(IP_RELATION_TIME_FORMATTER);
        String normalizedEndTime = endTime.format(IP_RELATION_TIME_FORMATTER);
        Integer limit = request.limit();
        if (limit == null || !IP_RELATION_LIMITS.contains(limit)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "limit仅支持20、50或100");
        }
        if (request.entities() == null || request.entities().isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "实体列表不能为空");
        }

        LinkedHashMap<String, DataEntity> requestedEntities = new LinkedHashMap<>();
        for (String entityValue : request.entities()) {
            String entityName = requireNonBlank(entityValue, "实体名称不能为空");
            if (requestedEntities.containsKey(entityName)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "实体名称重复: " + entityName);
            }
            DataEntity entity = metaDataService.getDataEntityByName(entityName);
            if (entity == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "实体不存在: " + entityName);
            }
            requestedEntities.put(entityName, entity);
        }

        if (request.relationMappings() == null || request.relationMappings().isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "关系字段映射不能为空");
        }
        Map<String, IpRelationQuerySource> sourceByEntity = new LinkedHashMap<>();
        for (IpRelationQueryRequest.RelationMapping mapping : request.relationMappings()) {
            if (mapping == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "关系字段映射不能为空");
            }
            String entityName = requireNonBlank(mapping.entity(), "映射实体不能为空");
            DataEntity entity = requestedEntities.get(entityName);
            if (entity == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "映射实体不在请求实体列表中: " + entityName);
            }
            if (sourceByEntity.containsKey(entityName)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "同一实体最多配置一组关系字段映射: " + entityName);
            }
            String sourceField = requireNonBlank(mapping.sourceField(), "源IP字段不能为空");
            String targetField = requireNonBlank(mapping.targetField(), "目的IP字段不能为空");
            String timeField = requireNonBlank(mapping.timeField(), "时间字段不能为空");
            if (new HashSet<>(List.of(sourceField, targetField, timeField)).size() != 3) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "源IP、目的IP和时间字段不得重复: " + entityName);
            }

            Map<String, DataAttribute> attributesByName =
                    metaDataService.getAllDataAttributeByEntity(entity).stream()
                            .collect(Collectors.toMap(
                                    DataAttribute::getName,
                                    Function.identity(),
                                    (first, second) -> first,
                                    LinkedHashMap::new));
            DataAttribute sourceAttribute = requireLogicalAttribute(
                    attributesByName, entityName, sourceField);
            DataAttribute targetAttribute = requireLogicalAttribute(
                    attributesByName, entityName, targetField);
            DataAttribute timeAttribute = requireLogicalAttribute(
                    attributesByName, entityName, timeField);
            if (!isScalarString(sourceAttribute.getColumnType())
                    || !isScalarString(targetAttribute.getColumnType())) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "源IP和目的IP字段必须是标量String: " + entityName);
            }
            if (!isDateTimeColumn(timeAttribute.getColumnType())) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "时间字段必须是DateTime或DateTime64: " + entityName);
            }
            sourceByEntity.put(entityName, new IpRelationQuerySource(
                    entityName,
                    entity.getLabel(),
                    entity.getTableName(),
                    sourceField,
                    targetField,
                    timeField,
                    sourceAttribute.getColumnName(),
                    targetAttribute.getColumnName(),
                    timeAttribute.getColumnName(),
                    timeAttribute.getColumnType()));
        }

        List<IpRelationQuerySource> sources = requestedEntities.keySet().stream()
                .map(sourceByEntity::get)
                .filter(Objects::nonNull)
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> xaxisData = new ArrayList<>();
        List<Long> seriesData = new ArrayList<>();
        long total = 0L;
        int matchedEntityCount = 0;
        for (Map.Entry<String, DataEntity> entry : requestedEntities.entrySet()) {
            IpRelationQuerySource source = sourceByEntity.get(entry.getKey());
            long entityTotal = source == null ? 0L : queryEngine.countAnyOfInTime(
                    source, normalizedIp, normalizedStartTime, normalizedEndTime).longValue();
            if (entityTotal > 0) {
                matchedEntityCount++;
            }
            total += entityTotal;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity", entry.getKey());
            row.put("label", entry.getValue().getLabel());
            row.put("fields", source == null
                    ? List.of() : List.of(source.sourceField(), source.targetField()));
            row.put("time_field", source == null ? null : source.timeField());
            row.put("total", entityTotal);
            rows.add(row);
            xaxisData.add(entry.getValue().getLabel());
            seriesData.add(entityTotal);
        }

        Map<String, Object> relationData = queryEngine.findIpRelations(
                sources, normalizedIp, normalizedStartTime, normalizedEndTime, limit);
        List<Map<String, Object>> peers = enrichPeerEntityLabels(
                relationData.get("peers"), requestedEntities);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", normalizedIp);
        result.put("start_time", normalizedStartTime);
        result.put("end_time", normalizedEndTime);
        result.put("time_zone", ZoneId.of(retrievalTimeZone).getId());
        result.put("limit", limit);
        result.put("total", total);
        result.put("entity_count", rows.size());
        result.put("matched_entity_count", matchedEntityCount);
        result.put("rows", rows);
        result.put("xaxis_data", xaxisData);
        result.put("series_data", seriesData);
        result.put("relation_total", relationData.getOrDefault("relation_total", 0L));
        result.put("peer_count", peers.size());
        result.put("peer_total", relationData.getOrDefault("peer_total", peers.size()));
        result.put("has_more", relationData.getOrDefault("has_more", false));
        result.put("peers", peers);
        return result;
    }

    @Override
    public Map<String, Object> ipEventTimeline(IpEventTimelineQueryRequest request) {
        if (request == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "请求体不能为空");
        }
        String normalizedIp = requireNonBlank(request.ip(), "IP不能为空");
        LocalDateTime startTime = parseRelationTime(request.startTime(), "开始时间");
        LocalDateTime endTime = parseRelationTime(request.endTime(), "结束时间");
        if (startTime.isAfter(endTime)) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), "开始时间不得晚于结束时间");
        }
        Duration queryRange = Duration.between(startTime, endTime);
        if (queryRange.compareTo(MAX_IP_RELATION_RANGE) > 0) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(), "查询时间跨度不得超过90天");
        }
        if (request.eventMappings() == null || request.eventMappings().isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "事件字段映射不能为空");
        }

        Map<String, IpEventTimelineQuerySource> sourceByEntity = new LinkedHashMap<>();
        for (IpEventTimelineQueryRequest.EventMapping mapping : request.eventMappings()) {
            if (mapping == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "事件字段映射不能为空");
            }
            String entityName = requireNonBlank(mapping.entity(), "映射实体不能为空");
            if (sourceByEntity.containsKey(entityName)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "同一实体最多配置一组事件字段映射: " + entityName);
            }
            DataEntity entity = metaDataService.getDataEntityByName(entityName);
            if (entity == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "实体不存在: " + entityName);
            }

            String sourceField = requireNonBlank(mapping.sourceField(), "源IP字段不能为空");
            String targetField = requireNonBlank(mapping.targetField(), "目的IP字段不能为空");
            String timeField = requireNonBlank(mapping.timeField(), "时间字段不能为空");
            String eventTypeField = requireNonBlank(mapping.eventTypeField(), "事件分类字段不能为空");
            if (new HashSet<>(List.of(
                    sourceField, targetField, timeField, eventTypeField)).size() != 4) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "源IP、目的IP、时间和事件分类字段不得重复: " + entityName);
            }

            Integer eventTypeStart = mapping.eventTypeStart();
            Integer eventTypeLength = mapping.eventTypeLength();
            if (eventTypeStart == null || eventTypeStart < 1 || eventTypeStart > 64) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "事件分类起始位置必须在1到64之间: " + entityName);
            }
            if (eventTypeLength == null || eventTypeLength < 1 || eventTypeLength > 16) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "事件分类长度必须在1到16之间: " + entityName);
            }

            Map<String, DataAttribute> attributesByName =
                    metaDataService.getAllDataAttributeByEntity(entity).stream()
                            .collect(Collectors.toMap(
                                    DataAttribute::getName,
                                    Function.identity(),
                                    (first, second) -> first,
                                    LinkedHashMap::new));
            DataAttribute sourceAttribute = requireLogicalAttribute(
                    attributesByName, entityName, sourceField);
            DataAttribute targetAttribute = requireLogicalAttribute(
                    attributesByName, entityName, targetField);
            DataAttribute timeAttribute = requireLogicalAttribute(
                    attributesByName, entityName, timeField);
            DataAttribute eventTypeAttribute = requireLogicalAttribute(
                    attributesByName, entityName, eventTypeField);
            if (!isScalarString(sourceAttribute.getColumnType())
                    || !isScalarString(targetAttribute.getColumnType())
                    || !isScalarString(eventTypeAttribute.getColumnType())) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "源IP、目的IP和事件分类字段必须是标量String: " + entityName);
            }
            if (!isDateTimeColumn(timeAttribute.getColumnType())) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        "时间字段必须是DateTime或DateTime64: " + entityName);
            }

            sourceByEntity.put(entityName, new IpEventTimelineQuerySource(
                    entityName,
                    entity.getTableName(),
                    sourceField,
                    targetField,
                    timeField,
                    eventTypeField,
                    sourceAttribute.getColumnName(),
                    targetAttribute.getColumnName(),
                    timeAttribute.getColumnName(),
                    eventTypeAttribute.getColumnName(),
                    sourceAttribute.getColumnType(),
                    targetAttribute.getColumnType(),
                    timeAttribute.getColumnType(),
                    eventTypeAttribute.getColumnType(),
                    eventTypeStart,
                    eventTypeLength));
        }

        String normalizedStartTime = startTime.format(IP_RELATION_TIME_FORMATTER);
        String normalizedEndTime = endTime.format(IP_RELATION_TIME_FORMATTER);
        boolean hourly = queryRange.compareTo(HOURLY_EVENT_TIMELINE_RANGE) <= 0;
        Map<String, Object> timelineData = queryEngine.findIpEventTimeline(
                new ArrayList<>(sourceByEntity.values()),
                normalizedIp,
                normalizedStartTime,
                normalizedEndTime,
                hourly);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", normalizedIp);
        result.put("start_time", normalizedStartTime);
        result.put("end_time", normalizedEndTime);
        result.put("time_zone", ZoneId.of(retrievalTimeZone).getId());
        result.put("granularity", hourly ? "hour" : "day");
        result.put("total", timelineData.getOrDefault("total", 0L));
        result.put("inbound_total", timelineData.getOrDefault("inbound_total", 0L));
        result.put("outbound_total", timelineData.getOrDefault("outbound_total", 0L));
        result.put("buckets", timelineData.getOrDefault("buckets", List.of()));
        return result;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), message);
        }
        return value.trim();
    }

    private LocalDateTime parseRelationTime(String value, String label) {
        String normalized = requireNonBlank(value, label + "不能为空");
        try {
            return LocalDateTime.parse(normalized, IP_RELATION_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ApiException(ResultCodeEnum.INVALID_TIME_RANGE.getCode(),
                    label + "格式必须为yyyy-MM-dd HH:mm:ss");
        }
    }

    private DataAttribute requireLogicalAttribute(Map<String, DataAttribute> attributesByName,
                                                  String entityName,
                                                  String logicalField) {
        DataAttribute attribute = attributesByName.get(logicalField);
        if (attribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "实体逻辑字段不存在: " + entityName + "." + logicalField);
        }
        if (attribute.getEntity() != null && !attribute.getEntity().isBlank()
                && !entityName.equals(attribute.getEntity())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                    "字段不属于映射实体: " + entityName + "." + logicalField);
        }
        return attribute;
    }

    private boolean isScalarString(String columnType) {
        return "String".equalsIgnoreCase(unwrapColumnType(columnType));
    }

    private boolean isDateTimeColumn(String columnType) {
        String type = unwrapColumnType(columnType).toLowerCase(Locale.ROOT);
        return type.equals("datetime") || type.startsWith("datetime64(");
    }

    private String unwrapColumnType(String columnType) {
        String current = columnType == null ? "" : columnType.trim();
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> enrichPeerEntityLabels(
            Object rawPeers, Map<String, DataEntity> requestedEntities) {
        if (!(rawPeers instanceof List<?> peerList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object rawPeer : peerList) {
            if (!(rawPeer instanceof Map<?, ?> peerMap)) {
                continue;
            }
            Map<String, Object> peer = new LinkedHashMap<>((Map<String, Object>) peerMap);
            Object rawEntities = peer.get("entities");
            List<Map<String, Object>> entityRows = new ArrayList<>();
            if (rawEntities instanceof List<?> entityList) {
                for (Object rawEntity : entityList) {
                    if (!(rawEntity instanceof Map<?, ?> entityMap)) {
                        continue;
                    }
                    Map<String, Object> entityRow =
                            new LinkedHashMap<>((Map<String, Object>) entityMap);
                    String entityName = String.valueOf(entityRow.get("entity"));
                    DataEntity entity = requestedEntities.get(entityName);
                    entityRow.put("label", entity == null ? entityName : entity.getLabel());
                    entityRows.add(entityRow);
                }
            }
            peer.put("entities", entityRows);
            result.add(peer);
        }
        return result;
    }

    private Map<String, String> getColumnValueMap(String entityName, Map<String, Object> mapDto) {
        Map<String, String> columnValueMap = new HashMap<>();
        mapDto.entrySet().stream().forEach(entry -> {
            String columnName = entry.getKey();
            // 检查是否mapping的备选值
            DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, columnName);
            if (dataAttribute == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + columnName);
            }
            if (MetaDataConstants.isSystemMaintained(dataAttribute)) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                        dataAttribute.getName() + "由系统自动维护，不允许手工写入");
            }
            if (dataAttribute.isMustCandidate() && !dataAttribute.getMapping().containsValue(entry.getValue())) {
                throw new ApiException(ResultCodeEnum.FIELD_NOT_CANDIDATE.getCode(), ResultCodeEnum.FIELD_NOT_CANDIDATE.getDescription());
            }
            String keyColumn = dataAttribute.getColumnName();
            switch (dataAttribute.getColumnType()) {
                case "String":
                case "DateTime64(3)":
                case "json":
                    columnValueMap.put(keyColumn, "'%s'".formatted(escapeSqlValue(entry.getValue().toString())));
                    break;
                case "Array(String)":
                    columnValueMap.put(keyColumn, "['%s']".formatted(escapeSqlValue(entry.getValue().toString()).replaceAll(",", "','")));
                    break;
                case "UInt16":
                case "Float64":
                default:
                    columnValueMap.put(keyColumn, entry.getValue().toString());
                    break;
            }
        });
        return columnValueMap;
    }

    private List<String> requireRecordIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "记录ID不能为空");
        }
        return ids.stream().map(this::requireRecordId).toList();
    }

    private String requireRecordId(String id) {
        if (id == null || id.isBlank()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "记录ID不能为空");
        }
        String normalized = id.trim();
        try {
            UUID uuid = UUID.fromString(normalized);
            if (!uuid.toString().equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException("非标准UUID格式");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "记录ID必须为标准UUID格式");
        }
        return normalized;
    }

    private String escapeSqlValue(String value) {
        return value.replace("'", "''");
    }
}
