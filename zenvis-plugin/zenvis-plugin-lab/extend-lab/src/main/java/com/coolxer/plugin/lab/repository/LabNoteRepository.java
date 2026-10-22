package com.coolxer.plugin.lab.repository;

import com.coolxer.plugin.lab.api.LabApiException;
import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LabNoteRepository {

    private static final String COLUMNS = "id, title, content, status, owner, create_time, update_time";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LabNoteRepository(
            @Qualifier("pluginMysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageRows<LabNote> search(int page, int perPage, String keyword, String status) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("keyword", keyword)
                    .addValue("status", status)
                    .addValue("limit", perPage)
                    .addValue("offset", (page - 1L) * perPage);
            String where = whereClause();
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_lab_note" + where,
                    params,
                    Long.class);
            List<LabNote> rows = jdbcTemplate.query(
                    "SELECT " + COLUMNS + " FROM t_lab_note" + where
                            + " ORDER BY update_time DESC, id DESC LIMIT :limit OFFSET :offset",
                    params,
                    (rs, rowNum) -> map(rs));
            return new PageRows<>(rows, total == null ? 0L : total);
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    public LabNote findById(long id) {
        try {
            List<LabNote> rows = jdbcTemplate.query(
                    "SELECT " + COLUMNS + " FROM t_lab_note WHERE id = :id",
                    new MapSqlParameterSource("id", id),
                    (rs, rowNum) -> map(rs));
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    public LabNote insert(String title, String content, String status, String owner) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.getJdbcTemplate().update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO t_lab_note (title, content, status, owner) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, title);
                statement.setString(2, content);
                statement.setString(3, status);
                statement.setString(4, owner);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw LabApiException.unavailable("笔记创建后未返回主键");
            }
            return findById(key.longValue());
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    public LabNote update(long id, String title, String content, String status, String owner) {
        try {
            int affected = jdbcTemplate.update(
                    "UPDATE t_lab_note SET title = :title, content = :content, status = :status, "
                            + "owner = :owner, update_time = CURRENT_TIMESTAMP WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("title", title)
                            .addValue("content", content)
                            .addValue("status", status)
                            .addValue("owner", owner));
            return affected == 0 ? null : findById(id);
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    public boolean delete(long id) {
        try {
            return jdbcTemplate.update(
                    "DELETE FROM t_lab_note WHERE id = :id",
                    new MapSqlParameterSource("id", id)) > 0;
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    public LabNoteStats stats() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT status, COUNT(*) AS amount FROM t_lab_note GROUP BY status",
                    new MapSqlParameterSource());
            Map<String, Long> byStatus = new LinkedHashMap<>();
            long total = 0L;
            for (Map<String, Object> row : rows) {
                long amount = number(row.get("amount"));
                byStatus.put(String.valueOf(row.get("status")), amount);
                total += amount;
            }
            return new LabNoteStats(total, byStatus);
        } catch (DataAccessException exception) {
            throw LabApiException.unavailable("笔记数据库当前不可用");
        }
    }

    private String whereClause() {
        return " WHERE (:keyword IS NULL OR title LIKE :keyword ESCAPE '!' OR content LIKE :keyword ESCAPE '!')"
                + " AND (:status IS NULL OR status = :status)";
    }

    private LabNote map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new LabNote(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("status"),
                resultSet.getString("owner"),
                localDateTime(resultSet.getTimestamp("create_time")),
                localDateTime(resultSet.getTimestamp("update_time")));
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
