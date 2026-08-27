package com.coolxer.service.retrieval;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EntityCsvService {

    public static final int MAX_IMPORT_BYTES = 5 * 1024 * 1024;
    public static final int MAX_IMPORT_ROWS = 10_000;
    public static final int MAX_EXPORT_ROWS = 50_000;
    private static final int EXPORT_PAGE_SIZE = 100;
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final DateTimeFormatter DATE_TIME_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_TIME_SECONDS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private final MetaDataService metaDataService;
    private final EntityCoreService entityCoreService;
    private final ObjectMapper objectMapper;

    public EntityCsvService(
            MetaDataService metaDataService,
            EntityCoreService entityCoreService,
            ObjectMapper objectMapper) {
        this.metaDataService = metaDataService;
        this.entityCoreService = entityCoreService;
        this.objectMapper = objectMapper;
    }

    public ImportResult importCsv(String entityName, MultipartFile file) throws IOException {
        DataEntity entity = requireEntity(entityName);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空 CSV 文件");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("CSV 文件不能超过 5 MiB");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("仅支持 .csv 文件");
        }

        List<DataAttribute> attributes = businessAttributes(entity);
        List<Map<String, Object>> records = parseAndValidate(file.getBytes(), attributes);
        for (Map<String, Object> record : records) {
            entityCoreService.add(entityName, record);
        }
        return new ImportResult(records.size(), filename == null ? entityName + ".csv" : filename);
    }

    public ExportResult exportCsv(String entityName, Map<String, Object> requestParams) {
        DataEntity entity = requireEntity(entityName);
        List<DataAttribute> attributes = businessAttributes(entity);
        Map<String, Object> baseParams = new HashMap<>(requestParams == null ? Map.of() : requestParams);
        baseParams.remove("page");
        baseParams.remove("perPage");
        baseParams.remove("per_page");

        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0;
        for (int page = 1; rows.size() < MAX_EXPORT_ROWS; page++) {
            Map<String, Object> pageParams = new HashMap<>(baseParams);
            pageParams.put("page", page);
            pageParams.put("perPage", EXPORT_PAGE_SIZE);
            PageRowsVo<Map<String, Object>> result = entityCoreService.getPageList(entityName, pageParams);
            if (result == null) {
                throw new IllegalArgumentException("实体不存在: " + entityName);
            }
            total = result.getTotal();
            if (result.getRows() == null || result.getRows().isEmpty()) {
                break;
            }
            int remaining = MAX_EXPORT_ROWS - rows.size();
            rows.addAll(result.getRows().subList(0, Math.min(remaining, result.getRows().size())));
            if (rows.size() >= total || result.getRows().size() < EXPORT_PAGE_SIZE) {
                break;
            }
        }
        return new ExportResult(encodeCsv(attributes, rows), rows.size(), total > rows.size());
    }

    public byte[] importTemplate(String entityName) {
        DataEntity entity = requireEntity(entityName);
        return encodeCsv(businessAttributes(entity), List.of());
    }

    List<Map<String, Object>> parseAndValidate(byte[] bytes, List<DataAttribute> attributes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 文件缺少表头");
        }
        if (rows.size() - 1 > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("CSV 数据行不能超过 10000 行");
        }

        List<DataAttribute> headers = resolveHeaders(rows.get(0), attributes);
        List<Map<String, Object>> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (row.size() > headers.size()) {
                throw rowError(rowIndex + 1, "字段数超过表头");
            }
            Map<String, Object> record = new LinkedHashMap<>();
            for (int column = 0; column < row.size(); column++) {
                String value = row.get(column).trim();
                if (!value.isEmpty()) {
                    DataAttribute attribute = headers.get(column);
                    validateValue(attribute, value, rowIndex + 1);
                    record.put(attribute.getName(), normalizeCandidateValue(attribute, value));
                }
            }
            validateRequired(attributes, record, rowIndex + 1);
            records.add(record);
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV 文件没有可导入的数据行");
        }
        return records;
    }

    private Object normalizeCandidateValue(DataAttribute attribute, String value) {
        if (!attribute.isMustCandidate() || attribute.getMapping() == null) {
            return value;
        }
        return attribute.getMapping().values().stream()
                .filter(candidate -> Objects.toString(candidate, "").equals(value))
                .findFirst()
                .orElse(value);
    }

    private DataEntity requireEntity(String entityName) {
        if (StringUtils.isBlank(entityName)) {
            throw new IllegalArgumentException("实体名称不能为空");
        }
        DataEntity entity = metaDataService.getDataEntityByName(entityName);
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityName);
        }
        return entity;
    }

    private List<DataAttribute> businessAttributes(DataEntity entity) {
        List<DataAttribute> attributes = metaDataService.getAllDataAttributeByEntity(entity).stream()
                .filter(attribute -> !MetaDataConstants.isSystemMaintained(attribute))
                .toList();
        if (attributes.isEmpty()) {
            throw new IllegalArgumentException("实体没有可导入导出的业务字段: " + entity.getName());
        }
        return attributes;
    }

    private List<DataAttribute> resolveHeaders(
            List<String> headers,
            List<DataAttribute> attributes) {
        Map<String, DataAttribute> aliases = new HashMap<>();
        Set<String> ambiguousLabels = new HashSet<>();
        for (DataAttribute attribute : attributes) {
            aliases.put(attribute.getName(), attribute);
            if (StringUtils.isNotBlank(attribute.getLabel())) {
                DataAttribute previous = aliases.putIfAbsent(attribute.getLabel(), attribute);
                if (previous != null && previous != attribute) {
                    ambiguousLabels.add(attribute.getLabel());
                }
            }
        }
        ambiguousLabels.forEach(aliases::remove);

        List<DataAttribute> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawHeader : headers) {
            String header = rawHeader.trim();
            if (ambiguousLabels.contains(header)) {
                throw new IllegalArgumentException("CSV 中文表头在实体中不唯一，请使用字段 name: " + header);
            }
            DataAttribute attribute = aliases.get(header);
            if (attribute == null) {
                throw new IllegalArgumentException("CSV 包含未知或系统维护表头: " + header);
            }
            if (!seen.add(attribute.getName())) {
                throw new IllegalArgumentException("CSV 表头重复: " + header);
            }
            resolved.add(attribute);
        }
        validateRequiredHeaders(attributes, seen);
        return resolved;
    }

    private void validateRequiredHeaders(List<DataAttribute> attributes, Set<String> present) {
        for (DataAttribute attribute : attributes) {
            if (attribute.isRequired() && !present.contains(attribute.getName())) {
                throw new IllegalArgumentException("CSV 缺少必填表头: " + attribute.getName());
            }
        }
    }

    private void validateRequired(
            List<DataAttribute> attributes,
            Map<String, Object> record,
            int rowNumber) {
        for (DataAttribute attribute : attributes) {
            Object value = record.get(attribute.getName());
            if (attribute.isRequired() && (value == null || value.toString().isBlank())) {
                throw rowError(rowNumber, "缺少必填字段 " + attribute.getName());
            }
        }
    }

    private void validateValue(DataAttribute attribute, String value, int rowNumber) {
        if (attribute.isMustCandidate()) {
            Map<String, Object> mapping = attribute.getMapping();
            boolean supported = mapping != null && mapping.values().stream()
                    .map(item -> Objects.toString(item, ""))
                    .anyMatch(value::equals);
            if (!supported) {
                throw rowError(rowNumber, attribute.getName() + " 不在 Meta mapping 候选值中");
            }
        }
        String type = StringUtils.defaultString(attribute.getColumnType());
        try {
            if (type.matches("U?Int(8|16|32|64|128|256)")) {
                if (!INTEGER.matcher(value).matches()) {
                    throw new NumberFormatException();
                }
                BigInteger integer = new BigInteger(value);
                if (type.startsWith("UInt") && integer.signum() < 0) {
                    throw new NumberFormatException();
                }
            } else if (type.startsWith("Float") || type.startsWith("Decimal")) {
                new BigDecimal(value);
            } else if ("Bool".equals(type)
                    && !Set.of("true", "false", "0", "1").contains(value.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException();
            } else if (type.startsWith("DateTime")) {
                validateDateTime(value);
            } else if ("json".equalsIgnoreCase(type)) {
                objectMapper.readTree(value);
            }
        } catch (Exception exception) {
            throw rowError(rowNumber, attribute.getName() + " 不符合 " + type + " 类型");
        }
    }

    private void validateDateTime(String value) {
        try {
            LocalDateTime.parse(value, DATE_TIME_MILLIS);
            return;
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime.parse(value, DATE_TIME_SECONDS);
                return;
            } catch (DateTimeParseException ignoredAgain) {
                OffsetDateTime.parse(value);
            }
        }
    }

    private byte[] encodeCsv(List<DataAttribute> attributes, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder("\ufeff");
        csv.append(attributes.stream().map(DataAttribute::getName).collect(java.util.stream.Collectors.joining(",")))
                .append("\r\n");
        for (Map<String, Object> row : rows) {
            for (int index = 0; index < attributes.size(); index++) {
                if (index > 0) {
                    csv.append(',');
                }
                DataAttribute attribute = attributes.get(index);
                Object value = row.get(attribute.getName());
                if (value == null && StringUtils.isNotBlank(attribute.getDisplayName())) {
                    value = row.get(attribute.getDisplayName());
                }
                if (value == null) {
                    value = row.get(attribute.getColumnName());
                }
                csv.append(csvValue(value));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvValue(Object value) {
        String safe = value == null ? "" : value.toString();
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(current);
                }
            } else if (current == '"' && value.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(value.toString());
                value.setLength(0);
            } else if (current == '\n' || current == '\r') {
                row.add(value.toString());
                value.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (current == '\r' && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 存在未闭合的双引号");
        }
        if (value.length() > 0 || !row.isEmpty()) {
            row.add(value.toString());
            rows.add(row);
        }
        return rows;
    }

    private IllegalArgumentException rowError(int rowNumber, String message) {
        return new IllegalArgumentException("CSV 第 " + rowNumber + " 行: " + message);
    }

    public record ImportResult(int imported, String filename) {
    }

    public record ExportResult(byte[] content, int exported, boolean truncated) {
    }
}
