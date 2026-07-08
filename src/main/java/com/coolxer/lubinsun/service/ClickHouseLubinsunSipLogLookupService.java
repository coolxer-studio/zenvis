package com.coolxer.lubinsun.service;

import com.coolxer.lubinsun.config.LubinsunPlatformProperties;
import com.coolxer.lubinsun.model.LubinsunSipLogLookupResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ClickHouseLubinsunSipLogLookupService implements LubinsunSipLogLookupService {

    private static final String SECURITY_EVENT_TABLE = "zenvis.sangfor_sip_security_event";
    private static final String SECURITY_ALARM_TABLE = "zenvis.sangfor_sip_security_alarm";

    private final JdbcTemplate jdbcTemplate;
    private final LubinsunPlatformProperties properties;

    public ClickHouseLubinsunSipLogLookupService(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource,
                                                 LubinsunPlatformProperties properties) {
        this.jdbcTemplate = new JdbcTemplate(clickHouseDataSource);
        this.properties = properties;
    }

    @Override
    public LubinsunSipLogLookupResult lookup(String ipExpression) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> events = new ArrayList<>();
        List<Map<String, Object>> alarms = new ArrayList<>();
        for (String ip : splitIps(ipExpression)) {
            mergeRows(events, queryByIp(
                    SECURITY_EVENT_TABLE,
                    "log_time",
                    ip,
                    errors
            ));
            mergeRows(alarms, queryByIp(
                    SECURITY_ALARM_TABLE,
                    "last_time",
                    ip,
                    errors
            ));
        }
        return new LubinsunSipLogLookupResult(events, alarms, errors);
    }

    private void mergeRows(List<Map<String, Object>> target, List<Map<String, Object>> source) {
        for (Map<String, Object> row : source) {
            if (!target.contains(row)) {
                target.add(row);
            }
        }
    }

    private List<Map<String, Object>> queryByIp(String tableName, String sortColumn, String ip, List<String> errors) {
        int limit = Math.max(1, Math.min(properties.getSipLogLimit(), 1000));
        String sql = "select * from " + tableName + " where " + ipWhereClause(tableName)
                + " order by " + sortColumn + " desc limit " + limit;
        try {
            return jdbcTemplate.queryForList(sql, ip, ip, ip).stream()
                    .map(this::normalizeRow)
                    .toList();
        } catch (DataAccessException e) {
            String message = tableName + " 查询失败: " + e.getMostSpecificCause().getMessage();
            log.warn("Lubinsun SIP 日志查询失败, table: {}, ip: {}", tableName, ip, e);
            errors.add(message);
            return List.of();
        }
    }

    private String ipWhereClause(String tableName) {
        if (SECURITY_ALARM_TABLE.equals(tableName)) {
            return "(attack_ip = ? or suffer_ip = ? or has(x_forwarded_for, ?))";
        }
        return "(ip = ? or src_ip = ? or dst_ip = ?)";
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return value;
    }

    private List<String> splitIps(String ipExpression) {
        if (ipExpression == null || ipExpression.isBlank()) {
            return List.of();
        }
        Set<String> ips = new LinkedHashSet<>();
        for (String part : ipExpression.split(",")) {
            String ip = part == null ? null : part.trim();
            if (ip != null && !ip.isBlank()) {
                ips.add(ip);
            }
        }
        return List.copyOf(ips);
    }
}
