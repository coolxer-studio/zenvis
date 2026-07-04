package com.coolxer.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 兼容早期按 enum ordinal 存储的插件状态。
 */
@Slf4j
@Component
@Order(0)
public class PluginStatusMigrationComponent implements CommandLineRunner {

    private static final String PLUGIN_TABLE_NAME = "t_sys_plugin";
    private static final String DASHBOARD_TABLE_NAME = "t_sys_dashboard";
    private static final String MCP_TABLE_NAME = "t_ai_mcp_server";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        dropStatusCheckConstraints();
        try {
            jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " MODIFY COLUMN status VARCHAR(32)");
        } catch (Exception e) {
            log.debug("插件状态列可能已是字符串类型或插件表尚未创建，跳过列类型调整", e);
        }
        normalizeLegacyStatusValues();
        ensureSourceColumn(DASHBOARD_TABLE_NAME);
        ensureSourceColumn(MCP_TABLE_NAME);
    }

    private void normalizeLegacyStatusValues() {
        try {
            jdbcTemplate.update("UPDATE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " SET status = 'UN_INSTALL' WHERE status IS NULL OR status = '' OR status = '0'");
            jdbcTemplate.update("UPDATE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " SET status = 'INSTALLED' WHERE status = '1'");
            jdbcTemplate.update("UPDATE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " SET status = 'UN_INSTALL' WHERE status = 'UNLOAD' OR status = 'UNINSTALLED'");
        } catch (Exception e) {
            log.debug("插件状态历史值迁移跳过", e);
        }
    }

    private void dropStatusCheckConstraints() {
        List<String> constraintNames = findStatusCheckConstraints();
        if (constraintNames.isEmpty()) {
            constraintNames = Collections.singletonList("t_sys_plugin_chk_1");
        }
        for (String constraintName : constraintNames) {
            dropCheckConstraint(constraintName);
        }
    }

    private List<String> findStatusCheckConstraints() {
        try {
            return jdbcTemplate.queryForList("""
                    SELECT tc.CONSTRAINT_NAME
                    FROM information_schema.TABLE_CONSTRAINTS tc
                    JOIN information_schema.CHECK_CONSTRAINTS cc
                      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                     AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
                      AND tc.TABLE_NAME = ?
                      AND tc.CONSTRAINT_TYPE = 'CHECK'
                      AND (cc.CHECK_CLAUSE LIKE '%status%' OR tc.CONSTRAINT_NAME = 't_sys_plugin_chk_1')
                    """, String.class, PLUGIN_TABLE_NAME);
        } catch (Exception e) {
            log.debug("查询插件状态 CHECK 约束失败，使用已知约束名兜底", e);
            return Collections.emptyList();
        }
    }

    private void dropCheckConstraint(String constraintName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " DROP CHECK " + quoteIdentifier(constraintName));
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(PLUGIN_TABLE_NAME) + " DROP CONSTRAINT " + quoteIdentifier(constraintName));
            } catch (Exception ignored) {
                log.debug("插件状态 CHECK 约束不存在或当前数据库不支持删除: {}", constraintName, e);
            }
        }
    }

    private void ensureSourceColumn(String tableName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN source VARCHAR(256) DEFAULT 'default'");
        } catch (Exception e) {
            log.debug("{} source 列可能已存在或表尚未创建，跳过列添加", tableName, e);
        }
        try {
            jdbcTemplate.update("UPDATE " + quoteIdentifier(tableName) + " SET source = 'default' WHERE source IS NULL OR source = ''");
        } catch (Exception e) {
            log.debug("{} source 历史值迁移跳过", tableName, e);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
