package com.coolxer.service.system;

import java.nio.file.Path;
import java.util.List;

/**
 * 插件数据库迁移服务。
 */
public interface PluginMigrationService {

    /**
     * 预解析迁移并校验已执行历史，不执行任何插件迁移脚本。
     */
    void validateMysql(String packageName, List<Path> migrations);

    /**
     * 按版本顺序执行插件尚未应用的 MySQL 迁移。
     *
     * @param packageName 插件包名
     * @param migrations 迁移脚本
     */
    void migrateMysql(String packageName, List<Path> migrations);
}
