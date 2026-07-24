package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.query.DataQuery;
import com.coolxer.model.retrieval.query.IpEventTimelineQuerySource;
import com.coolxer.model.retrieval.query.IpRelationQuerySource;
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

    public Map<String, Object> findById(String tableName, String keyColumn, String id,
                                        List<DataAttribute> dataAttributes);

    public BigDecimal count(String tableName, Map<String, Object> searchMap);

    /**
     * 统计任意一个指定字段精确匹配给定值的数据量。
     *
     * @param tableName 表名
     * @param fields    参与 OR 匹配的字段列表
     * @param value     精确匹配值
     * @return 匹配的数据量
     */
    BigDecimal countAnyOf(String tableName, List<String> fields, String value);

    /**
     * 在指定业务时间范围内统计源或目的字段精确匹配 IP 的记录数。
     */
    BigDecimal countAnyOfInTime(IpRelationQuerySource source, String value,
                                String startTime, String endTime);

    /**
     * 在 ClickHouse 中跨实体聚合真实对端 IP，并返回全局 Top N。
     */
    Map<String, Object> findIpRelations(List<IpRelationQuerySource> sources, String value,
                                        String startTime, String endTime, int limit);

    /**
     * 在 ClickHouse 中跨实体按时间、方向和事件分类聚合安全事件。
     */
    Map<String, Object> findIpEventTimeline(List<IpEventTimelineQuerySource> sources,
                                            String value,
                                            String startTime,
                                            String endTime,
                                            boolean hourly);

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
