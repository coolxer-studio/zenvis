package com.coolxer.plugin.operation.repository;

import com.coolxer.plugin.operation.model.OperationBoardDto;
import com.coolxer.plugin.operation.model.OperationBoardRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Component
public class OperationBoardRepository {

    private static final String TABLE = "t_operation_board";
    private static final String COLUMNS =
            "id, last_board, next_board, policy, event, metrics, conditions, icon, title, view";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OperationBoardRepository(
            @Qualifier("pluginMysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long insert(OperationBoardDto dto, Long nextBoard, String icon) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lastBoard", positive(dto.getLastBoard()))
                .addValue("nextBoard", nextBoard)
                .addValue("policy", dto.getPolicy())
                .addValue("event", dto.getEvent())
                .addValue("metrics", dto.getMetrics())
                .addValue("conditions", conditions(dto.getConditions()))
                .addValue("icon", icon)
                .addValue("title", dto.getPanelTitle())
                .addValue("view", dto.getPanelView());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO t_operation_board
                    (last_board, next_board, policy, event, metrics, conditions, icon, title, view,
                     create_time, update_time, is_delete)
                VALUES
                    (:lastBoard, :nextBoard, :policy, :event, :metrics, :conditions, :icon, :title, :view,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public Optional<OperationBoardRecord> findById(long id) {
        List<OperationBoardRecord> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow
        );
        return rows.stream().findFirst();
    }

    public List<OperationBoardRecord> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " ORDER BY id",
                new MapSqlParameterSource(),
                this::mapRow
        );
    }

    public void updateNext(long id, Long nextBoard) {
        jdbcTemplate.update(
                "UPDATE " + TABLE + " SET next_board = :nextBoard, update_time = CURRENT_TIMESTAMP WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("nextBoard", nextBoard)
        );
    }

    public void updateLast(long id, Long lastBoard) {
        jdbcTemplate.update(
                "UPDATE " + TABLE + " SET last_board = :lastBoard, update_time = CURRENT_TIMESTAMP WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("lastBoard", lastBoard)
        );
    }

    public void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    private OperationBoardRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OperationBoardRecord(
                rs.getLong("id"),
                nullableLong(rs, "last_board"),
                nullableLong(rs, "next_board"),
                rs.getString("policy"),
                rs.getString("event"),
                rs.getString("metrics"),
                rs.getString("conditions"),
                rs.getString("icon"),
                rs.getString("title"),
                rs.getString("view")
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private String conditions(Object value) {
        if (value == null || value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("运营看板条件不是有效 JSON", e);
        }
    }
}
