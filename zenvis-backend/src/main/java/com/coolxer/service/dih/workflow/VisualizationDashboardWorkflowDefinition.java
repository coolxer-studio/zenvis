package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Controlled DashboardSpec workflow. The model proposes specs and change sets;
 * the platform validates, previews and publishes both configuration and the
 * Dashboard record atomically. Menus are intentionally outside this workflow.
 */
@Component
public class VisualizationDashboardWorkflowDefinition extends AbstractVisualizationWorkflowDefinition {

    @Override
    public String agentType() {
        return VisualizationAgentTypes.DASHBOARD;
    }

    @Override
    public String workflowType() {
        return "visualization_dashboard";
    }

    @Override
    public String defaultObjectType() {
        return "dashboard";
    }

    @Override
    protected boolean writable() {
        return true;
    }

    @Override
    public boolean supportsPart(ChatMessagePart part) {
        return part != null
                && !"config".equals(part.getType())
                && super.supportsPart(part);
    }

    @Override
    protected Set<String> domainPartTypes() {
        return Set.of("visualization-dashboard-record", "visualization-dashboard-preview");
    }

    @Override
    protected Set<String> domainRecordTypes() {
        return Set.of("visualization-dashboard-record");
    }

    @Override
    protected String objectTypeForRecord(String partType) {
        return "dashboard";
    }

