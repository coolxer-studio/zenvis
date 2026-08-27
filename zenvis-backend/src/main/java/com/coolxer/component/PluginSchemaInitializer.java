package com.coolxer.component;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.Plugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 修复 Hibernate 无法自动更新的插件状态 CHECK 约束。
 */
@Slf4j
@Component
public class PluginSchemaInitializer {

    private static final String CONSTRAINT_NAME = "t_sys_plugin_status_chk";
    private final JdbcTemplate jdbcTemplate;

    public PluginSchemaInitializer(@Qualifier("mysqlDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void ensureUpgradeSchema() {
        List<String> clauses = jdbcTemplate.query("""
                SELECT cc.CHECK_CLAUSE
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.CHECK_CONSTRAINTS cc
                  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
                  AND tc.TABLE_NAME = ?
                  AND tc.CONSTRAINT_NAME = ?
                """, (rs, rowNum) -> rs.getString(1), MysqlFinalTableName.T_SYS_PLUGIN, CONSTRAINT_NAME);
        if (clauses.stream().anyMatch(clause -> clause != null
                && clause.contains("UPGRADING") && clause.contains("UPGRADE_FAILED"))) {
            return;
        }
        if (!clauses.isEmpty()) {
            jdbcTemplate.execute("ALTER TABLE " + MysqlFinalTableName.T_SYS_PLUGIN
                    + " DROP CHECK " + CONSTRAINT_NAME);
        }
        jdbcTemplate.execute("ALTER TABLE " + MysqlFinalTableName.T_SYS_PLUGIN
                + " ADD CONSTRAINT " + CONSTRAINT_NAME + " CHECK (" + Plugin.STATUS_CHECK_CONSTRAINT + ")");
        log.info("插件状态约束已包含升级状态");
    }
}
