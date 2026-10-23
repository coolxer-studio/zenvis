package com.coolxer.component;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 报表修订/归档正文已迁移到 MinIO，旧库中的 content 列需要允许 NULL。
 */
@Slf4j
@Component
public class ReportSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ReportSchemaInitializer(@Qualifier("mysqlDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void ensureObjectStorageSchema() {
        makeContentNullable(MysqlFinalTableName.T_AI_REPORT_REVISION);
        makeContentNullable(MysqlFinalTableName.T_AI_REPORT_ARTIFACT);
    }

    private void makeContentNullable(String tableName) {
        List<String> nullable = jdbcTemplate.query("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'content'
                """, (rs, rowNum) -> rs.getString(1), tableName);
        if (nullable.isEmpty() || "YES".equalsIgnoreCase(nullable.get(0))) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN content LONGTEXT NULL");
        log.info("报表表 {} 的 content 列已允许 NULL", tableName);
    }
}