    @Override
    public AgentWorkflowStep resolveStep(AgentWorkflowState state,
                                         ChatMessagePart part,
                                         Map<String, Object> metadata) {
        normalizeDashboardKeys(metadata);
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        if (missingMeta(metadata)) {
            return AgentWorkflowStep.BLOCKED;
        }
        String action = text(metadata.get("action"));
        if ("visualization-dashboard-decision".equals(part.getType())) {
            if (VisualizationAgentTypes.selectEntityAction(agentType()).equals(action)) {
                return AgentWorkflowStep.ENTITY_SELECTION;
            }
            if (VisualizationAgentTypes.confirmQueryPlanAction(agentType()).equals(action)) {
                return AgentWorkflowStep.QUERY_PLAN_CONFIRMATION;
            }
            if (VisualizationAgentTypes.confirmDashboardPlanAction(agentType()).equals(action)) {
                return dashboardPlanReady(state, metadata)
                        ? AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if (VisualizationAgentTypes.confirmPublishAction(agentType()).equals(action)) {
                return validatedPreviewReady(state, metadata)
                        ? AgentWorkflowStep.DASHBOARD_VALIDATING
                        : AgentWorkflowStep.BLOCKED;
            }
            if (VisualizationAgentTypes.confirmPlanAction(agentType()).equals(action)
                    || VisualizationAgentTypes.confirmOverwriteAction(agentType()).equals(action)
                    || VisualizationAgentTypes.confirmMenuAction(agentType()).equals(action)) {
                return AgentWorkflowStep.BLOCKED;
            }
            if (looksLikeEntitySelection(part, metadata)) {
                return AgentWorkflowStep.ENTITY_SELECTION;
            }
            return AgentWorkflowStep.INTENT_CONFIRMATION;
        }
        if ("visualization-dashboard-preview".equals(part.getType())) {
            if (!validatedPreviewReady(state, metadata)) {
                return AgentWorkflowStep.BLOCKED;
            }
            return AgentWorkflowStep.DASHBOARD_VALIDATING;
        }
        if ("info-steps".equals(part.getType())) {
            return looksLikeEntitySelection(part, metadata)
                    ? AgentWorkflowStep.ENTITY_SELECTION
                    : AgentWorkflowStep.DASHBOARD_DISCOVERY;
        }
        if (domainRecordTypes().contains(part.getType())) {
            return AgentWorkflowStep.VERIFIED;
        }
        return null;
    }

    @Override
    public List<String> allowedActions(AgentWorkflowStep step,
                                       Map<String, Object> metadata) {
        if (missingMeta(metadata)) {
            return List.of();
        }
        return switch (step) {
            case INTENT_CONFIRMATION, DASHBOARD_DISCOVERY, ENTITY_SELECTION ->
                    List.of("submit");
            case QUERY_PLAN_CONFIRMATION, DASHBOARD_PLAN_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case DASHBOARD_VALIDATING -> hasLockedDigests(metadata)
                    ? List.of("approve", "reject", "revise")
                    : List.of("retry", "revise");
            case BLOCKED -> List.of("revise", "retry");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> Set.of(AgentWorkflowStep.INTENT_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_DISCOVERY,
                    AgentWorkflowStep.ENTITY_SELECTION).contains(step);
            case "approve" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_VALIDATING).contains(step);
            case "reject" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_VALIDATING).contains(step);
            case "revise" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION,
                    AgentWorkflowStep.DASHBOARD_VALIDATING,
                    AgentWorkflowStep.BLOCKED).contains(step);
            case "retry" -> Set.of(AgentWorkflowStep.DASHBOARD_VALIDATING,
                    AgentWorkflowStep.BLOCKED).contains(step);
            default -> false;
        };
    }

    @Override
    public AgentWorkflowStep transition(AgentWorkflowState state, String action) {
        AgentWorkflowStep current = state.getStep();
        return switch (action) {
            case "submit" -> AgentWorkflowStep.ENTITY_META;
            case "approve" -> switch (current) {
                case QUERY_PLAN_CONFIRMATION -> AgentWorkflowStep.DATA_QUERY;
                case DASHBOARD_PLAN_CONFIRMATION -> AgentWorkflowStep.DASHBOARD_VALIDATING;
                case DASHBOARD_VALIDATING -> AgentWorkflowStep.DASHBOARD_APPLYING;
                default -> current;
            };
            case "reject" -> AgentWorkflowStep.CANCELLED;
            case "revise" -> switch (current) {
                case QUERY_PLAN_CONFIRMATION -> AgentWorkflowStep.ENTITY_META;
                default -> AgentWorkflowStep.DASHBOARD_DISCOVERY;
            };
            case "retry" -> AgentWorkflowStep.DASHBOARD_VALIDATING;
            default -> current;
        };
    }

    @Override
    public Map<String, Object> continuation(AgentWorkflowState state, String action) {
        return switch (state.getStep()) {
            case DASHBOARD_DISCOVERY -> continuation(
                    "已进入数据大屏发现阶段。",
                    "先调用 visualization_dashboard_list，并按需调用 visualization_dashboard_get 读取清单、版本、"
                            + "摘要与已发布 Meta；只生成 DashboardSpec 或 DashboardChangeSet，"
                            + "不得输出 HTML、CSS、JavaScript、AMIS、API URL、SQL、配置文件正文或菜单参数。");
            case ENTITY_META, ENTITY_SELECTION -> continuation(
                    "已收到大屏需求，平台将查询实体与字段 Meta。",
                    "仅使用 retrieval 只读 Meta 工具确认实体、字段、时间口径和查询工具，随后输出"
                            + " visualization_dashboard.confirm_dashboard_plan 完整方案卡。");
            case QUERY_PLAN_CONFIRMATION -> continuation(
                    "已确认大屏查询口径。",
                    "只使用锁定的查询工具和参数生成完整 DashboardSpec，并输出"
                            + " visualization_dashboard.confirm_dashboard_plan 方案卡。");
            case DASHBOARD_PLAN_CONFIRMATION -> Map.of();
            case DASHBOARD_VALIDATING -> continuation(
                    "已确认大屏方案，平台将执行确定性编译、Meta 校验和真实查询取样。",
                    "必须调用 visualization_dashboard_validate。校验通过后只输出 "
                            + "zenvis:visualization-dashboard-preview，原样携带服务端返回的 candidate_digest、"
                            + "validation_digest、files、diagnostics 和 preview；不得自行生成 HTML 或 AMIS。"
                            + "输出预览卡后停止本轮，等待用户确认发布。");
            case DASHBOARD_APPLYING -> continuation(
                    "已确认真实数据预览与发布内容。",
                    "只允许调用 visualization_dashboard_apply，参数必须原样使用工作流锁定的 workflow_id、"
                            + "candidate_digest 和 validation_digest；配置与 Dashboard 只执行一次高风险审批。");
            case VERIFIED -> continuation(
                    "大屏配置与 Dashboard 已发布并完成读回校验。",
                    "只输出服务端真实发布结果对应的 zenvis:visualization-dashboard-record，"
                            + "recordKind=dashboard；不得生成菜单记录。");
            case CANCELLED -> continuation("已取消当前数据大屏流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试数据大屏校验阶段。",
                    "从服务端锁定的安全检查点继续，不得复用已过期摘要。")
                    : Map.of();
        };
    }

    @Override
    public void updateContext(AgentWorkflowState state,
                              ChatMessagePart part,
                              Map<String, Object> metadata) {
        normalizeDashboardKeys(metadata);
        super.updateContext(state, part, metadata);
        Map<String, Object> context = state.getContext();
        String action = text(metadata.get("action"));
        if (VisualizationAgentTypes.confirmDashboardPlanAction(agentType()).equals(action)) {
            Map<String, Object> plan = new LinkedHashMap<>();
            for (String key : List.of("dashboardId", "dashboardVersion", "name", "mode", "theme",
                    "specDigest", "metaDigest", "blocks", "querySummary", "previewSummary", "spec")) {
                if (metadata.containsKey(key)) {
                    plan.put(key, metadata.get(key));
                }
            }
            context.put("dashboardPlan", plan);
        }
        if (VisualizationAgentTypes.confirmPublishAction(agentType()).equals(action)) {
            Map<String, Object> publish = new LinkedHashMap<>();
            for (String key : List.of("dashboardId", "dashboardVersion", "mode",
                    "candidateDigest", "validationDigest", "specDigest", "fileDigest",
                    "files", "diagnostics", "preview")) {
                if (metadata.containsKey(key)) {
                    publish.put(key, metadata.get(key));
                }
            }
            context.put("publishPlan", publish);
        }
    }

    @Override
    public void decorate(AgentWorkflowState state,
                         ChatMessagePart part,
                         Map<String, Object> metadata) {
        if ("visualization-dashboard-preview".equals(part.getType())) {
            if (!"blocked".equals(text(metadata.get("validationStatus")))) {
                metadata.put("validationStatus", "validated");
                metadata.put("passed", true);
            }
            metadata.putIfAbsent("action", VisualizationAgentTypes.confirmPublishAction(agentType()));
            metadata.putIfAbsent("allowedActions", List.of("approve", "reject", "revise"));
            metadata.putIfAbsent("workflowId", state.getWorkflowId());
            metadata.putIfAbsent("step", AgentWorkflowStep.DASHBOARD_VALIDATING.name());
        }
    }

    @Override
    public String validateAction(AgentWorkflowState state,
                                 ChatMessagePart part,
                                 Map<String, Object> metadata,
                                 String action) {
        normalizeDashboardKeys(metadata);
        if (!"approve".equals(action)) {
            return "";
        }
        if (state.getStep() == AgentWorkflowStep.DASHBOARD_PLAN_CONFIRMATION) {
            Object plan = state.getContext() == null
                    ? null : state.getContext().get("dashboardPlan");
            String expected = plan instanceof Map<?, ?> map
                    ? text(map.get("specDigest")) : "";
            return !StringUtils.hasText(expected)
                    || Objects.equals(expected, text(metadata.get("specDigest")))
                    ? "" : "大屏方案在确认后被修改，必须重新校验 Meta 并生成新方案";
        }
        if (state.getStep() == AgentWorkflowStep.DASHBOARD_VALIDATING) {
            return validatedPreviewReady(state, metadata)
                    ? "" : "预览候选或验证摘要与服务端锁定结果不一致，必须重新调用 visualization_dashboard_validate";
        }
        return "";
    }

    private boolean dashboardPlanReady(AgentWorkflowState state, Map<String, Object> metadata) {
        if (!StringUtils.hasText(text(metadata.get("dashboardId")))) {
            return false;
        }
        return Set.of(AgentWorkflowStep.INTENT_CONFIRMATION,
                AgentWorkflowStep.DASHBOARD_DISCOVERY,
                AgentWorkflowStep.ENTITY_META,
                AgentWorkflowStep.ENTITY_SELECTION,
                AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                AgentWorkflowStep.BLOCKED).contains(state.getStep());
    }

    private boolean validatedPreviewReady(AgentWorkflowState state, Map<String, Object> metadata) {
        if (state != null && !Set.of(AgentWorkflowStep.DASHBOARD_VALIDATING,
                AgentWorkflowStep.BLOCKED).contains(state.getStep())) {
            return false;
        }
        boolean explicitPassed = Boolean.TRUE.equals(metadata.get("passed"))
                || "validated".equals(text(metadata.get("validationStatus")));
        String candidate = text(metadata.get("candidateDigest"));
        String validation = text(metadata.get("validationDigest"));
        if (!StringUtils.hasText(candidate) || !StringUtils.hasText(validation)) {
            return false;
        }
        if (state == null) {
            return explicitPassed;
        }
        Map<String, Object> context = state.getContext() == null ? Map.of() : state.getContext();
        return candidate.equals(text(context.get("candidateDigest")))
                && validation.equals(text(context.get("validationDigest")));
    }

    private boolean hasLockedDigests(Map<String, Object> metadata) {
        return StringUtils.hasText(text(metadata.get("candidateDigest")))
                && StringUtils.hasText(text(metadata.get("validationDigest")));
    }

    private void normalizeDashboardKeys(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("dashboard_id", "dashboardId"),
                Map.entry("dashboard_version", "dashboardVersion"),
                Map.entry("validation_status", "validationStatus"),
                Map.entry("candidate_digest", "candidateDigest"),
                Map.entry("validation_digest", "validationDigest"),
                Map.entry("spec_digest", "specDigest"),
                Map.entry("meta_digest", "metaDigest"),
                Map.entry("file_digest", "fileDigest"),
                Map.entry("refresh_interval_seconds", "refreshIntervalSeconds"),
                Map.entry("dashboard_type", "dashboardType"));
        aliases.forEach((source, target) -> {
            if (!metadata.containsKey(target) && metadata.containsKey(source)) {
                metadata.put(target, metadata.get(source));
            }
        });
    }

    private static Map<String, Object> continuation(String title, String request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        if (StringUtils.hasText(request)) {
            result.put("request", request);
        }
        return result;
    }
}
