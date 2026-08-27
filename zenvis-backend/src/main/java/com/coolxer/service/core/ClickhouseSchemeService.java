package com.coolxer.service.core;

import com.coolxer.model.retrieval.meta.MetaData;

/**
 * clickhouse数据库操作服务
 */
public interface ClickhouseSchemeService {
    /**
     * 初始化scheme
     */
    public void initScheme(String initSqlPath);

    /**
     * 通过MetaData数据初始化
     *
     * @param metaData
     */
    public void loadSchemeFromMetaData(MetaData metaData);

    /**
     * 创建缺失表和字段，并将表级 TTL 与 Meta 同步。任一语句失败时抛出异常。
     */
    void applyAdditiveScheme(MetaData metaData);

    /**
     * 将已存在 ClickHouse 表的 TTL 与 Meta 定义同步，不创建表或修改字段。
     */
    void synchronizeTableTtl(MetaData metaData);

    /**
     * 删除表
     *
     * @param tableName
     */
    void deleteTable(String tableName);
}
