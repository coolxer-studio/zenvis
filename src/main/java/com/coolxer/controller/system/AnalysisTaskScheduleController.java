package com.coolxer.controller.system;

import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.AnalysisTaskScheduleDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleEnabledDto;
import com.coolxer.model.system.dto.AnalysisTaskScheduleSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskScheduleVo;
import com.coolxer.service.system.AnalysisTaskScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI分析周期任务")
@RestController
@RequestMapping("/api/v1/system/analysis-task-schedule")
public class AnalysisTaskScheduleController extends BaseController {

    private final AnalysisTaskScheduleService scheduleService;

    public AnalysisTaskScheduleController(AnalysisTaskScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/add")
    public ResponseWrap<AnalysisTaskScheduleVo> add(@Valid @RequestBody AnalysisTaskScheduleDto dto) {
        try {
            return ResponseWrap.success(new AnalysisTaskScheduleVo(scheduleService.create(dto)));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/update")
    public ResponseWrap<?> update(@PathVariable("id") Long id,
                                  @Valid @RequestBody AnalysisTaskScheduleDto dto) {
        try {
            return scheduleService.update(id, dto) ? ResponseWrap.success("修改成功") : ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/list")
    public ResponseWrap<PageRowsVo<AnalysisTaskScheduleVo>> list(AnalysisTaskScheduleSearchDto search) {
        try {
            return ResponseWrap.success(scheduleService.getPageList(search));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/view")
    public ResponseWrap<AnalysisTaskScheduleVo> view(@PathVariable("id") Long id) {
        try {
            AnalysisTaskScheduleVo schedule = scheduleService.info(id);
            return schedule == null ? ResponseWrap.fail() : ResponseWrap.success(schedule);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/enabled")
    public ResponseWrap<AnalysisTaskScheduleVo> setEnabled(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnalysisTaskScheduleEnabledDto dto) {
        try {
            return ResponseWrap.success(scheduleService.setEnabled(id, dto.getEnabled()));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            scheduleService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }
}
