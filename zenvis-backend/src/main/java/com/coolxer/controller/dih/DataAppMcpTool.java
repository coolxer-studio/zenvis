package com.coolxer.controller.dih;

import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.dataapp.DataAppApplyResult;
import com.coolxer.model.dih.dataapp.DataAppArtifact;
import com.coolxer.model.dih.dataapp.DataAppChangeSet;
import com.coolxer.model.dih.dataapp.DataAppDiagnostic;
import com.coolxer.model.dih.dataapp.DataAppManifest;
import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppSpec;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import com.coolxer.service.dih.dataapp.DataAppPackageService;
import com.coolxer.service.dih.dataapp.DataAppPreviewRegistry;
import com.coolxer.service.dih.dataapp.DataAppPublishService;
import com.coolxer.service.dih.dataapp.DataAppSupport;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.utils.JacksonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * Controlled data-application lifecycle exposed to the visualization-app agent.
 * The model supplies only DataAppSpec/DataAppChangeSet; this service owns all
 * AMIS, API, persistence and menu operations.
 */
@Service
public class DataAppMcpTool {

    private final DataAppPackageService packageService;
    private final DataAppPublishService publishService;
    private final DataAppPreviewRegistry previewRegistry;
    private final ChatSessionService chatSessionService;
    private final WorkflowStateStore workflowStateStore;
    private final ConfigService configService;

