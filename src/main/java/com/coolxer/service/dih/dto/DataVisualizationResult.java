package com.coolxer.service.dih.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据可视化结果数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataVisualizationResult {

    /**
     * 分析时间
     */
    private String visualizationTime;

    /**
     * 分析范围
     * 如: ["所有主机", "所有应用", "网络设备"]
     */
    private List<String> visualizationScope;

    /**
     * 可视化分析总结
     */
    private String summary;

    /**
     * 发现的问题列表
     */
    private List<Issue> issues;

    /**
     * 各维度系统健康状态
     * 如: {"cpu": 85.5, "memory": 75.2, "disk": 90.1, "network": 95.0}
     */
    private Map<String, Double> healthScores;

    /**
     * 图表配置列表
     */
    private List<ChartConfig> charts;

    /**
     * 建议的操作
     */
    private List<String> recommendedActions;

    /**
     * 问题详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        /**
         * 问题ID
         */
        private String id;

        /**
         * 问题类型
         * 如: "performance", "security", "error", "network"
         */
        private String type;

        /**
         * 问题级别
         * 如: "info", "warning", "critical"
         */
        private String level;

        /**
         * 问题描述
         */
        private String description;

        /**
         * 问题来源
         * 如: "CPU过高", "磁盘空间不足", "网络延迟"
         */
        private String source;

        /**
         * 设备/服务标识
         */
        private String deviceId;

        /**
         * 发现时间
         */
        private String detectedAt;

        /**
         * 问题状态
         * 如: "active", "resolved"
         */
        private String status;

        /**
         * 解决建议
         */
        private String suggestion;

        /**
         * 详细数据
         */
        private Map<String, Object> details;
    }

    /**
     * 图表配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartConfig {
        /**
         * 图表ID
         */
        private String id;

        /**
         * 图表标题
         */
        private String title;

        /**
         * 图表类型
         * 如: "line", "bar", "pie", "gauge"
         */
        private String type;

        /**
         * 图表描述
         */
        private String description;

        /**
         * 数据维度
         * 如: "performance", "network", "errors"
         */
        private String dimension;

        /**
         * ECharts 配置
         */
        private Map<String, Object> echartsOption;
    }
}
