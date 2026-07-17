package com.coolxer.service.system;

import java.nio.file.Path;
import java.util.List;

/**
 * 插件数据库迁移服务。
 */
public interface PluginMigrationService {

    /**
     * 按版本顺序执行插件尚未应用的 MySQL 迁移。
     *
     * @param packageName 插件包名
     * @param migrations 迁移脚本
     */
    void migrateMysql(String packageName, List<Path> migrations);
}
