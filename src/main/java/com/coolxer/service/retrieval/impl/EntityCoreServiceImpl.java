package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EntityCoreServiceImpl implements EntityCoreService {

    @Autowired
    private MetaDataService metaDataService;

    @Autowired
    private QueryEngine queryEngine;

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
            queryEngine.delete(dataEntity.getTableName(), "id", id);
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteALL(String entityName, List<String> ids) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            queryEngine.deleteIn(dataEntity.getTableName(), "id", ids);
            return true;
        }
        return false;
    }

    @Override
    public boolean update(String entityName, String id, Map<String, Object> mapDto) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            Map<String, String> columnValueMap = getColumnValueMap(entityName, mapDto);
            // 剔除orderBy的主键字段
            if (dataEntity.getAutoCreate() != null) {
                dataEntity.getAutoCreate().getOrderBy().forEach(orderBy -> {
                    columnValueMap.remove(orderBy);
                });
            }
            // 剔除id
            columnValueMap.remove("id");
            // json类型的字段暂不支持更新
            metaDataService.getAllDataAttributeByEntity(dataEntity).stream().forEach(
                    dataAttribute -> {
                        if (dataAttribute.getColumnType().equalsIgnoreCase("json")) {
                            columnValueMap.remove(dataAttribute.getColumnName());
                        }
                    }
            );
            queryEngine.update(dataEntity.getTableName(), columnValueMap, "id", id);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> getOne(String entityName, String id) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            List<DataAttribute> dataAttributes = metaDataService.getAllDataAttributeByEntity(dataEntity);
            Map<String, Object> result = queryEngine.findById(dataEntity.getTableName(), id, dataAttributes);
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
                Map<String, Object> result = queryEngine.countByDateOfWeek(dataEntity.getTableName(), "insert_time");
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

    private Map<String, String> getColumnValueMap(String entityName, Map<String, Object> mapDto) {
        Map<String, String> columnValueMap = new HashMap<>();
        mapDto.entrySet().stream().forEach(entry -> {
            String columnName = entry.getKey();
            // 检查是否mapping的备选值
            DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, columnName);
            if (dataAttribute == null) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + columnName);
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

    private String escapeSqlValue(String value) {
        return value.replace("'", "''");
    }
}
