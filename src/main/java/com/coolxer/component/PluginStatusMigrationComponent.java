package com.coolxer.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 兼容早期按 enum ordinal 存储的插件状态。
 */
@Slf4j
@Component
@Order(0)
public class PluginStatusMigrationComponent implements CommandLineRunner {

    private static final String TABLE_NAME = "t_sys_plugin";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " MODIFY COLUMN status VARCHAR(32)");
        } catch (Exception e) {
            log.debug("插件状态列可能已是字符串类型或插件表尚未创建，跳过列类型调整", e);
        }
        normalizeLegacyStatusValues();
    }

    private void normalizeLegacyStatusValues() {
        try {
            jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET status = 'UN_INSTALL' WHERE status IS NULL OR status = '' OR status = '0'");
            jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET status = 'INSTALLED' WHERE status = '1'");
            jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET status = 'UN_INSTALL' WHERE status = 'UNLOAD' OR status = 'UNINSTALLED'");
        } catch (Exception e) {
            log.debug("插件状态历史值迁移跳过", e);
        }
    }
}
