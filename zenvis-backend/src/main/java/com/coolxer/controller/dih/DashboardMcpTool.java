package com.coolxer.controller.dih;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardChangeSet;
import com.coolxer.model.dih.dashboard.DashboardDiagnostic;
import com.coolxer.model.dih.dashboard.DashboardManifest;
import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import com.coolxer.service.dih.dashboard.DashboardPackageService;
import com.coolxer.service.dih.dashboard.DashboardPreviewRegistry;
import com.coolxer.service.dih.dashboard.DashboardPublishService;
import com.coolxer.service.dih.dashboard.DashboardSupport;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * Controlled data-dashboard lifecycle. The model supplies only DashboardSpec or
 * DashboardChangeSet; HTML, AMIS, API paths, persistence and Dashboard state are
 * owned by the backend.
 */
@Service("visualizationDashboardMcpTool")
public class DashboardMcpTool {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "html", "html_content", "css", "javascript", "js", "script", "inline_script",
            "amis", "api_url", "api_path", "request_url", "sql", "menu", "menu_id",
            "menu_create", "config_body", "config_apply");

    private final DashboardPackageService packageService;
    private final DashboardPublishService publishService;
    private final DashboardPreviewRegistry previewRegistry;
    private final ChatSessionService chatSessionService;
    private final WorkflowStateStore workflowStateStore;
    private final DashboardRepository dashboardRepository;

    public DashboardMcpTool(DashboardPackageService packageService,
                            DashboardPublishService publishService,
                            DashboardPreviewRegistry previewRegistry,
                            ChatSessionService chatSessionService,
                            WorkflowStateStore workflowStateStore,
                            DashboardRepository dashboardRepository) {
        this.packageService = packageService;
        this.publishService = publishService;
        this.previewRegistry = previewRegistry;
        this.chatSessionService = chatSessionService;
        this.workflowStateStore = workflowStateStore;
        this.dashboardRepository = dashboardRepository;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_dashboard_list",
            description = "只读列出带受管清单的数据大屏、版本、模式、发布状态和 Dashboard 记录 ID")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DashboardManifest manifest : packageService.listManifests()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dashboard_id", manifest.getDashboardId());
            item.put("name", manifest.getName());
            item.put("dashboard_version", manifest.getDashboardVersion());
            item.put("mode", manifest.getMode());
            item.put("theme", manifest.getTheme());
            item.put("dashboard_type", manifest.getDashboardType());
            item.put("dashboard_record_id", manifest.getDashboardRecordId());
            item.put("publish_status", manifest.getPublishStatus());
            item.put("spec_digest", manifest.getSpecDigest());
            item.put("file_digest", manifest.getFileDigest());
            item.put("updated_at", manifest.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_dashboard_get",
            description = "读取受管大屏的清单、完整 DashboardSpec、文件摘要和只读兼容标记；无清单历史大屏只读返回")
    public Map<String, Object> get(
            @ToolParam(description = "稳定大屏标识，例如 user-event-dashboard") String dashboardId) {
        String normalized = DashboardSupport.normalizeDashboardId(dashboardId);
        if (!DashboardSupport.validDashboardId(normalized)) {
            throw new IllegalArgumentException("dashboard_id 无效");
        }
        DashboardManifest manifest = packageService.readManifest(normalized).orElse(null);
        if (manifest == null) {
            return packageService.legacyReadOnly(normalized);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("read_only", false);
        result.put("managed", true);
        result.put("dashboard_id", manifest.getDashboardId());
        result.put("name", manifest.getName());
        result.put("dashboard_version", manifest.getDashboardVersion());
        result.put("mode", manifest.getMode());
        result.put("theme", manifest.getTheme());
        result.put("dashboard_type", manifest.getDashboardType());
        result.put("dashboard_record_id", manifest.getDashboardRecordId());
        result.put("publish_status", manifest.getPublishStatus());
        result.put("spec_digest", manifest.getSpecDigest());
        result.put("file_digest", manifest.getFileDigest());
        result.put("meta_digest", manifest.getMetaDigest());
        result.put("files", manifest.getFiles());
        result.put("spec", manifest.getSpec());
        result.put("updated_at", manifest.getUpdatedAt());
        Dashboard dashboard = manifest.getDashboardRecordId() == null ? null
                : dashboardRepository.findById(manifest.getDashboardRecordId()).orElse(null);
        result.put("dashboard_record_present", dashboard != null);
        if (dashboard != null) {
            result.put("dashboard_code", dashboard.getCode());
            result.put("dashboard_source", dashboard.getSource());
            result.put("dashboard_html_path", dashboard.getHtmlPath());
            result.put("dashboard_config_index", dashboard.getConfigIndex());
            result.put("dashboard_is_default", Boolean.TRUE.equals(dashboard.getIsDefault()));
        }
        return result;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_dashboard_validate",
            description = "使用完整 DashboardSpec 或 DashboardChangeSet 校验 Meta、布局、查询与摘要，编译候选并生成受控真实数据预览，不写配置")
    public DashboardValidationResult validate(
            @ToolParam(description = "完整 DashboardSpec JSON；新建大屏时提供，更新时可为空", required = false)
            String specJson,
            @ToolParam(description = "DashboardChangeSet JSON；更新已有大屏时提供，包含 base_dashboard_version、base_spec_digest、base_file_digest 和完整 spec", required = false)
            String changeSetJson) {
        WorkflowBinding binding = workflowBinding();
        requireWorkflowStep(binding.state(), AgentWorkflowStep.DASHBOARD_VALIDATING,
                AgentWorkflowStep.BLOCKED);
        if (StringUtils.isBlank(specJson) == StringUtils.isBlank(changeSetJson)) {
            throw new IllegalArgumentException("spec_json 与 change_set_json 必须且只能提供一个");
        }
        assertNoForbiddenPayload(specJson, "spec_json");
        assertNoForbiddenPayload(changeSetJson, "change_set_json");
        DashboardSpec spec = readJson(specJson, DashboardSpec.class, "spec_json");
        DashboardChangeSet changes = readJson(changeSetJson, DashboardChangeSet.class, "change_set_json");
        String previewToken = previewRegistry.newPreviewToken();
        DashboardValidationResult result = packageService.build(
                spec, changes, binding.workflowId(), binding.chatId(), previewToken);
        if (!result.isPassed()) {
            previewRegistry.remove(binding.workflowId());
            Map<String, Object> invalid = new LinkedHashMap<>();
            invalid.put("validationStatus", "invalid");
            invalid.put("validation", result);
            invalid.put("dashboardId", result.getDashboardId());
            invalid.put("candidateDigest", "");
            invalid.put("validationDigest", "");
            invalid.put("specDigest", "");
            invalid.put("fileDigest", "");
            invalid.put("metaDigest", "");
            invalid.put("preview", Map.of());
            invalid.put("files", List.of());
            updateWorkflow(binding, AgentWorkflowStep.DASHBOARD_VALIDATING, invalid,
                    result.getDiagnostics());
            return result;
        }
        DashboardPackage packageValue = packageService.packageForValidation(result);
        Map<String, String> previewFiles = packageService.previewFiles(
                packageValue, binding.workflowId(), binding.chatId(), previewToken);
        previewRegistry.put(binding.workflowId(), binding.chatId(), binding.userId(),
                previewToken, result, packageValue, previewFiles);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("workflowId", binding.workflowId());
        context.put("validationStatus", "validated");
        context.put("validation", result);
        context.put("dashboardId", result.getDashboardId());
        context.put("dashboardVersion", result.getDashboardVersion());
        context.put("candidateDigest", result.getCandidateDigest());
        context.put("validationDigest", result.getValidationDigest());
        context.put("specDigest", result.getSpecDigest());
        context.put("fileDigest", result.getFileDigest());
        context.put("metaDigest", result.getMetaDigest());
        context.put("dashboardSpec", JacksonUtil.toMap(result.getSpec()));
        context.put("preview", result.getPreview());
        context.put("files", result.getFiles());
        updateWorkflow(binding, AgentWorkflowStep.DASHBOARD_VALIDATING, context, null);
        return result;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "visualization_dashboard_apply",
            description = "使用工作流锁定的 candidate_digest 和 validation_digest 一次发布大屏配置与 Dashboard，读回失败自动回滚；不创建菜单")
    public DashboardApplyResult apply(
            @ToolParam(description = "当前工作流 ID，必须来自 validate 结果") String workflowId,
            @ToolParam(description = "validate 返回的 candidate_digest") String candidateDigest,
            @ToolParam(description = "validate 返回的 validation_digest") String validationDigest) {
        BindContext context = requireContext();
        WorkflowBinding binding = workflowBinding();
        if (!workflowId.equals(binding.workflowId())) {
            throw new IllegalArgumentException("发布目标不是当前活动工作流");
        }
        DashboardApplyResult applied = previewRegistry
                .getApplied(workflowId, context.userId(), context.chatId(),
                        candidateDigest, validationDigest)
                .orElseGet(() -> cachedApplyResult(binding, candidateDigest, validationDigest));
        if (applied != null) {
            if (binding.state().getStep() != AgentWorkflowStep.VERIFIED) {
                updatesForAppliedWorkflow(binding, applied);
            }
            return applied;
        }
        DashboardPreviewRegistry.PreviewSession session = previewRegistry
                .getForApply(workflowId, context.userId(), context.chatId())
                .orElseThrow(() -> new IllegalArgumentException("预览候选不存在、已过期或不属于当前用户"));
        requireWorkflowStep(binding.state(), AgentWorkflowStep.DASHBOARD_APPLYING);
        DashboardApplyResult result = publishService.apply(session, candidateDigest, validationDigest);
        AgentWorkflowStep step = "verified".equals(result.getStatus())
                ? AgentWorkflowStep.VERIFIED : AgentWorkflowStep.BLOCKED;
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("applyResult", JacksonUtil.toMap(result));
        updates.put("publishedDashboardId", result.getDashboardId());
        updates.put("publishedDashboardVersion", result.getDashboardVersion());
        updates.put("publishedDashboardRecordId", result.getDashboardRecordId());
        updateWorkflow(binding, step, updates, result.getDiagnostics());
        if ("verified".equals(result.getStatus())) {
            previewRegistry.markApplied(workflowId, result);
        }
        return result;
    }

    private void updatesForAppliedWorkflow(WorkflowBinding binding, DashboardApplyResult result) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("applyResult", JacksonUtil.toMap(result));
        updates.put("publishedDashboardId", result.getDashboardId());
        updates.put("publishedDashboardVersion", result.getDashboardVersion());
        updates.put("publishedDashboardRecordId", result.getDashboardRecordId());
        updateWorkflow(binding, AgentWorkflowStep.VERIFIED, updates, result.getDiagnostics());
    }

    private DashboardApplyResult cachedApplyResult(WorkflowBinding binding,
                                                   String candidateDigest,
                                                   String validationDigest) {
        Map<String, Object> stateContext = binding.state().getContext() == null
                ? Map.of() : binding.state().getContext();
        if (!Objects.equals(candidateDigest, text(stateContext.get("candidateDigest")))
                || !Objects.equals(validationDigest, text(stateContext.get("validationDigest")))) {
            return null;
        }
        Object raw = stateContext.get("applyResult");
        if (!(raw instanceof Map<?, ?>)) {
            return null;
        }
        try {
            DashboardApplyResult result = JacksonConfig.OBJECT_MAPPER.convertValue(
                    raw, DashboardApplyResult.class);
            return result != null && "verified".equals(result.getStatus()) ? result : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void assertNoForbiddenPayload(String json, String parameter) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(json);
            forbiddenKey(root, parameter);
            if (root != null && root.path("spec").path("blocks").isArray()) {
                for (JsonNode block : root.path("spec").path("blocks")) {
                    JsonNode records = block.path("records");
                    if (records.isObject()) {
                        for (String mutation : List.of("create", "update", "delete")) {
                            if (records.has(mutation)) {
                                throw new IllegalArgumentException(parameter + " 的 records 不允许包含 " + mutation + " 写操作");
                            }
                        }
                    }
                }
            }
            if (root != null && root.path("blocks").isArray()) {
                for (JsonNode block : root.path("blocks")) {
                    JsonNode records = block.path("records");
                    if (records.isObject()) {
                        for (String mutation : List.of("create", "update", "delete")) {
                            if (records.has(mutation)) {
                                throw new IllegalArgumentException(parameter + " 的 records 不允许包含 " + mutation + " 写操作");
                            }
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(parameter + " 不是合法 JSON：" + e.getMessage(), e);
        }
    }

    private void forbiddenKey(JsonNode node, String parameter) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (FORBIDDEN_KEYS.contains(entry.getKey().toLowerCase())) {
                    throw new IllegalArgumentException(parameter + " 不允许包含字段：" + entry.getKey());
                }
                forbiddenKey(entry.getValue(), parameter);
            });
        } else if (node.isArray()) {
            node.forEach(item -> forbiddenKey(item, parameter));
        }
    }

    private <T> T readJson(String value, Class<T> type, String parameter) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(parameter + " 不是合法 JSON：" + e.getMessage(), e);
        }
    }

    private WorkflowBinding workflowBinding() {
        BindContext context = requireContext();
        ChatSession chatSession = chatSessionService.getChatSessionBySessionId(
                context.chatId(), user(context.userId()));
        if (chatSession == null) {
            throw new IllegalArgumentException("会话不存在或不属于当前用户");
        }
        AgentWorkflowState state = workflowStateStore.loadActive(
                chatSession, VisualizationAgentTypes.DASHBOARD).orElseGet(() -> {
            String now = Instant.now().toString();
            return new AgentWorkflowState()
                    .setWorkflowId(UUID.randomUUID().toString())
                    .setWorkflowType("visualization_dashboard")
                    .setAgentType(VisualizationAgentTypes.DASHBOARD)
                    .setObjectType("dashboard")
                    .setStep(AgentWorkflowStep.DASHBOARD_DISCOVERY)
                    .setStatus("active")
                    .setStateRevision(0)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
        });
        return new WorkflowBinding(context.userId(), context.chatId(), chatSession, state);
    }

    private BindContext requireContext() {
        McpInvocationContext invocation = McpInvocationContextHolder.current();
        if (invocation == null
                || invocation.channel() == McpInvocationChannel.MCP_SERVER
                || invocation.requesterUserId() == null
                || StringUtils.isBlank(invocation.chatId())) {
            throw new IllegalArgumentException("数据大屏工具必须由已登录的对话工作流调用");
        }
        return new BindContext(invocation.requesterUserId(), invocation.chatId());
    }

    private void requireWorkflowStep(AgentWorkflowState state, AgentWorkflowStep... allowed) {
        for (AgentWorkflowStep step : allowed) {
            if (step == state.getStep()) {
                return;
            }
        }
        throw new IllegalStateException("当前工作流阶段不允许此操作：" + state.getStep());
    }

    private void updateWorkflow(WorkflowBinding binding,
                                AgentWorkflowStep step,
                                Map<String, Object> updates,
                                List<DashboardDiagnostic> diagnostics) {
        AgentWorkflowState state = binding.state();
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(state.getContext());
        if (updates != null) {
            context.putAll(updates);
        }
        state.setContext(context);
        state.setStep(step);
        state.setStatus(switch (step) {
            case VERIFIED -> "completed";
            case BLOCKED -> "blocked";
            case CANCELLED -> "cancelled";
            default -> "active";
        });
        state.setStateRevision(state.getStateRevision() + 1);
        state.setUpdatedAt(Instant.now().toString());
        if (diagnostics != null) {
            List<Map<String, Object>> failures = state.getFailures() == null
                    ? new ArrayList<>() : new ArrayList<>(state.getFailures());
            for (DashboardDiagnostic diagnostic : diagnostics) {
                if ("error".equals(diagnostic.getSeverity())) {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("code", diagnostic.getCode());
                    failure.put("message", diagnostic.getMessage());
                    failure.put("retryStep", "DASHBOARD_VALIDATING");
                    failures.add(failure);
                }
            }
            state.setFailures(failures);
        }
        workflowStateStore.upsert(binding.chatSession(), state);
        chatSessionService.updateWorkflowState(
                (long) binding.chatSession().getId(),
                binding.chatSession().getMessages(),
                binding.chatSession().getExtraData(),
                user(binding.userId()));
    }

    private User user(Integer id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private record BindContext(Integer userId, String chatId) {
    }

    private record WorkflowBinding(Integer userId,
                                   String chatId,
                                   ChatSession chatSession,
                                   AgentWorkflowState state) {
        private String workflowId() {
            return state.getWorkflowId();
        }
    }
}
