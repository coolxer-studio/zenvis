package com.coolxer.model.retrieval.query;

/**
 * 由 Meta 校验并解析后的 IP 安全事件时间轴查询源。
 */
public record IpEventTimelineQuerySource(
        String entity,
        String tableName,
        String sourceField,
        String targetField,
        String timeField,
        String eventTypeField,
        String sourceColumn,
        String targetColumn,
        String timeColumn,
        String eventTypeColumn,
        String sourceColumnType,
        String targetColumnType,
        String timeColumnType,
        String eventTypeColumnType,
        int eventTypeStart,
        int eventTypeLength) {
}
