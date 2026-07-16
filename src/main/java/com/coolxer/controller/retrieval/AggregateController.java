package com.coolxer.controller.retrieval;

import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dashboard.vo.StackedLineChartVo;
import com.coolxer.model.retrieval.vo.AggregateMsgInfoVo;
import com.coolxer.service.retrieval.AggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "数据检索")
@RestController
@RequestMapping("/api/v1/retrieval/aggregate")
public class AggregateController {

    private final AggregateService aggregateService;

    public AggregateController(AggregateService aggregateService) {
        this.aggregateService = aggregateService;
    }

    @GetMapping("/msg/tag")
    @Operation(summary = "实体聚合标签", description = "按实体元数据和检索条件统计标签")
    public ResponseWrap<AggregateMsgInfoVo> msgTag(@RequestParam Map<String, String> params) {
        requireParams(params);
        return ResponseWrap.success(aggregateService.findAgendaTagsByParams(params));
    }

    @GetMapping("/msg/trend")
    @Operation(summary = "实体数据分布", description = "按实体元数据和检索条件统计数据分布")
    public ResponseWrap<StackedLineChartVo> msgTrend(@RequestParam Map<String, String> params) {
        requireParams(params);
        return ResponseWrap.success(aggregateService.findMsgTrend(params));
    }

    private void requireParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数");
        }
    }
}
