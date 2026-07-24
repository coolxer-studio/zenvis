package com.coolxer.model.retrieval.query;

import java.util.List;

/**
 * IP 关系聚合查询。实体和字段均使用 Meta 中的逻辑名称。
 */
public record IpRelationQueryRequest(
        String ip,
        String startTime,
        String endTime,
        Integer limit,
        List<String> entities,
        List<RelationMapping> relationMappings) {

    public record RelationMapping(
            String entity,
            String sourceField,
            String targetField,
            String timeField) {
    }
}
