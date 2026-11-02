package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class VisualizationAppWorkflowDefinition extends AbstractVisualizationWorkflowDefinition {

    @Override
    public String agentType() {
        return VisualizationAgentTypes.APP;
    }

    @Override
    public String workflowType() {
        return "visualization_app";
    }

    @Override
    public String defaultObjectType() {
        return "data_application";
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
        return Set.of("visualization-app-record", "visualization-app-preview");
    }

    @Override
    protected Set<String> domainRecordTypes() {
        return Set.of("visualization-app-record");
    }

    @Override
    protected String objectTypeForRecord(String partType) {
        return "data_application";
    }

    @Override
    public AgentWorkflowStep resolveStep(AgentWorkflowState state,
                                         ChatMessagePart part,
                                         Map<String, Object> metadata) {
        normalizeDataAppKeys(metadata);
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        if (missingMeta(metadata)) {
            return AgentWorkflowStep.BLOCKED;
        }
        String action = text(metadata.get("action"));
        if ("visualization-app-decision".equals(part.getType())) {
            if (VisualizationAgentTypes.selectEntityAction(agentType()).equals(action)) {
                return AgentWorkflowStep.ENTITY_SELECTION;
            }
            if (VisualizationAgentTypes.confirmQueryPlanAction(agentType()).equals(action)) {
                return AgentWorkflowStep.QUERY_PLAN_CONFIRMATION;
            }
            if (VisualizationAgentTypes.confirmApplicationPlanAction(agentType()).equals(action)) {
                return appPlanReady(state, metadata)
                        ? AgentWorkflowStep.APP_PLAN_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if (VisualizationAgentTypes.confirmPublishAction(agentType()).equals(action)) {
                return publishPlanReady(state, metadata)
                        ? AgentWorkflowStep.PERSIST_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if (VisualizationAgentTypes.retryMenuAction(agentType()).equals(action)) {
                return state.getStep() == AgentWorkflowStep.PARTIAL_MENU
                        && lockedDigestsMatch(state, metadata)
                        ? AgentWorkflowStep.PARTIAL_MENU
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
        if ("visualization-app-preview".equals(part.getType())) {
            String validationStatus = text(metadata.get("validationStatus"));
            if (!Boolean.TRUE.equals(metadata.get("passed"))
                    && !Set.of("validated", "partial_menu").contains(validationStatus)) {
                return AgentWorkflowStep.BLOCKED;
            }
            if (!lockedDigestsMatch(state, metadata)) {
                return AgentWorkflowStep.BLOCKED;
            }
            return "partial_menu".equals(validationStatus)
                    ? AgentWorkflowStep.PARTIAL_MENU
                    : AgentWorkflowStep.PERSIST_CONFIRMATION;
        }
        if ("info-steps".equals(part.getType())) {
            return looksLikeEntitySelection(part, metadata)
                    ? AgentWorkflowStep.ENTITY_SELECTION
                    : AgentWorkflowStep.APP_DISCOVERY;
        }
        if ("config".equals(part.getType())) {
            return AgentWorkflowStep.ARTIFACT_READY;
        }
        if (domainRecordTypes().contains(part.getType())) {
            return isPartialMenu(metadata)
                    ? AgentWorkflowStep.PARTIAL_MENU
                    : AgentWorkflowStep.VERIFIED;
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
            case INTENT_CONFIRMATION, APP_DISCOVERY, ENTITY_SELECTION -> List.of("submit");
            case QUERY_PLAN_CONFIRMATION, APP_PLAN_CONFIRMATION, PERSIST_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case PARTIAL_MENU -> List.of("retry", "revise", "reject");
            case APP_VALIDATING, BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> Set.of(AgentWorkflowStep.INTENT_CONFIRMATION,
                    AgentWorkflowStep.APP_DISCOVERY,
                    AgentWorkflowStep.ENTITY_SELECTION).contains(step);
            case "approve" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.APP_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PERSIST_CONFIRMATION).contains(step);
            case "reject" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.APP_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PERSIST_CONFIRMATION,
                    AgentWorkflowStep.PARTIAL_MENU).contains(step);
            case "revise" -> Set.of(AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.APP_PLAN_CONFIRMATION,
                    AgentWorkflowStep.APP_VALIDATING,
                    AgentWorkflowStep.PERSIST_CONFIRMATION,
                    AgentWorkflowStep.PARTIAL_MENU,
                    AgentWorkflowStep.BLOCKED).contains(step);
            case "retry" -> Set.of(AgentWorkflowStep.APP_VALIDATING,
                    AgentWorkflowStep.PARTIAL_MENU,
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
                case APP_PLAN_CONFIRMATION -> AgentWorkflowStep.APP_VALIDATING;
                case PERSIST_CONFIRMATION -> AgentWorkflowStep.APP_APPLYING;
                default -> current;
            };
            case "reject" -> switch (current) {
                case PARTIAL_MENU -> AgentWorkflowStep.PARTIAL_MENU;
                default -> AgentWorkflowStep.CANCELLED;
            };
            case "revise" -> switch (current) {
                case PARTIAL_MENU -> AgentWorkflowStep.PARTIAL_MENU;
                case QUERY_PLAN_CONFIRMATION -> AgentWorkflowStep.ENTITY_META;
                default -> AgentWorkflowStep.APP_DISCOVERY;
            };
            case "retry" -> current == AgentWorkflowStep.PARTIAL_MENU
                    ? AgentWorkflowStep.PARTIAL_MENU : AgentWorkflowStep.APP_VALIDATING;
            default -> current;
        };
    }

    @Override
    public Map<String, Object> continuation(AgentWorkflowState state, String action) {
        if ("retry".equals(action) && state.getStep() == AgentWorkflowStep.PARTIAL_MENU) {
            Map<String, Object> context = state.getContext() == null
                    ? new LinkedHashMap<>() : state.getContext();
            state.setContext(context);
            context.put("menuRetryRequired", true);
        }
        return switch (state.getStep()) {
            case APP_DISCOVERY -> continuation(
                    "已进入数据应用发现阶段。",
                    "先调用 visualization_app_list/get 读取真实受管应用和 Meta。只生成 DataAppSpec 或 DataAppChangeSet，"
                            + "不得输出 AMIS、API URL、adaptor、JavaScript 或配置文件正文。");
            case ENTITY_META, META_DISCOVERY -> continuation(
                    "已收到应用需求，平台将查询实体与字段 Meta。",
                    "仅使用 retrieval 只读 Meta 工具确认实体、字段、操作符和展示类型，然后生成"
                            + " visualization_app.confirm_application_plan 方案卡。");
            case APP_PLAN_CONFIRMATION -> Map.of();
            case APP_VALIDATING -> continuation(
                    "已确认应用方案与查询口径。",
                    "必须调用 visualization_app_validate。校验通过后只输出 "
                            + "zenvis:visualization-app-preview，携带服务端返回的 candidate_digest、"
                            + "validation_digest、文件、菜单、诊断和 preview；不得自行编译 AMIS。");
            case PERSIST_CONFIRMATION -> continuation(
                    "已确认真实数据预览与发布内容。",
                    "只允许调用 visualization_app_apply，参数必须原样使用工作流锁定的 workflow_id、"
                            + "candidate_digest 和 validation_digest；配置与菜单只进行一次高风险审批。");
            case APP_APPLYING -> continuation(
                    "平台正在执行配置与菜单发布。",
                    "等待 visualization_app_apply 的真实读回结果；verified 才能声明成功，"
                            + "partial_menu 必须展示菜单重试入口，blocked 不得声明发布成功。");
            case PARTIAL_MENU -> continuation(
                    "应用配置已发布并读回，菜单仍未完成。",
                    "保留旧可用入口和当前版本锁定；只允许输出菜单重试卡并调用 "
                            + "visualization_app_retry_menu，不得重写应用文件或重新校验覆盖当前版本。");
            case CANCELLED -> continuation("已取消当前数据应用流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试数据应用阶段。", "请从服务端锁定的安全检查点继续。")
                    : Map.of();
        };
    }

    @Override
    public void updateContext(AgentWorkflowState state,
                              ChatMessagePart part,
                              Map<String, Object> metadata) {
        normalizeDataAppKeys(metadata);
        super.updateContext(state, part, metadata);
        Map<String, Object> context = state.getContext();
        String action = text(metadata.get("action"));
        if (VisualizationAgentTypes.confirmApplicationPlanAction(agentType()).equals(action)) {
            Map<String, Object> plan = new LinkedHashMap<>();
            for (String key : List.of("appId", "appVersion", "name", "description",
                    "homePageId", "specDigest", "pages", "changes", "querySummary", "spec")) {
                if (metadata.containsKey(key)) {
                    plan.put(key, metadata.get(key));
                }
            }
            context.put("applicationPlan", plan);
        }
        if (VisualizationAgentTypes.confirmPublishAction(agentType()).equals(action)) {
            Map<String, Object> publish = new LinkedHashMap<>();
            for (String key : List.of("appId", "appVersion", "candidateDigest",
                    "validationDigest", "files", "menu", "preview")) {
                if (metadata.containsKey(key)) {
                    publish.put(key, metadata.get(key));
                }
            }
            context.put("publishPlan", publish);
        }
        if (VisualizationAgentTypes.retryMenuAction(agentType()).equals(action)) {
            context.put("menuRetryRequired", true);
        }
    }

    @Override
    public void decorate(AgentWorkflowState state,
                         ChatMessagePart part,
                         Map<String, Object> metadata) {
        if (!"visualization-app-preview".equals(part.getType())) {
            return;
        }
        if (state.getStep() == AgentWorkflowStep.PARTIAL_MENU) {
            metadata.put("validationStatus", "partial_menu");
        } else if (state.getStep() == AgentWorkflowStep.PERSIST_CONFIRMATION) {
            metadata.put("validationStatus", "validated");
        }
        metadata.putIfAbsent("passed", true);
    }

    @Override
    public String validateAction(AgentWorkflowState state,
                                 ChatMessagePart part,
                                 Map<String, Object> metadata,
                                 String action) {
        normalizeDataAppKeys(metadata);
        if (state.getStep() == AgentWorkflowStep.PARTIAL_MENU
                && "retry".equals(action)) {
            return lockedDigestsMatch(state, metadata)
                    ? "" : "菜单重试必须使用工作流锁定的 candidate_digest 和 validation_digest";
        }
        if (!"approve".equals(action)) {
            return "";
        }
        if (state.getStep() == AgentWorkflowStep.APP_PLAN_CONFIRMATION) {
            Object plan = state.getContext() == null
                    ? null : state.getContext().get("applicationPlan");
            String expected = plan instanceof Map<?, ?> map
                    ? text(map.get("specDigest")) : "";
            return !StringUtils.hasText(expected)
                    || Objects.equals(expected, text(metadata.get("specDigest")))
                    ? "" : "应用方案在确认后被修改，必须重新校验 Meta 并生成新方案";
        }
        if (state.getStep() == AgentWorkflowStep.PERSIST_CONFIRMATION) {
            return lockedDigestsMatch(state, metadata)
                    ? "" : "候选或验证摘要与服务端锁定结果不一致，必须重新调用 visualization_app_validate";
        }
        return super.validateAction(state, part, metadata, action);
    }

    private boolean appPlanReady(AgentWorkflowState state, Map<String, Object> metadata) {
        String appId = text(metadata.get("appId"));
        if (!StringUtils.hasText(appId)) {
            return false;
        }
        return Set.of(AgentWorkflowStep.INTENT_CONFIRMATION,
                AgentWorkflowStep.APP_DISCOVERY,
                AgentWorkflowStep.ENTITY_META,
                AgentWorkflowStep.ENTITY_SELECTION,
                AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                AgentWorkflowStep.BLOCKED).contains(state.getStep());
    }

    private boolean publishPlanReady(AgentWorkflowState state, Map<String, Object> metadata) {
        if (state.getStep() != AgentWorkflowStep.PERSIST_CONFIRMATION) {
            return false;
        }
        return lockedDigestsMatch(state, metadata);
    }

    private boolean lockedDigestsMatch(AgentWorkflowState state, Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext() == null
                ? Map.of() : state.getContext();
        String candidate = text(context.get("candidateDigest"));
        String validation = text(context.get("validationDigest"));
        return StringUtils.hasText(candidate)
                && StringUtils.hasText(validation)
                && candidate.equals(text(metadata.get("candidateDigest")))
                && validation.equals(text(metadata.get("validationDigest")));
    }

    private boolean isPartialMenu(Map<String, Object> metadata) {
        return "partial_menu".equals(text(metadata.get("publishStatus")))
                || "partial_menu".equals(text(metadata.get("status")));
    }

    private void normalizeDataAppKeys(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        Map<String, String> aliases = Map.of(
                "app_id", "appId",
                "app_version", "appVersion",
                "validation_status", "validationStatus",
                "candidate_digest", "candidateDigest",
                "validation_digest", "validationDigest",
                "spec_digest", "specDigest",
                "meta_digest", "metaDigest",
                "previous_mode", "previousMode",
                "deleted_files", "deletedFiles",
                "home_page_id", "homePageId");
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
