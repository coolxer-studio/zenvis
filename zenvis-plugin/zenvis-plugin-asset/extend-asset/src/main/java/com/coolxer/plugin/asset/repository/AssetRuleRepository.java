package com.coolxer.plugin.asset.repository;

import com.coolxer.plugin.asset.model.AssetRuleDto;
import com.coolxer.plugin.asset.model.AssetRuleRecord;
import com.coolxer.plugin.asset.model.AssetRuleSearchQuery;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AssetRuleRepository {

    private static final String TABLE = "t_asset_rule";
    private static final String COLUMNS =
            "id, name, description, asset, conditions, action, status, result, create_time, update_time";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AssetRuleRepository(
            @Qualifier("pluginMysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long insert(AssetRuleDto dto, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", dto.getName())
                .addValue("description", dto.getDescription())
                .addValue("asset", enumName(dto.getAsset()))
                .addValue("conditions", conditions(dto.getConditions()))
                .addValue("action", enumName(dto.getAction()))
                .addValue("status", status)
                .addValue("result", dto.getResult());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO t_asset_rule
                    (name, description, asset, conditions, action, status, result,
                     create_time, update_time, is_delete)
                VALUES
                    (:name, :description, :asset, :conditions, :action, :status, :result,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public boolean update(long id, AssetRuleDto dto) {
        List<String> assignments = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        add(assignments, params, "name", dto.getName());
        add(assignments, params, "description", dto.getDescription());
        add(assignments, params, "asset", enumName(dto.getAsset()));
        if (dto.getConditions() != null) {
            add(assignments, params, "conditions", conditions(dto.getConditions()));
        }
        add(assignments, params, "action", enumName(dto.getAction()));
        add(assignments, params, "status", enumName(dto.getStatus()));
        add(assignments, params, "result", dto.getResult());
        if (assignments.isEmpty()) {
            return findById(id).isPresent();
        }
        assignments.add("update_time = CURRENT_TIMESTAMP");
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET " + String.join(", ", assignments) + " WHERE id = :id",
                params
        ) > 0;
    }

    public void deleteById(long id) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public void deleteAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids));
    }

    public Optional<AssetRuleRecord> findById(long id) {
        List<AssetRuleRecord> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow
        );
        return rows.stream().findFirst();
    }

    public List<AssetRuleRecord> findPage(AssetRuleSearchQuery query) {
        QueryParts parts = queryParts(query);
        int page = Math.max(query.getPage(), 1);
        int perPage = Math.min(Math.max(query.getPerPage(), 1), 200);
        parts.params()
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + parts.where()
                        + " ORDER BY update_time DESC LIMIT :limit OFFSET :offset",
                parts.params(),
                this::mapRow
        );
    }

    public long count(AssetRuleSearchQuery query) {
        QueryParts parts = queryParts(query);
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + parts.where(),
                parts.params(),
                Long.class
        );
        return result == null ? 0L : result;
    }

    private QueryParts queryParts(AssetRuleSearchQuery query) {
        List<String> filters = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (query.getAsset() != null) {
            filters.add("asset = :asset");
            params.addValue("asset", query.getAsset().name());
        }
        if (query.getAction() != null) {
            filters.add("action = :action");
            params.addValue("action", query.getAction().name());
        }
        if (query.getStatus() != null) {
            filters.add("status = :status");
            params.addValue("status", query.getStatus().name());
        }
        return new QueryParts(filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters), params);
    }

    private AssetRuleRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AssetRuleRecord(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("asset"),
                rs.getString("conditions"),
                rs.getString("action"),
                rs.getString("status"),
                rs.getString("result"),
                rs.getTimestamp("create_time"),
                rs.getTimestamp("update_time")
        );
    }

    private void add(List<String> assignments, MapSqlParameterSource params, String column, Object value) {
        if (value != null) {
            assignments.add(column + " = :" + column);
            params.addValue(column, value);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String conditions(Object value) {
        if (value == null || value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("资产规则条件不是有效 JSON", e);
        }
    }

    private record QueryParts(String where, MapSqlParameterSource params) {
    }
}
