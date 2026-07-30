package com.coolxer.controller.retrieval;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.retrieval.analytics.AggregateQueryRequest;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.HistogramQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.model.retrieval.analytics.ScatterQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.analytics.ValueStatisticsQueryRequest;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "实体统计分析")
@RestController
@RequestMapping("/api/v1/entity")
public class EntityAnalyticsController extends BaseController {

    private final EntityAnalyticsService analyticsService;

    public EntityAnalyticsController(EntityAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/overview/query")
    @Operation(summary = "多实体数据概览",
            description = "统计多个实体的累计量、当前周期量以及可选的对比周期。")
    public ResponseWrap<?> overview(@RequestBody OverviewQueryRequest request) {
        return ResponseWrap.success(analyticsService.overview(request));
    }

    @PostMapping("/summary/query")
    @Operation(summary = "实体指标汇总",
            description = "对单个实体执行COUNT、DISTINCT_COUNT、SUM、AVG、MIN、MAX指标。")
    public ResponseWrap<?> summary(@RequestBody SummaryQueryRequest request) {
        return ResponseWrap.success(analyticsService.summary(request));
    }

    @PostMapping("/trend/query")
    @Operation(summary = "实体时间趋势",
            description = "按小时、天、周或月统计一个或多个实体指标的趋势。")
    public ResponseWrap<?> trend(@RequestBody TrendQueryRequest request) {
        return ResponseWrap.success(analyticsService.trend(request));
    }

    @PostMapping("/distribution/query")
    @Operation(summary = "任意字段分组统计",
            description = "按一个或多个实体的任意兼容标量字段统计TopN分布，TopN最大100。")
    public ResponseWrap<?> distribution(@RequestBody DistributionQueryRequest request) {
        return ResponseWrap.success(analyticsService.distribution(request));
    }

    @PostMapping("/aggregate/query")
    @Operation(summary = "单实体多维聚合",
            description = "使用Meta逻辑字段执行最多两个维度、二十个指标的受控聚合查询。")
    public ResponseWrap<?> aggregate(@RequestBody AggregateQueryRequest request) {
        return ResponseWrap.success(analyticsService.aggregate(request));
    }

    @PostMapping("/histogram/query")
    @Operation(summary = "数值字段直方图",
            description = "对一个Meta数值字段执行5到100个受控区间的直方图统计。")
    public ResponseWrap<?> histogram(@RequestBody HistogramQueryRequest request) {
        return ResponseWrap.success(analyticsService.histogram(request));
    }

    @PostMapping("/scatter/query")
    @Operation(summary = "散点图与气泡图",
            description = "对Meta数值字段进行有上限、稳定排序的散点或气泡取样。")
    public ResponseWrap<?> scatter(@RequestBody ScatterQueryRequest request) {
        return ResponseWrap.success(analyticsService.scatter(request));
    }

    @PostMapping("/value-statistics/query")
    @Operation(summary = "指定值跨字段统计",
            description = "统计指定值在多个实体、多个逻辑字段中的精确匹配数量。")
    public ResponseWrap<?> valueStatistics(@RequestBody ValueStatisticsQueryRequest request) {
        return ResponseWrap.success(analyticsService.valueStatistics(request));
    }

    @PostMapping("/relations/query")
    @Operation(summary = "任意字段关系聚合",
            description = "按显式源字段、目标字段和时间字段映射聚合指定值的对端关系。")
    public ResponseWrap<?> relations(@RequestBody RelationQueryRequest request) {
        return ResponseWrap.success(analyticsService.relations(request));
    }

    @PostMapping("/relation-timeline/query")
    @Operation(summary = "任意字段关系时间轴",
            description = "按显式关系、时间和分类字段映射生成指定值的关系事件时间轴。")
    public ResponseWrap<?> relationTimeline(@RequestBody RelationTimelineQueryRequest request) {
        return ResponseWrap.success(analyticsService.relationTimeline(request));
    }
}
