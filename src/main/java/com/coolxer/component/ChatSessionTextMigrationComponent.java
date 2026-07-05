package com.coolxer.component;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 扩容 DIH 会话 JSON 字段，避免长配置或模型回复超过 MySQL TEXT 上限。
 */
@Slf4j
@Component
@Order(0)
public class ChatSessionTextMigrationComponent implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        modifyColumnToLongText("messages");
        modifyColumnToLongText("extra_data");
    }

    private void modifyColumnToLongText(String columnName) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + quoteIdentifier(MysqlFinalTableName.T_AI_CHAT_SESSION)
                    + " MODIFY COLUMN " + quoteIdentifier(columnName) + " LONGTEXT");
        } catch (Exception e) {
            log.debug("AI 会话 {} 列可能已是 LONGTEXT 或会话表尚未创建，跳过列类型调整", columnName, e);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
