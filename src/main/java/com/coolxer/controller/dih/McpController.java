package com.coolxer.controller.dih;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.dih.dto.McpServerSearchDto;
import com.coolxer.model.dih.dto.McpToolCallDto;
import com.coolxer.model.dih.dto.McpApprovalDecisionDto;
import com.coolxer.model.dih.dto.McpInvocationSearchDto;
import com.coolxer.model.dih.dto.McpToolPolicyBulkUpdateDto;
import com.coolxer.model.dih.dto.McpToolPolicyUpdateDto;
import com.coolxer.model.dih.vo.McpServerVo;
import com.coolxer.model.dih.vo.McpToolVo;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.model.dih.vo.McpToolPolicyVo;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.controller.BaseController;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpClientService;
import com.coolxer.service.dih.mcp.McpToolPolicyService;
import com.coolxer.service.dih.mcp.McpToolContext;
import io.swagger.v3.oas.annotations.Operation;
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
 * MCP客户端服务管理。
 */
@Tag(name = "MCP服务管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/dih/mcp")
public class McpController extends BaseController {

    @Autowired
    private McpClientService mcpClientService;

    @Autowired
    private AgentMcpToolService agentMcpToolService;

    @Autowired
    private McpApprovalService mcpApprovalService;

    @Autowired
    private McpToolPolicyService mcpToolPolicyService;

