package com.coolxer.controller.retrieval;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest;
import com.coolxer.model.retrieval.query.IpRelationQueryRequest;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.MetaDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体的通用接口-默认生成
 */
@Tag(name = "实体的通用接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/entity/")
public class EntityCountController extends BaseController {

    @Autowired
    private EntityCoreService entityCoreService;

    @Autowired
    private MetaDataService metaDataService;

    @GetMapping({"/count"})
    public ResponseWrap<?> count(@RequestParam(value = "entities") List<String> entities) {
        try {
            Map<String, Object> entitiesCount = entityCoreService.count(entities);
            return ResponseWrap.success(entitiesCount);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/count-increase"})
    public ResponseWrap<?> countIncrease(@RequestParam(value = "entities") List<String> entities) {
        try {
            Map<String, Object> entitiesCount = entityCoreService.count(entities);
            Map<String, Object> entitiesCountToday = entityCoreService.countToady(entities);
            Map<String, Object> data = new HashMap<>();
            data.put("count", entitiesCount);
            data.put("countToday", entitiesCountToday);
            return ResponseWrap.success(data);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/trend"})
    public ResponseWrap<?> trend(@RequestParam(value = "entities") List<String> entities) {
        try {
            Map<String, Object> entitiesCount = entityCoreService.trend(entities);
            return ResponseWrap.success(entitiesCount);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }

    }

    @GetMapping({"/statistics"})
    public ResponseWrap<?> statistics(@RequestParam(value = "entities") List<String> entities, @RequestParam(value = "field") String field) {
        try {
            Map<String, Object> entitiesCount = entityCoreService.statistics(entities, field);
            return ResponseWrap.success(entitiesCount);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/ip-statistics"})
    @Operation(
            summary = "跨实体 IP 数据统计",
            description = "按传入实体顺序，统计 src_ip、dst_ip、dest_ip 逻辑字段中精确匹配指定 IP 的数据量；同一实体内使用 OR 条件，单条数据只计数一次。")
    public ResponseWrap<?> ipStatistics(
            @Parameter(description = "参与统计的实体名称，可重复传参或使用逗号分隔", required = true)
            @RequestParam(value = "entities") List<String> entities,
            @Parameter(description = "待统计的 IPv4 或 IPv6 地址", required = true, example = "192.0.2.1")
            @RequestParam(value = "ip") String ip) {
        try {
            return ResponseWrap.success(entityCoreService.ipStatistics(entities, ip));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/ip-relations/query"})
    @Operation(
            summary = "跨实体 IP 关系聚合",
            description = "使用 Meta 逻辑字段显式映射，在最长 90 天的时间范围内聚合指定 IP 的真实对端 IP。")
    public ResponseWrap<?> ipRelations(@RequestBody IpRelationQueryRequest request) {
        try {
            return ResponseWrap.success(entityCoreService.ipRelations(request));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/ip-event-timeline/query"})
    @Operation(
            summary = "跨实体 IP 安全事件时间轴聚合",
            description = "使用 Meta 逻辑字段显式映射，在最长 90 天的时间范围内按方向和事件三级分类聚合指定 IP 的安全事件；不返回事件明细。")
    public ResponseWrap<?> ipEventTimeline(@RequestBody IpEventTimelineQueryRequest request) {
        try {
            return ResponseWrap.success(entityCoreService.ipEventTimeline(request));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

}
