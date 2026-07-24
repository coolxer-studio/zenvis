package com.coolxer.model.retrieval.query;

import java.util.List;

/**
 * IP 安全事件时间轴查询。实体和字段均使用 Meta 中的逻辑名称。
 */
public record IpEventTimelineQueryRequest(
        String ip,
        String startTime,
        String endTime,
        List<EventMapping> eventMappings) {

    public record EventMapping(
            String entity,
            String sourceField,
            String targetField,
            String timeField,
            String eventTypeField,
            Integer eventTypeStart,
            Integer eventTypeLength) {
    }
}
