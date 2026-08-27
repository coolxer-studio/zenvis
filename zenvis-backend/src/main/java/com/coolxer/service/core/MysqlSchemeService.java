package com.coolxer.service.core;

/**
 * mysql数据库操作服务
 */
public interface MysqlSchemeService {
    /**
     * 初始化scheme
     */
    void initScheme(String initSqlPath);
}