    @GetMapping("/servers/list")
    @Operation(summary = "MCP服务列表", description = "分页查询外部MCP服务配置")
    public ResponseWrap<PageRowsVo<McpServerVo>> list(McpServerSearchDto searchDto) {
        try {
            return ResponseWrap.success(mcpClientService.getPageList(searchDto));
        } catch (Exception e) {
            log.error("查询MCP服务列表失败", e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/add")
    @Operation(summary = "新增MCP服务")
    public ResponseWrap<McpServerVo> add(@Valid @RequestBody McpServerDto dto) {
        try {
            return ResponseWrap.success(mcpClientService.create(dto));
        } catch (Exception e) {
            log.error("新增MCP服务失败", e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/{id}/update")
    @Operation(summary = "更新MCP服务")
    public ResponseWrap<?> update(@PathVariable("id") Integer id, @Valid @RequestBody McpServerDto dto) {
        try {
            if (mcpClientService.update(id, dto)) {
                return ResponseWrap.success("修改成功");
            }
            return ResponseWrap.fail();
        } catch (Exception e) {
            log.error("更新MCP服务失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/servers/{id}")
    @Operation(summary = "删除MCP服务")
    public ResponseWrap<?> delete(@PathVariable("id") Integer id) {
        try {
            mcpClientService.delete(id);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            log.error("删除MCP服务失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/servers/{id}/view")
    @Operation(summary = "MCP服务详情")
    public ResponseWrap<McpServerVo> view(@PathVariable("id") Integer id) {
        try {
            return ResponseWrap.success(mcpClientService.info(id));
        } catch (Exception e) {
            log.error("查询MCP服务详情失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/{id}/enable")
    @Operation(summary = "启用MCP服务")
    public ResponseWrap<McpServerVo> enable(@PathVariable("id") Integer id) {
        try {
            return ResponseWrap.success(mcpClientService.setEnabled(id, true));
        } catch (Exception e) {
            log.error("启用MCP服务失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/{id}/disable")
    @Operation(summary = "停用MCP服务")
    public ResponseWrap<McpServerVo> disable(@PathVariable("id") Integer id) {
        try {
            return ResponseWrap.success(mcpClientService.setEnabled(id, false));
        } catch (Exception e) {
            log.error("停用MCP服务失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/{id}/refresh")
    @Operation(summary = "刷新MCP服务连接")
    public ResponseWrap<McpServerVo> refresh(@PathVariable("id") Integer id) {
        try {
            return ResponseWrap.success(mcpClientService.refresh(id));
        } catch (Exception e) {
            log.error("刷新MCP服务失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/servers/refresh")
    @Operation(summary = "刷新全部MCP服务连接")
    public ResponseWrap<List<McpServerVo>> refreshAll() {
        try {
            return ResponseWrap.success(mcpClientService.refreshAll());
        } catch (Exception e) {
            log.error("刷新全部MCP服务失败", e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/tools")
    @Operation(summary = "MCP工具列表")
    public ResponseWrap<List<McpToolVo>> tools(@RequestParam(value = "serverId", required = false) Integer serverId) {
        try {
            return ResponseWrap.success(mcpClientService.listTools(serverId));
        } catch (Exception e) {
            log.error("查询MCP工具列表失败, serverId={}", serverId, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/tools/call")
    @Operation(summary = "测试调用MCP工具")
    public ResponseWrap<?> callTool(@RequestBody McpToolCallDto callDto) {
        try {
            return ResponseWrap.success(mcpClientService.callTool(callDto, getSessionUser()));
        } catch (Exception e) {
            log.error("调用MCP工具失败, serverId={}, serverCode={}, tool={}",
                    callDto == null ? null : callDto.getServerId(),
                    callDto == null ? null : callDto.getServerCode(),
                    callDto == null ? null : callDto.getName(),
                    e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/tools/policies/list")
    @Operation(summary = "MCP工具审批策略列表")
    public ResponseWrap<PageRowsVo<McpToolPolicyVo>> toolPolicies(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "20") int perPage,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sourceType", required = false) McpToolSourceType sourceType,
            @RequestParam(value = "policy", required = false) McpApprovalPolicy policy,
            @RequestParam(value = "available", required = false) Boolean available) {
        return ResponseWrap.success(mcpToolPolicyService.list(page, perPage, keyword, sourceType, policy, available));
    }

    @PostMapping("/tools/policies/update")
    @Operation(summary = "更新单个MCP工具审批策略")
    public ResponseWrap<McpToolPolicyVo> updateToolPolicy(@RequestBody McpToolPolicyUpdateDto dto) {
        requireSuperAdmin();
        if (dto == null || dto.getToolKey() == null) {
            return ResponseWrap.fail(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return ResponseWrap.success(mcpToolPolicyService.update(dto.getToolKey(), dto.getPolicy()));
    }

    @PostMapping("/tools/policies/bulk-update")
    @Operation(summary = "批量更新MCP工具审批策略")
    public ResponseWrap<List<McpToolPolicyVo>> bulkUpdateToolPolicy(@RequestBody McpToolPolicyBulkUpdateDto dto) {
        requireSuperAdmin();
        if (dto == null) {
            return ResponseWrap.fail(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return ResponseWrap.success(mcpToolPolicyService.bulkUpdate(dto.getToolKeys(), dto.getPolicy()));
    }

    @GetMapping("/approvals/list")
    @Operation(summary = "MCP待审批与调用记录列表")
    public ResponseWrap<PageRowsVo<McpApprovalVo>> approvals(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "20") int perPage) {
        return ResponseWrap.success(mcpApprovalService.listPendingApprovals(page, perPage, getSessionUser()));
    }

    @GetMapping("/approvals/{requestId}/view")
    @Operation(summary = "MCP审批详情")
    public ResponseWrap<McpApprovalVo> approvalView(@PathVariable("requestId") String requestId) {
        return ResponseWrap.success(mcpApprovalService.view(requestId, getSessionUser()));
    }

    @PostMapping("/approvals/{requestId}/decision")
    @Operation(summary = "提交MCP工具审批决策")
    public ResponseWrap<McpApprovalVo> approvalDecision(@PathVariable("requestId") String requestId,
                                                        @RequestBody McpApprovalDecisionDto dto) {
        if (dto == null) {
            return ResponseWrap.fail(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return ResponseWrap.success(mcpApprovalService.decide(
                requestId, dto.getDecision(), dto.getComment(), getSessionUser()));
    }

    @GetMapping("/invocations/list")
    @Operation(summary = "MCP工具调用审计列表")
    public ResponseWrap<PageRowsVo<McpApprovalVo>> invocations(McpInvocationSearchDto searchDto) {
        return ResponseWrap.success(mcpApprovalService.listInvocations(searchDto, getSessionUser()));
    }

    private void requireSuperAdmin() {
        com.coolxer.dao.mysql.entity.User user = getSessionUser();
        if (user == null || !Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }

    @GetMapping("/agent/prompt")
    @Operation(summary = "业务 Agent MCP 工具提示词", description = "查看指定业务Agent当前会加载的MCP服务和工具提示词")
    public ResponseWrap<SingleValueVo> agentPrompt(@RequestParam(value = "agentType", required = false) String agentType) {
        try {
            McpToolContext context = agentMcpToolService.resolve(agentType);
            return ResponseWrap.success(new SingleValueVo(context.systemPrompt()));
        } catch (Exception e) {
            log.error("查询业务Agent MCP工具提示词失败", e);
            return ResponseWrap.fail(e);
        }
    }
}
