package com.coolxer.service.retrieval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AnalyticsQueryEngine {

    Number aggregate(QuerySource source, Metric metric, TimeWindow window);

    List<Map<String, Object>> trend(QuerySource source, Metric metric, TimeWindow window,
                                    String granularity);

    List<Map<String, Object>> distribution(DistributionSource source, TimeWindow window,
                                           int limit, boolean includeNull);

    long countAnyOf(QuerySource source, List<String> columns, String focusValue,
                    TimeWindow window);

    Map<String, Object> relations(List<RelationSource> sources, String focusValue,
                                  TimeWindow window, int limit);

    List<Map<String, Object>> relationTimeline(List<TimelineSource> sources, String focusValue,
                                               TimeWindow window, String granularity,
                                               int categoryLimit);

    List<Map<String, Object>> aggregateGroups(GroupQuery query, TimeWindow window);

    HistogramResult histogram(HistogramSource source, TimeWindow window);

    ScatterResult scatter(ScatterSource source, TimeWindow window);

    record Criterion(String column, String columnType, String operator, List<String> values) {
    }

    record QuerySource(String entity, String label, String tableName,
                       String timeColumn, String timeColumnType,
                       List<Criterion> criteria, String criteriaLogic) {
    }

    record Metric(String operation, String column, String columnType, Double percentile) {
        public Metric(String operation, String column, String columnType) {
            this(operation, column, columnType, null);
        }
    }

    record DistributionSource(QuerySource source, String dimensionColumn,
                              String dimensionColumnType) {
    }

    record RelationSource(QuerySource source, String sourceColumn, String sourceColumnType,
                          String targetColumn, String targetColumnType) {
    }

    record TimelineSource(RelationSource relation, String categoryColumn,
                          String categoryColumnType, String extractionType,
                          int extractionStart, int extractionLength) {
    }

    record GroupDimension(String name, String label, String column, String columnType,
                          String kind, String granularity, boolean includeNull) {
    }

    record GroupMetric(String name, String label, Metric metric) {
    }

    record GroupOrder(String field, String direction) {
    }

    record GroupQuery(QuerySource source, List<GroupDimension> dimensions,
                      List<GroupMetric> metrics, GroupOrder orderBy, int limit) {
    }

    record HistogramSource(QuerySource source, String field, String column,
                           String columnType, int bins, BigDecimal min,
                           BigDecimal max) {
    }

    record HistogramResult(List<Map<String, Object>> rows, BigDecimal min,
                           BigDecimal max, long total) {
    }

    record ScatterSource(QuerySource source, String xField, String xColumn,
                         String yField, String yColumn, String sizeField,
                         String sizeColumn, String categoryField, String categoryColumn,
                         String labelField, String labelColumn, String sortColumn,
                         String sortDirection, int limit) {
    }

    record ScatterResult(List<Map<String, Object>> rows, boolean hasMore) {
    }

    record TimeWindow(String startTime, String endTime) {
        public boolean allTime() {
            return startTime == null || endTime == null;
        }
    }
}
