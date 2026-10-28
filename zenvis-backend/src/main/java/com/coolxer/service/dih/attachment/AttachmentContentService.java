package com.coolxer.service.dih.attachment;

import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 附件结构化读取、片段回查和表格条件查询。
 */
@Slf4j
@Service
public class AttachmentContentService {

    private static final Pattern ROW_RANGE = Pattern.compile("(?i)(?:rows?:)?(\\d+)\\s*[-:]\\s*(\\d+)");
    private static final Pattern SINGLE_ROW = Pattern.compile("(?i)(?:rows?:)?(\\d+)");
    private static final int MAX_QUERY_SCAN_ROWS = 1_000_000;
    private static final int MAX_QUERY_MEMORY_ROWS = 100_000;
    private static final int MAX_QUERY_LIMIT = 500;

    private final AttachmentDerivedStore derivedStore;
    private final AttachmentSearchService searchService;

    public AttachmentContentService(AttachmentDerivedStore derivedStore,
                                    AttachmentSearchService searchService) {
        this.derivedStore = derivedStore;
        this.searchService = searchService;
    }

    public AttachmentManifest manifest(ChatAttachmentRecord record) {
        return derivedStore.readManifest(record);
    }

    public List<AttachmentSearchService.SearchHit> search(List<ChatAttachmentRecord> records,
                                                          String query,
                                                          int topK,
                                                          Integer userId) {
        return searchService.search(records, query, topK, userId);
    }

    public List<AttachmentChunk> firstChunks(ChatAttachmentRecord record, int limit) {
        return derivedStore.readChunks(record, Math.max(1, limit));
    }

