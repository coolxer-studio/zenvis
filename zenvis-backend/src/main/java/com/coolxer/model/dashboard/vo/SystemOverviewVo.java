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
public class SystemOverviewVo implements Serializable {

    private Date checkedAt;
    private String status;
    private String statusDescription;
    private Summary summary;
    private List<Notice> notices;
    private ServiceHealth serviceHealth;
    private List<BusinessServiceStatus> businessServiceStatus;
    private List<TaskStatus> analysisTaskStatus;
    private List<RecentAnalysisTask> recentAnalysisTasks;
    private boolean pushTaskSourceAvailable;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary implements Serializable {
        private long entityCount;
        private Long pushTaskCount;
        private long analysisTaskCount;
        private long businessServiceCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Notice implements Serializable {
        private String key;
        private long count;
        private String info;
        private String level;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealth implements Serializable {
        private Integer ratio;
        private long instanceCount;
        private long upCount;
        private long abnormalCount;
        private long eventCount24h;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessServiceStatus implements Serializable {
        private String status;
        private String description;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatus implements Serializable {
        private String status;
        private String description;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentAnalysisTask implements Serializable {
        private Integer id;
        private String name;
        private String status;
        private String statusDescription;
        private Date updateTime;
    }
}
