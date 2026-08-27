package com.coolxer.model.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityStatisticsVo implements Serializable {

    private String range;
    private Date startTime;
    private Date endTime;
    private String granularity;
    private List<String> xAxis;
    private List<EntitySeries> series;
    private List<EntityRanking> ranking;
    private int omittedEntityCount;
    private List<SkippedEntity> skippedEntities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntitySeries implements Serializable {
        private String name;
        private String label;
        private List<Long> data;
        private long total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityRanking implements Serializable {
        private String name;
        private String label;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedEntity implements Serializable {
        private String name;
        private String label;
        private String reason;
        private String message;
    }
}
