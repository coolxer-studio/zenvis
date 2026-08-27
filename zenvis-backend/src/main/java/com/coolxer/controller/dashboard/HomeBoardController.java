package com.coolxer.controller.dashboard;

import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dashboard.EntityStatisticsRange;
import com.coolxer.model.dashboard.vo.EntityStatisticsVo;
import com.coolxer.model.dashboard.vo.SystemOverviewVo;
import com.coolxer.service.dashboard.SystemBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统状态看板")
@RestController
@RequestMapping("/api/v1/dashboard/home")
public class HomeBoardController {

    private final SystemBoardService systemBoardService;

    public HomeBoardController(SystemBoardService systemBoardService) {
        this.systemBoardService = systemBoardService;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询系统状态看板概览")
    public ResponseWrap<SystemOverviewVo> overview() {
        return ResponseWrap.success(systemBoardService.overview());
    }

    @GetMapping("/entity-statistics")
    @Operation(summary = "查询实体上报趋势和数据量排行")
    public ResponseWrap<EntityStatisticsVo> entityStatistics(
            @RequestParam(value = "range", defaultValue = "TODAY") EntityStatisticsRange range) {
        return ResponseWrap.success(systemBoardService.entityStatistics(range));
    }
}
