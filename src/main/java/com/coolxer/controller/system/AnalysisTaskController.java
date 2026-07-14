package com.coolxer.controller.system;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.dto.McpApprovalDecisionDto;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.system.AnalysisTaskService;
import com.coolxer.service.dih.mcp.McpApprovalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI分析任务
 */
@Tag(name = "AI分析任务")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/analysis-task")
public class AnalysisTaskController extends BaseController {

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private McpApprovalService mcpApprovalService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@Valid @RequestBody AnalysisTaskDto analysisTaskDto) {
        try {
            return ResponseWrap.success(new AnalysisTaskVo(analysisTaskService.create(analysisTaskDto)));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            analysisTaskService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            analysisTaskService.deleteByIds(ids);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @Valid @RequestBody AnalysisTaskDto analysisTaskDto) {
        try {
            if (analysisTaskService.update(id, analysisTaskDto)) {
                return ResponseWrap.success("修改成功");
            }
            return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<PageRowsVo<AnalysisTaskVo>> list(AnalysisTaskSearchDto analysisTaskSearchDto) {
        try {
            return ResponseWrap.success(analysisTaskService.getPageList(analysisTaskSearchDto));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<AnalysisTaskVo> query(@PathVariable("id") Long id) {
        try {
            AnalysisTaskVo analysisTaskVo = analysisTaskService.info(id);
            if (analysisTaskVo == null) {
                return ResponseWrap.fail();
            }
            return ResponseWrap.success(analysisTaskVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/enqueue"})
    public ResponseWrap<AnalysisTaskVo> enqueue(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(analysisTaskService.enqueue(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/cancel"})
    public ResponseWrap<AnalysisTaskVo> cancel(@PathVariable("id") Long id) {
        try {
            return ResponseWrap.success(analysisTaskService.cancel(id));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/queue/run-once"})
    public ResponseWrap<AnalysisTaskVo> runOnce() {
        try {
            return ResponseWrap.success(analysisTaskService.executeNextTask());
        } catch (Exception e) {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @GetMapping({"/queue/status"})
    public ResponseWrap<AnalysisTaskQueueVo> queueStatus() {
        try {
            return ResponseWrap.success(analysisTaskService.queueStatus());
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/approvals/list"})
    public ResponseWrap<PageRowsVo<McpApprovalVo>> pendingApprovals(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "20") int perPage) {
        try {
            User currentUser = getSessionUser();
            AnalysisTaskVo task = requireTaskApprovalAccess(id, currentUser);
            return ResponseWrap.success(mcpApprovalService.listTaskPendingApprovals(
                    task.getId(), page, perPage, currentUser));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/approvals/{requestId}/decision"})
    public ResponseWrap<McpApprovalVo> decideApproval(
            @PathVariable("id") Long id,
            @PathVariable("requestId") String requestId,
            @RequestBody McpApprovalDecisionDto dto) {
        try {
            if (dto == null) {
                return ResponseWrap.fail(ResultCodeEnum.FIELD_IS_EMPTY);
            }
            User currentUser = getSessionUser();
            AnalysisTaskVo task = requireTaskApprovalAccess(id, currentUser);
            return ResponseWrap.success(mcpApprovalService.decideTask(
                    task.getId(), requestId, dto.getDecision(), dto.getComment(), currentUser));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    private AnalysisTaskVo requireTaskApprovalAccess(Long id, User currentUser) {
        AnalysisTaskVo task = analysisTaskService.info(id);
        if (task == null) {
            throw new ApiException(404, "分析任务不存在");
        }
        boolean superAdmin = currentUser != null && Boolean.TRUE.equals(currentUser.getIsSuperAdmin());
        boolean owner = currentUser != null && task.getCreateBy() != null
                && currentUser.getId().equals(task.getCreateBy());
        if (!superAdmin && !owner) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        return task;
    }
}