    public ReadResult read(ChatAttachmentRecord record, String locator, int maxChars) {
        if (record == null) {
            return ReadResult.empty("附件不存在");
        }
        if (AttachmentStatus.UNSUPPORTED.equals(record.getParseStatus())) {
            return ReadResult.empty("该附件未解析正文，只能查看文件元信息");
        }
        int budget = Math.max(maxChars, 256);
        List<AttachmentChunk> selected = new ArrayList<>();
        String[] nextLocator = {null};
        boolean[] truncated = {false};
        int[] usedChars = {0};
        boolean[] matched = {false};
        Path path = null;
        try {
            path = derivedStore.downloadChunks(record);
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    AttachmentChunk chunk = JacksonUtil.toObject(line, AttachmentChunk.class);
                    if (chunk == null) {
                        continue;
                    }
                    boolean hit = matches(chunk, locator);
                    if (!hit) {
                        if (matched[0]) {
                            nextLocator[0] = chunk.getLocator();
                            break;
                        }
                        continue;
                    }
                    matched[0] = true;
                    String text = chunk.getText() == null ? "" : chunk.getText();
                    int separator = selected.isEmpty() ? 0 : 2;
                    if (usedChars[0] + separator + text.length() > budget) {
                        int remaining = budget - usedChars[0] - separator;
                        if (remaining > 0) {
                            AttachmentChunk partial = copyChunk(chunk);
                            partial.setText(text.substring(0, safePrefixLength(text, remaining)) + "\n[片段已截断]");
                            selected.add(partial);
                        }
                        nextLocator[0] = continuationLocator(chunk);
                        truncated[0] = true;
                        break;
                    }
                    selected.add(chunk);
                    usedChars[0] += separator + text.length();
                }
            }
        } catch (Exception e) {
            log.warn("读取附件范围失败: fileId={}, locator={}, error={}",
                    record.getFileId(), locator, e.getMessage());
            return ReadResult.empty("读取附件范围失败：" + e.getMessage());
        } finally {
            derivedStore.deleteQuietly(path);
        }
        if (selected.isEmpty()) {
            return ReadResult.empty("指定范围内没有可读取内容");
        }
        return new ReadResult(record.getFileId(), record.getFileName(), locator,
                nextLocator[0], selected, truncated[0],
                truncated[0] ? "内容超过单次读取上限，可使用 nextLocator 继续读取" : null);
    }

    public TableQueryResult tableQuery(ChatAttachmentRecord record,
                                       String requestedSheet,
                                       List<FilterSpec> filters,
                                       List<String> select,
                                       String groupBy,
                                       List<AggregateSpec> metrics,
                                       String sortBy,
                                       String sortDirection,
                                       Integer requestedLimit) {
        if (record == null || !isTableFormat(record.getFormat())) {
            return TableQueryResult.empty("该附件不是 CSV/TSV/XLSX 表格，无法执行表格查询");
        }
        int limit = requestedLimit == null ? 100 : Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Map<String, AggregateAccumulator> groups = new LinkedHashMap<>();
        long[] scanned = {0};
        long[] matched = {0};
        boolean[] truncated = {false};
        Path path = null;
        try {
            path = derivedStore.downloadChunks(record);
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || truncated[0]) {
                        continue;
                    }
                    AttachmentChunk chunk = JacksonUtil.toObject(line, AttachmentChunk.class);
                    if (chunk == null || !"table".equalsIgnoreCase(chunk.getKind())
                            || !StringUtils.hasText(chunk.getText())) {
                        continue;
                    }
                    if (StringUtils.hasText(requestedSheet)
                            && !requestedSheet.equalsIgnoreCase(value(chunk.getSheet()))) {
                        continue;
                    }
                    String[] lines = chunk.getText().split("\\R");
                    if (lines.length < 2) {
                        continue;
                    }
                    List<String> chunkColumns = splitRow(lines[0]);
                    if (columns.isEmpty()) {
                        columns.addAll(chunkColumns);
                    }
                    for (int index = 1; index < lines.length && !truncated[0]; index++) {
                        if (scanned[0]++ >= MAX_QUERY_SCAN_ROWS) {
                            truncated[0] = true;
                            break;
                        }
                        Map<String, String> row = rowMap(chunkColumns, splitRow(lines[index]));
                        if (!matchesFilters(row, filters)) {
                            continue;
                        }
                        matched[0]++;
                        if (StringUtils.hasText(groupBy)) {
                            accumulateGroup(groups, row, groupBy, metrics);
                            if (groups.size() > MAX_QUERY_MEMORY_ROWS) {
                                truncated[0] = true;
                            }
                        } else if (sortBy == null || sortBy.isBlank()) {
                            if (rows.size() < limit) {
                                rows.add(project(row, select));
                            } else {
                                truncated[0] = true;
                            }
                        } else if (rows.size() < MAX_QUERY_MEMORY_ROWS) {
                            rows.add(project(row, select));
                        } else {
                            truncated[0] = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("附件表格查询失败: fileId={}, error={}", record.getFileId(), e.getMessage());
            return TableQueryResult.empty("表格查询失败：" + e.getMessage());
        } finally {
            derivedStore.deleteQuietly(path);
        }

        if (StringUtils.hasText(groupBy)) {
            List<Map<String, Object>> groupedRows = groups.values().stream()
                    .map(AggregateAccumulator::result)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            columns = new ArrayList<>(groupedRows.isEmpty()
                    ? List.of(groupBy) : new ArrayList<>(groupedRows.get(0).keySet()));
            sortRows(groupedRows, sortBy, sortDirection);
            if (groupedRows.size() > limit) {
                truncated[0] = true;
            }
            rows = groupedRows.stream().limit(limit).toList();
        } else {
            if (!select.isEmpty()) {
                columns = select.stream().filter(columns::contains).toList();
            }
            sortRows(rows, sortBy, sortDirection);
            if (rows.size() > limit) {
                truncated[0] = true;
                rows = rows.stream().limit(limit).toList();
            }
        }
        return new TableQueryResult(record.getFileId(), record.getFileName(), requestedSheet,
                columns, rows, matched[0], rows.size(), truncated[0],
                truncated[0] ? "结果达到扫描、内存或返回行数上限，仅返回已处理部分" : null);
    }

    private boolean matches(AttachmentChunk chunk, String rawLocator) {
        String locator = rawLocator == null ? "" : rawLocator.trim();
        if (locator.isBlank()) {
            return true;
        }
        String normalized = locator.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ordinal:")) {
            Integer ordinal = integerValue(locator.substring("ordinal:".length()));
            return ordinal != null && Objects.equals(ordinal, chunk.getOrdinal());
        }
        if (normalized.startsWith("page:")) {
            Integer page = integerValue(locator.substring(5));
            return page != null && Objects.equals(page, chunk.getPage());
        }
        if (normalized.startsWith("slide:")) {
            Integer slide = integerValue(locator.substring(6));
            return slide != null && Objects.equals(slide, chunk.getSlide());
        }
        if (normalized.startsWith("lines:")) {
            int[] range = range(locator.substring(6));
            return range != null && overlaps(chunk.getStartLine(), chunk.getEndLine(), range[0], range[1]);
        }
        if (normalized.startsWith("section:")) {
            String section = normalized.substring(8);
            String actual = value(chunk.getSection()).toLowerCase(Locale.ROOT);
            return actual.equals(section) || actual.startsWith(section + ".")
                    || section.startsWith(actual + ".");
        }
        if (normalized.startsWith("sheet:")) {
            String body = locator.substring(6);
            int rowsOffset = body.toLowerCase(Locale.ROOT).indexOf("!rows:");
            String sheet = rowsOffset >= 0 ? body.substring(0, rowsOffset) : body;
            if (!sheet.equalsIgnoreCase(value(chunk.getSheet()))) {
                return false;
            }
            if (rowsOffset < 0) {
                return true;
            }
            int[] range = range(body.substring(rowsOffset + "!rows:".length()));
            return range != null && overlaps(chunk.getStartRow(), chunk.getEndRow(), range[0], range[1]);
        }
        return normalized.equals(value(chunk.getLocator()).toLowerCase(Locale.ROOT));
    }

    private boolean matchesFilters(Map<String, String> row, List<FilterSpec> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (FilterSpec filter : filters) {
            if (filter == null || !StringUtils.hasText(filter.column())) {
                continue;
            }
            String actual = row.getOrDefault(filter.column(), "");
            String expected = filter.value() == null ? "" : filter.value();
            String operator = filter.operator() == null ? "eq" : filter.operator().toLowerCase(Locale.ROOT);
            boolean matched = switch (operator) {
                case "ne" -> !equalsValue(actual, expected);
                case "contains" -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
                case "gt" -> compareValues(actual, expected) > 0;
                case "gte" -> compareValues(actual, expected) >= 0;
                case "lt" -> compareValues(actual, expected) < 0;
                case "lte" -> compareValues(actual, expected) <= 0;
                case "in" -> List.of(expected.split("\\|", -1)).stream()
                        .anyMatch(candidate -> equalsValue(actual, candidate));
                default -> equalsValue(actual, expected);
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private void accumulateGroup(Map<String, AggregateAccumulator> groups,
                                 Map<String, String> row,
                                 String groupBy,
                                 List<AggregateSpec> metrics) {
        String key = row.getOrDefault(groupBy, "");
        AggregateAccumulator accumulator = groups.computeIfAbsent(key,
                ignored -> new AggregateAccumulator(groupBy, key, metrics));
        accumulator.add(row);
    }

    private Map<String, Object> project(Map<String, String> row, List<String> select) {
        Map<String, Object> projected = new LinkedHashMap<>();
        if (select == null || select.isEmpty()) {
            row.forEach(projected::put);
            return projected;
        }
        for (String column : select) {
            if (row.containsKey(column)) {
                projected.put(column, row.get(column));
            }
        }
        return projected;
    }

    private void sortRows(List<Map<String, Object>> rows, String sortBy, String direction) {
        if (!StringUtils.hasText(sortBy) || rows.size() < 2) {
            return;
        }
        Comparator<Map<String, Object>> comparator = Comparator.comparing(
                row -> value(row.get(sortBy)), this::compareCellValues);
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private int compareCellValues(String left, String right) {
        java.math.BigDecimal leftNumber = number(left);
        java.math.BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        return left.compareTo(right);
    }

    private Map<String, String> rowMap(List<String> columns, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            row.put(columns.get(index), index < values.size() ? values.get(index) : "");
        }
        return row;
    }

    private List<String> splitRow(String line) {
        return new ArrayList<>(List.of(line.split("\\t", -1)));
    }

    private boolean isTableFormat(String format) {
        return "csv".equalsIgnoreCase(format) || "tsv".equalsIgnoreCase(format)
                || "xlsx".equalsIgnoreCase(format);
    }

    private int[] range(String value) {
        String input = value.trim();
        Matcher matcher = ROW_RANGE.matcher(input);
        if (matcher.find()) {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        }
        matcher = SINGLE_ROW.matcher(input);
        if (matcher.find()) {
            int row = Integer.parseInt(matcher.group(1));
            return new int[]{row, row};
        }
        return null;
    }

    private boolean overlaps(Integer start, Integer end, int rangeStart, int rangeEnd) {
        if (start == null && end == null) {
            return false;
        }
        int actualStart = start == null ? end : start;
        int actualEnd = end == null ? start : end;
        return actualStart <= rangeEnd && actualEnd >= rangeStart;
    }

    private int compareValues(String left, String right) {
        java.math.BigDecimal leftNumber = number(left);
        java.math.BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        return left.compareTo(right);
    }

    private boolean equalsValue(String left, String right) {
        java.math.BigDecimal leftNumber = number(left);
        java.math.BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        return left.equalsIgnoreCase(right);
    }

    private java.math.BigDecimal number(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private AttachmentChunk copyChunk(AttachmentChunk source) {
        return AttachmentChunk.builder()
                .id(source.getId())
                .fileId(source.getFileId())
                .fileName(source.getFileName())
                .kind(source.getKind())
                .locator(source.getLocator())
                .title(source.getTitle())
                .text(source.getText())
                .ordinal(source.getOrdinal())
                .page(source.getPage())
                .slide(source.getSlide())
                .section(source.getSection())
                .sheet(source.getSheet())
                .startRow(source.getStartRow())
                .endRow(source.getEndRow())
                .startLine(source.getStartLine())
                .endLine(source.getEndLine())
                .columns(source.getColumns())
                .build();
    }

    private String continuationLocator(AttachmentChunk chunk) {
        if (chunk.getOrdinal() != null) {
            return "ordinal:" + (chunk.getOrdinal() + 1);
        }
        return chunk.getLocator();
    }

    private int safePrefixLength(String value, int requested) {
        if (value == null || requested <= 0) {
            return 0;
        }
        int length = Math.min(value.length(), requested);
        if (length > 0 && length < value.length()
                && Character.isHighSurrogate(value.charAt(length - 1))) {
            length--;
        }
        return Math.max(length, 0);
    }

    private Integer integerValue(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record FilterSpec(String column, String operator, String value) {
    }

    public record AggregateSpec(String column, String operator, String alias) {
    }

    public record ReadResult(String fileId,
                             String fileName,
                             String locator,
                             String nextLocator,
                             List<AttachmentChunk> chunks,
                             boolean truncated,
                             String warning) {
        static ReadResult empty(String warning) {
            return new ReadResult(null, null, null, null, List.of(), false, warning);
        }
    }

    public record TableQueryResult(String fileId,
                                   String fileName,
                                   String sheet,
                                   List<String> columns,
                                   List<Map<String, Object>> rows,
                                   long matchedRows,
                                   int returnedRows,
                                   boolean truncated,
                                   String warning) {
        static TableQueryResult empty(String warning) {
            return new TableQueryResult(null, null, null, List.of(), List.of(),
                    0, 0, false, warning);
        }
    }

    private static final class AggregateAccumulator {
        private final String groupColumn;
        private final String groupValue;
        private final List<AggregateSpec> metrics;
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, java.math.BigDecimal> sums = new LinkedHashMap<>();
        private final Map<String, java.math.BigDecimal> minimums = new LinkedHashMap<>();
        private final Map<String, java.math.BigDecimal> maximums = new LinkedHashMap<>();
        private long rows;

        private AggregateAccumulator(String groupColumn, String groupValue, List<AggregateSpec> metrics) {
            this.groupColumn = groupColumn;
            this.groupValue = groupValue;
            this.metrics = metrics == null || metrics.isEmpty()
                    ? List.of(new AggregateSpec(null, "count", "count"))
                    : metrics;
        }

        private void add(Map<String, String> row) {
            rows++;
            for (AggregateSpec metric : metrics) {
                String operator = value(metric.operator()).toLowerCase(Locale.ROOT);
                String column = metric.column();
                String key = metricKey(metric);
                if ("count".equals(operator)) {
                    counts.merge(key, 1L, Long::sum);
                    continue;
                }
                java.math.BigDecimal number = decimal(row.get(column));
                if (number == null) {
                    continue;
                }
                counts.merge(key, 1L, Long::sum);
                sums.merge(key, number, java.math.BigDecimal::add);
                minimums.merge(key, number, java.math.BigDecimal::min);
                maximums.merge(key, number, java.math.BigDecimal::max);
            }
        }

        private Map<String, Object> result() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(groupColumn, groupValue);
            for (AggregateSpec metric : metrics) {
                String key = metricKey(metric);
                String operator = value(metric.operator()).toLowerCase(Locale.ROOT);
                Object aggregate = switch (operator) {
                    case "sum" -> sums.get(key);
                    case "avg" -> counts.getOrDefault(key, 0L) == 0 ? null
                            : sums.get(key).divide(java.math.BigDecimal.valueOf(counts.get(key)), 6,
                            java.math.RoundingMode.HALF_UP);
                    case "min" -> minimums.get(key);
                    case "max" -> maximums.get(key);
                    default -> counts.getOrDefault(key, rows);
                };
                result.put(value(metric.alias()).isBlank() ? defaultAlias(metric) : metric.alias(), aggregate);
            }
            return result;
        }

        private String metricKey(AggregateSpec metric) {
            return value(metric.column()) + ":" + value(metric.operator());
        }

        private String defaultAlias(AggregateSpec metric) {
            String operator = value(metric.operator()).toLowerCase(Locale.ROOT);
            return StringUtils.hasText(metric.column()) ? operator + "_" + metric.column() : operator;
        }

        private java.math.BigDecimal decimal(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return new java.math.BigDecimal(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String value(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }
}
