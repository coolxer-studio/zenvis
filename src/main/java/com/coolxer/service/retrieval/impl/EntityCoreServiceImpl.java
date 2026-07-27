package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
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
