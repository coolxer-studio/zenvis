package com.coolxer.service.retrieval;

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

    record Criterion(String column, String columnType, String operator, List<String> values) {
    }

    record QuerySource(String entity, String label, String tableName,
                       String timeColumn, String timeColumnType,
                       List<Criterion> criteria, String criteriaLogic) {
    }

    record Metric(String operation, String column, String columnType) {
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

    record TimeWindow(String startTime, String endTime) {
        public boolean allTime() {
            return startTime == null || endTime == null;
        }
    }
}
