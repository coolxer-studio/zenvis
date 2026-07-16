package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.query.DataQuery;
import com.coolxer.model.retrieval.rule.RetrievalPageable;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface QueryEngine {

    public void save(String tableName, List<String> columnList, List<String> valueList);

    public void update(String tableName, Map<String, String> mapData, String keyColumn, String keyValue);

    public void delete(String tableName, String keyColumn, String keyValue);

    public void deleteIn(String tableName, String keyColumn, List<String> keyValueList);

    public Map<String, Object> findById(String tableName, String id, List<DataAttribute> dataAttributes);

    public BigDecimal count(String tableName, Map<String, Object> searchMap);

    public BigDecimal countToday(String tableName, Map<String, Object> searchMap);

    Map<String, Object> countByDateOfWeek(String tableName, String timeField);

    Map<String, Long> countByTimeRange(String tableName,
                                       String timeField,
                                       String columnType,
                                       String timeUnit,
                                       Date startTime,
                                       Date endTime,
                                       boolean hourly);

    Map<String, Object> countByField(String tableName, String field);

    public Map<String, Object> findByPage(String tableName, Map<String, Object> searchMap, RetrievalPageable pageable, List<DataAttribute> dataAttributes);

    public List<String> getDistinct(String tableName, String attribute);

    public List<String> getDistinctForArray(String tableName, String attribute);

    public List<String> getLike(String tableName, String attribute, String searchTerm);

    public Map<String, Object> queryWithRetrieval(DataQuery dataQuery, RetrievalPageable pageable);

}
