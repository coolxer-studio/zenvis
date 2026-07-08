package com.coolxer.lubinsun.controller;

import com.coolxer.lubinsun.model.LubinsunSkillRunEventVo;
import com.coolxer.lubinsun.model.LubinsunSkillRunTaskVo;
import com.coolxer.lubinsun.model.LubinsunTaskDetailVo;
import com.coolxer.lubinsun.model.LubinsunTaskDto;
import com.coolxer.lubinsun.model.LubinsunTaskSearchDto;
import com.coolxer.lubinsun.service.LubinsunTaskService;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Lubinsun Skill任务")
@Slf4j
@RestController
@RequestMapping("/api/v1/lubinsun/tasks")
public class LubinsunTaskController {

    private final LubinsunTaskService lubinsunTaskService;

    public LubinsunTaskController(LubinsunTaskService lubinsunTaskService) {
        this.lubinsunTaskService = lubinsunTaskService;
    }

    @PostMapping("/add")
    public ResponseWrap<LubinsunSkillRunTaskVo> add(@Valid @RequestBody LubinsunTaskDto dto) {
        try {
            return ResponseWrap.success(lubinsunTaskService.create(dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/ingest")
    public ResponseWrap<LubinsunSkillRunTaskVo> ingest(@Valid @RequestBody LubinsunTaskDto dto) {
        try {
            return ResponseWrap.success(lubinsunTaskService.create(dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/update")
    public ResponseWrap<LubinsunSkillRunTaskVo> update(@PathVariable("id") Long id,
                                                       @Valid @RequestBody LubinsunTaskDto dto) {
        try {
            return ResponseWrap.success(lubinsunTaskService.update(id, dto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/list")
    public ResponseWrap<PageRowsVo<LubinsunSkillRunTaskVo>> list(LubinsunTaskSearchDto searchDto) {
        try {
            return ResponseWrap.success(lubinsunTaskService.getPageList(searchDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/view")
    public ResponseWrap<LubinsunTaskDetailVo> view(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(lubinsunTaskService.info(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseWrap<String> delete(@PathVariable("id") Long id) {
        try {
            lubinsunTaskService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/bulk/{ids}")
    public ResponseWrap<String> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            lubinsunTaskService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseWrap<LubinsunSkillRunTaskVo> execute(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(lubinsunTaskService.execute(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/refresh")
    public ResponseWrap<LubinsunSkillRunTaskVo> refresh(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(lubinsunTaskService.refresh(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/events")
    public ResponseWrap<List<LubinsunSkillRunEventVo>> events(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(lubinsunTaskService.events(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }
}
