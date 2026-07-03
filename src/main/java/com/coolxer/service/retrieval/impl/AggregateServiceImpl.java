package com.coolxer.service.retrieval.impl;

import com.coolxer.model.dashboard.vo.StackedLineChartVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.vo.AggregateMsgInfoVo;
import com.coolxer.service.retrieval.AggregateService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import com.coolxer.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AggregateServiceImpl implements AggregateService {

    @Autowired
    MetaDataService metaDataService;
    @Autowired
    QueryEngine queryEngine;

    @Override
    public AggregateMsgInfoVo findAgendaTagsByParams(Map<String, String> params) {
        String entityName = params.remove("entity_name");
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            String tableName = dataEntity.getTableName();
            String whereClause = buildWhereClauseForAggregate(dataEntity, params);
            // 获取数据
            Map<String, Object> agendaTagsMap = queryEngine.groupAgendaTagsWithWhereClause(tableName, whereClause);
            ArrayList<AggregateMsgInfoVo.Tag> tags = new ArrayList<>();
            if (Objects.nonNull(agendaTagsMap) && agendaTagsMap.containsKey("agenda_tags_array")) {
                String agendaTagsArrayString = (String) agendaTagsMap.get("agenda_tags_array");
                // 使用flatMap将二维集合展平成一维
                List<String> agendaTagsList = List.of(agendaTagsArrayString.split(","));
                // 使用Collectors.groupingBy和Collectors.counting来统计元素出现次数
                Map<String, Long> agendaTagsCountMap = agendaTagsList.stream()
                        .collect(Collectors.groupingBy(e -> e, Collectors.counting()));
                Map<String, List<String>> tagMap = new HashMap<>();

                agendaTagsCountMap.entrySet().forEach(entry -> {
                    String key = entry.getKey();
                    String label = key;
                    String level = "other";
                    if (key.contains(":")) {
                        label = StringUtils.substringBeforeLast(entry.getKey(), ":");
                        level = StringUtils.substringAfterLast(entry.getKey(), ":").toLowerCase();
                    }
                    String value = label + "(" + entry.getValue() + ")";
                    List valueList = tagMap.computeIfAbsent(level, v -> new ArrayList<String>());
                    valueList.add(value);
                });
                for (Map.Entry<String, List<String>> entry : tagMap.entrySet()) {
                    tags.add(new AggregateMsgInfoVo.Tag(entry.getKey(), entry.getValue()));
                }
            }
            return new AggregateMsgInfoVo(tags);
        }
        return null;
    }

    @Override
    public StackedLineChartVo findMsgTrend(Map<String, String> params) {
        String entityName = params.remove("entity_name");
        DataEntity dataEntity = metaDataService.getDataEntityByName(entityName);
        if (dataEntity != null) {
            String tableName = dataEntity.getTableName();
            // 从参数中获取必需的值
            String startTimeStr = params.get("start_time");
            String endTimeStr = params.get("end_time");
            Date startTime = DateUtil.parseDate(startTimeStr);
            Date endTime = DateUtil.parseDate(endTimeStr);
            LocalDate localDateStart = startTime.toInstant().atZone(ZoneOffset.ofHours(8)).toLocalDate();
            LocalDate localDateEnd = endTime.toInstant().atZone(ZoneOffset.ofHours(8)).toLocalDate();
            // 构建查询参数
            String whereClause = buildWhereClauseForAggregate(dataEntity, params);
            // 查询数据
            List<String> dateRange;
            SimpleDateFormat simpleDateFormat = DateUtil.SIMPLE_DATE_FORMAT_01;
            Set<String> groupKeySet = new TreeSet<>();
            Map<String, Long> groupValueMap = new HashMap<>();
            if (Math.abs(localDateEnd.toEpochDay() - localDateStart.toEpochDay()) < 1) {
                // 如果是一天之内，返回24小时分组的数据
                dateRange = DateUtil.HOUR_LIST;
                List<Map<String, Object>> msgTrendMap = queryEngine.countTypeByHourWithWhereClause(tableName, whereClause);
                msgTrendMap.forEach(map -> {
                    String groupKey = (String) map.get("group_key");
                    int time = (short) map.get("time");
                    String timeString = (time > 9 ? time : "0" + time) + ":00";
                    BigDecimal count = (BigDecimal) map.get("msg_count");
                    groupKeySet.add(groupKey);
                    groupValueMap.put(groupKey + ":" + timeString, count.longValue());
                });
            } else {
                // 小于当月的一个月时间
                dateRange = DateUtil.getDateRangeWithFormat01(startTime, endTime);
                List<Map<String, Object>> msgTrendMap = queryEngine.countTypeByDayWithWhereClause(tableName, whereClause);
                msgTrendMap.forEach(map -> {
                    String groupKey = (String) map.get("group_key");
                    String time = simpleDateFormat.format((Date) map.get("time"));
                    BigDecimal count = (BigDecimal) map.get("msg_count");
                    groupKeySet.add(groupKey);
                    groupValueMap.put(groupKey + ":" + time, count.longValue());
                });
            }


            List<List<Long>> yAxisList = new ArrayList<>();
            for (String groupKey : groupKeySet) {
                List<Long> currentKeyValueList = new ArrayList<>();
                for (String time : dateRange) {
                    currentKeyValueList.add(groupValueMap.getOrDefault(groupKey + ":" + time, 0L));
                }
                yAxisList.add(currentKeyValueList);
            }
            return StackedLineChartVo
                    .builder()
                    .yAxisName(groupKeySet)
                    .yAxisData(yAxisList)
                    .xAxis(dateRange)
                    .build();
        }
        return null;
    }

    private String buildWhereClauseForAggregate(DataEntity dataEntity, Map<String, String> params) {
        // 从参数中获取必需的值,剔除必要参数
        String active = params.remove("active");
        String startTimeStr = params.remove("start_time");
        String endTimeStr = params.remove("end_time");

        // 构建 whereClause，处理 params 中的所有参数
        StringBuilder whereClauseBuilder = new StringBuilder("WHERE 1=1");

        // 遍历所有参数，动态添加到 where 条件中
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 只有当值不为空时才添加到查询条件中
            if (value != null && !value.isEmpty()) {
                DataAttribute dataAttribute = metaDataService.getDataAttributeByName(dataEntity.getName(), key);
                if (dataAttribute != null) {
                    whereClauseBuilder.append(" AND ")
                            .append(escapeSqlField(dataAttribute.getColumnName()))
                            .append(" = '")
                            .append(escapeSqlValue(value))
                            .append("'");
                }
            }
        }
        whereClauseBuilder.append(" AND ").append(" server_time > '").append(escapeSqlValue(startTimeStr)).append("'");
        whereClauseBuilder.append(" AND ").append(" server_time< '").append(escapeSqlValue(endTimeStr)).append("'");
        return whereClauseBuilder.toString();
    }

    // 验证字段名是否合法（只允许字母、数字、下划线）
    private boolean isValidFieldName(String fieldName) {
        return fieldName != null && fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    // 转义 SQL 字段名
    private String escapeSqlField(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        // 验证字段名后再使用
        if (isValidFieldName(fieldName)) {
            return fieldName;
        } else {
            throw new IllegalArgumentException("Invalid field name: " + fieldName);
        }
    }

    // 转义 SQL 值，防止 SQL 注入
    private String escapeSqlValue(String value) {
        if (value == null) {
            return "NULL";
        }
        // 转义单引号
        return value.replace("'", "''");
    }
}
