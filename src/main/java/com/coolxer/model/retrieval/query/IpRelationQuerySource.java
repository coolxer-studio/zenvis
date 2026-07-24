package com.coolxer.model.retrieval.query;

/**
 * 由 Meta 校验并解析后的 IP 关系查询源。
 */
public record IpRelationQuerySource(
        String entity,
        String entityLabel,
        String tableName,
        String sourceField,
        String targetField,
        String timeField,
        String sourceColumn,
        String targetColumn,
        String timeColumn,
        String timeColumnType) {
}
