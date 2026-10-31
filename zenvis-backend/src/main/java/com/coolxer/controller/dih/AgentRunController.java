package com.coolxer.controller.dih;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.dto.AgentRunRequestDto;
import com.coolxer.model.dih.dto.AgentRunSteerDto;
import com.coolxer.model.dih.dto.AgentRunInteractionResponseDto;
import com.coolxer.model.dih.vo.AgentRunEventVo;
import com.coolxer.model.dih.vo.AgentRunInteractionResponseVo;
import com.coolxer.model.dih.vo.AgentRunVo;
import com.coolxer.service.dih.agentrun.AgentRunInteractionService;
import com.coolxer.service.dih.agentrun.AgentRunService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 持久化 DIH 智能体 Run API。
 */
@RestController
@RequestMapping("/api/v1/dih/chat/runs")
public class AgentRunController extends BaseController {

    private final AgentRunService agentRunService;
    private final AgentRunInteractionService interactionService;

    public AgentRunController(AgentRunService agentRunService,
                              AgentRunInteractionService interactionService) {
        this.agentRunService = agentRunService;
        this.interactionService = interactionService;
    }

    @PostMapping
    @Operation(summary = "创建持久化智能体任务")
    public ResponseWrap<AgentRunVo> create(@RequestBody AgentRunRequestDto request) {
        return ResponseWrap.success(agentRunService.create(request, getSessionUser()));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "获取智能体任务快照")
    public ResponseWrap<AgentRunVo> view(@PathVariable("runId") String runId) {
        return ResponseWrap.success(agentRunService.get(runId, getSessionUser()));
    }

    @GetMapping
    @Operation(summary = "分页查询智能体任务")
    public ResponseWrap<PageRowsVo<AgentRunVo>> list(
            @RequestParam(value = "chatId", required = false) String chatId,
            @RequestParam(value = "status", required = false) AgentRunStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "20") int perPage) {
        return ResponseWrap.success(agentRunService.list(
                chatId, status, page, perPage, getSessionUser()));
    }

    @GetMapping("/{runId}/events")
    @Operation(summary = "回放并订阅智能体任务事件")
    public Flux<String> events(HttpServletResponse response,
                               @PathVariable("runId") String runId,
                               @RequestParam(value = "after", defaultValue = "0") long after,
                               @RequestParam(value = "types", required = false) List<String> types) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/x-ndjson;charset=UTF-8");
        return agentRunService.eventStream(runId, after, types, getSessionUser());
    }

    @GetMapping("/{runId}/events/list")
    @Operation(summary = "分页读取智能体任务事件")
    public ResponseWrap<List<AgentRunEventVo>> eventList(
            @PathVariable("runId") String runId,
            @RequestParam(value = "after", defaultValue = "0") long after,
            @RequestParam(value = "types", required = false) List<String> types) {
        return ResponseWrap.success(agentRunService.events(
                runId, after, types, getSessionUser()));
    }

    @PostMapping("/{runId}/messages")
    @Operation(summary = "向运行中的智能体任务发送用户主动消息")
    public ResponseWrap<AgentRunVo> sendMessage(@PathVariable("runId") String runId,
                                                @RequestBody AgentRunSteerDto request) {
        return ResponseWrap.success(agentRunService.steer(runId, request, getSessionUser()));
    }

    @PostMapping("/{runId}/interactions/{interactionId}/respond")
    @Operation(summary = "提交智能体任务交互响应")
    public ResponseWrap<AgentRunInteractionResponseVo> respondInteraction(
            @PathVariable("runId") String runId,
            @PathVariable("interactionId") String interactionId,
            @RequestBody AgentRunInteractionResponseDto request) {
        return ResponseWrap.success(interactionService.respond(
                runId, interactionId, request, getSessionUser()));
    }

    @PostMapping("/{runId}/pause")
    @Operation(summary = "暂停智能体任务")
    public ResponseWrap<AgentRunVo> pause(
            @PathVariable("runId") String runId,
            @RequestParam(value = "reason", required = false) String reason) {
        return ResponseWrap.success(agentRunService.pause(runId, reason, getSessionUser()));
    }

    @PostMapping("/{runId}/resume")
    @Operation(summary = "继续智能体任务")
    public ResponseWrap<AgentRunVo> resume(@PathVariable("runId") String runId) {
        return ResponseWrap.success(agentRunService.resume(runId, getSessionUser()));
    }

    @PostMapping("/{runId}/cancel")
    @Operation(summary = "取消智能体任务")
    public ResponseWrap<AgentRunVo> cancel(@PathVariable("runId") String runId) {
        return ResponseWrap.success(agentRunService.cancel(runId, getSessionUser()));
    }
}