    public DataAppMcpTool(DataAppPackageService packageService,
                          DataAppPublishService publishService,
                          DataAppPreviewRegistry previewRegistry,
                          ChatSessionService chatSessionService,
                          WorkflowStateStore workflowStateStore,
                          ConfigService configService) {
        this.packageService = packageService;
        this.publishService = publishService;
        this.previewRegistry = previewRegistry;
        this.chatSessionService = chatSessionService;
        this.workflowStateStore = workflowStateStore;
        this.configService = configService;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_app_list",
            description = "只读列出带 _app.json 清单的受管数据应用")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataAppManifest manifest : packageService.listManifests()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("app_id", manifest.getAppId());
            item.put("name", manifest.getName());
            item.put("app_version", manifest.getAppVersion());
            item.put("mode", StringUtils.defaultIfBlank(manifest.getMode(), manifest.getMenuType()));
            item.put("home_page_id", manifest.getHomePageId());
            item.put("publish_status", manifest.getPublishStatus());
            item.put("menu_id", manifest.getMenuId());
            item.put("pages", manifest.getPages());
            item.put("updated_at", manifest.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_app_get",
            description = "读取受管数据应用的清单、DataAppSpec、页面摘要和受管文件摘要；无清单旧应用只读返回兼容标记")
    public Map<String, Object> get(
            @ToolParam(description = "稳定应用标识，例如 user-event-app") String appId) {
        String normalized = DataAppSupport.normalizeAppId(appId);
        if (!DataAppSupport.validAppId(normalized)) {
            throw new IllegalArgumentException("app_id 无效");
        }
        Optional<DataAppManifest> manifest = packageService.readManifest(normalized);
        if (manifest.isEmpty()) {
            return legacyReadOnly(normalized);
        }
        DataAppManifest value = manifest.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("read_only", false);
        result.put("managed", true);
        result.put("manifest", value);
        result.put("spec", value.getSpec());
        result.put("app_version", value.getAppVersion());
        result.put("spec_digest", value.getSpecDigest());
        List<Map<String, Object>> files = new ArrayList<>();
        value.getFiles().forEach((path, digest) -> {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", path);
            file.put("digest", digest);
            String content = configService.readFile(normalized, path);
            file.put("present", content != null);
            file.put("drift", content == null || !digest.equals(DataAppSupport.digest(content)));
            file.put("size", content == null ? null : content.getBytes(StandardCharsets.UTF_8).length);
            files.add(file);
        });
        result.put("files", files);
        result.put("next_action", "使用当前 app_version 和 spec_digest 生成 DataAppChangeSet；"
                + "只修改页面时通过 put_pages/delete_page_ids 提交。");
        return result;
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "visualization_app_validate",
            description = "确定性校验 DataAppSpec/DataAppChangeSet，按已发布 Meta 编译 AMIS，执行代表性真实查询并生成受控预览；不写入配置或菜单")
    public DataAppValidationResult validate(
            @ToolParam(description = "完整 DataAppSpec JSON；新建应用时提供，更新已有应用时可为空", required = false)
            String specJson,
            @ToolParam(description = "DataAppChangeSet JSON；更新已有应用时提供，包含 base_app_version、base_spec_digest、put_pages、delete_page_ids", required = false)
            String changeSetJson) {
        WorkflowBinding binding = workflowBinding();
        requireWorkflowStep(binding.state(),
                AgentWorkflowStep.APP_VALIDATING,
                AgentWorkflowStep.BLOCKED);
        DataAppSpec spec = readJson(specJson, DataAppSpec.class, "spec_json");
        DataAppChangeSet changes = readJson(changeSetJson, DataAppChangeSet.class, "change_set_json");
        String previewToken = previewRegistry.newPreviewToken();
        DataAppValidationResult result = packageService.build(
                spec, changes, binding.workflowId(), binding.chatId(), previewToken);
        if (!result.isPassed()) {
            updateWorkflow(binding, AgentWorkflowStep.APP_VALIDATING, Map.of(
                    "validationStatus", "invalid",
                    "validation", result), result.getDiagnostics());
            return result;
        }
        DataAppPackage packageValue = validationPackage(result, binding.workflowId());
        Map<String, String> previewFiles = packageService.previewFiles(
                packageValue, binding.workflowId(), binding.chatId(), previewToken);
        previewRegistry.put(binding.workflowId(), binding.chatId(), binding.userId(),
                previewToken, result, packageValue, previewFiles);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("workflowId", binding.workflowId());
        context.put("validationStatus", "validated");
        context.put("validation", result);
        context.put("appId", result.getAppId());
        context.put("appVersion", result.getAppVersion());
        context.put("candidateDigest", result.getCandidateDigest());
        context.put("validationDigest", result.getValidationDigest());
        context.put("specDigest", result.getSpecDigest());
        context.put("metaDigest", result.getMetaDigest());
        context.put("appSpec", JacksonUtil.toMap(result.getSpec()));
        context.put("menu", JacksonUtil.toMap(result.getMenu()));
        context.put("preview", result.getPreview());
        context.put("files", result.getFiles());
        context.put("deletedFiles", result.getDeletedFiles());
        updateWorkflow(binding, AgentWorkflowStep.APP_VALIDATING, context, null);
        return result;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "visualization_app_apply",
            description = "使用工作流锁定摘要，一次发布数据应用配置与菜单并执行全量读回；仅接受 visualization_app_validate 返回的 candidate_digest 和 validation_digest")
    public DataAppApplyResult apply(
            @ToolParam(description = "当前工作流 ID，必须来自 validate 结果的 workflow_id") String workflowId,
            @ToolParam(description = "validate 返回的 candidate_digest") String candidateDigest,
            @ToolParam(description = "validate 返回的 validation_digest") String validationDigest) {
        BindContext context = requireContext();
        DataAppPreviewRegistry.PreviewSession session = previewRegistry
                .getForApply(workflowId, context.userId(), context.chatId())
                .orElseThrow(() -> new IllegalArgumentException("预览候选不存在、已过期或不属于当前用户"));
        WorkflowBinding binding = workflowBinding();
        if (!workflowId.equals(binding.workflowId())) {
            throw new IllegalArgumentException("发布目标不是当前活动工作流");
        }
        requireWorkflowStep(binding.state(), AgentWorkflowStep.APP_APPLYING,
                AgentWorkflowStep.PERSIST_CONFIRMATION);
        DataAppApplyResult result = publishService.apply(session, candidateDigest, validationDigest);
        AgentWorkflowStep step = switch (result.getStatus()) {
            case "verified" -> AgentWorkflowStep.VERIFIED;
            case "partial_menu" -> AgentWorkflowStep.PARTIAL_MENU;
            default -> AgentWorkflowStep.BLOCKED;
        };
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("applyResult", JacksonUtil.toMap(result));
        updates.put("publishedAppId", result.getAppId());
        updates.put("publishedAppVersion", result.getAppVersion());
        updates.put("menuStatus", JacksonUtil.toMap(result.getMenu()));
        updateWorkflow(binding, step, updates, result.getDiagnostics());
        if ("verified".equals(result.getStatus())) {
            previewRegistry.remove(workflowId);
        }
        return result;
    }

    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "visualization_app_retry_menu",
            description = "仅重试 PARTIAL_MENU 状态下已锁定版本的菜单发布与读回，不重写应用文件")
    public DataAppApplyResult retryMenu(
            @ToolParam(description = "当前工作流 ID") String workflowId,
            @ToolParam(description = "锁定候选的 candidate_digest") String candidateDigest,
            @ToolParam(description = "锁定候选的 validation_digest") String validationDigest) {
        BindContext context = requireContext();
        DataAppPreviewRegistry.PreviewSession session = previewRegistry
                .getForApply(workflowId, context.userId(), context.chatId())
                .orElseThrow(() -> new IllegalArgumentException("菜单重试会话已过期，请重新校验"));
        if (!candidateDigest.equals(session.candidateDigest())
                || !validationDigest.equals(session.validationDigest())) {
            throw new IllegalArgumentException("菜单重试摘要与锁定候选不一致");
        }
        WorkflowBinding binding = workflowBinding();
        if (!workflowId.equals(binding.workflowId())) {
            throw new IllegalArgumentException("菜单重试目标不是当前活动工作流");
        }
        requireWorkflowStep(binding.state(), AgentWorkflowStep.PARTIAL_MENU);
        DataAppApplyResult result = publishService.retryMenu(session);
        AgentWorkflowStep step = "verified".equals(result.getStatus())
                ? AgentWorkflowStep.VERIFIED : AgentWorkflowStep.PARTIAL_MENU;
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("menuRetryResult", JacksonUtil.toMap(result));
        updateWorkflow(binding, step, updates, result.getDiagnostics());
        if ("verified".equals(result.getStatus())) {
            previewRegistry.remove(workflowId);
        }
        return result;
    }

    private DataAppPackage validationPackage(DataAppValidationResult result,
                                             String workflowId) {
        // The registry owns the candidate until apply; rebuild it from the same
        // manifest/spec content that was returned by validate.
        return packageService.packageForValidation(result, workflowId);
    }

    private Map<String, Object> legacyReadOnly(String appId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app_id", appId);
        result.put("read_only", true);
        result.put("managed", false);
        result.put("compatibility", "legacy");
        result.put("message", "该应用没有 _app.json 清单，仅可只读查看；不会被新流程接管或覆盖。");
        try {
            result.put("tree", configService.getConfigFileTree(appId));
        } catch (RuntimeException ignored) {
            result.put("tree", List.of());
        }
        return result;
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
                chatSession, VisualizationAgentTypes.APP).orElseGet(() -> {
            String now = Instant.now().toString();
            return new AgentWorkflowState()
                    .setWorkflowId(UUID.randomUUID().toString())
                    .setWorkflowType("visualization_app")
                    .setAgentType(VisualizationAgentTypes.APP)
                    .setObjectType("data_application")
                    .setStep(AgentWorkflowStep.APP_DISCOVERY)
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
            throw new IllegalArgumentException("数据应用工具必须由已登录的对话工作流调用");
        }
        return new BindContext(invocation.requesterUserId(), invocation.chatId());
    }

    private void requireWorkflowStep(AgentWorkflowState state,
                                     AgentWorkflowStep... allowed) {
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
                                List<DataAppDiagnostic> diagnostics) {
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
        if (diagnostics != null && !diagnostics.isEmpty()) {
            List<Map<String, Object>> failures = state.getFailures() == null
                    ? new ArrayList<>() : new ArrayList<>(state.getFailures());
            for (DataAppDiagnostic diagnostic : diagnostics) {
                if ("error".equals(diagnostic.getSeverity())) {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("code", diagnostic.getCode());
                    failure.put("message", diagnostic.getMessage());
                    failure.put("retryStep", step == AgentWorkflowStep.PARTIAL_MENU
                            ? "PARTIAL_MENU" : "APP_VALIDATING");
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
