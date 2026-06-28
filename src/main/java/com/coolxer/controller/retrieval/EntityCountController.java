package com.coolxer.controller.retrieval;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.MetaDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

}
