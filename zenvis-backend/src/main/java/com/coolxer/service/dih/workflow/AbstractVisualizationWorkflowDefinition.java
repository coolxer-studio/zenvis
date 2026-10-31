package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Shared read-only query workflow for the three visualization agents. Persistence and
 * publication only exist in the application and dashboard subclasses.
 */
public abstract class AbstractVisualizationWorkflowDefinition implements WorkflowDefinition {

    static final String VERIFIED_CONFIG_KEY = "verifiedPersistedConfig";
    static final String VERIFIED_DASHBOARD_KEY = "verifiedPersistedDashboard";

    protected abstract boolean writable();

    protected Set<String> domainPartTypes() {
        return Set.of();
    }

    protected Set<String> domainRecordTypes() {
        return Set.of();
    }

    protected String objectTypeForRecord(String partType) {
        return defaultObjectType();
    }

    @Override
    public boolean supportsPart(ChatMessagePart part) {
        if (part == null) {
            return false;
        }
        return Set.of("info-steps", "confirm",
                        VisualizationAgentTypes.decisionPartType(agentType()))
                .contains(part.getType())
                || (VisualizationAgentTypes.isChart(agentType())
                && "visualization-chart-preview".equals(part.getType()))
                || (writable() && "config".equals(part.getType()))
                || domainPartTypes().contains(part.getType());
    }

    @Override
    public AgentWorkflowStep resolveStep(AgentWorkflowState state,
                                         ChatMessagePart part,
                                         Map<String, Object> metadata) {
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        if (missingMeta(metadata)) {
            return AgentWorkflowStep.BLOCKED;
        }
        String action = text(metadata.get("action"));
        String prefix = VisualizationAgentTypes.actionPrefix(agentType());
        if (VisualizationAgentTypes.decisionPartType(agentType()).equals(part.getType())) {
            if ((prefix + ".select_entity_from_meta").equals(action)) {
                return AgentWorkflowStep.ENTITY_SELECTION;
            }
            if ((prefix + ".confirm_query_plan").equals(action)) {
                return AgentWorkflowStep.QUERY_PLAN_CONFIRMATION;
            }
            if ((prefix + ".confirm_menu").equals(action)) {
                return menuConfirmationReady(state)
                        ? AgentWorkflowStep.MENU_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if ((prefix + ".confirm_overwrite").equals(action)) {
                if (!writable()) {
                    return AgentWorkflowStep.BLOCKED;
                }
                return Set.of(AgentWorkflowStep.ARTIFACT_READY,
                                AgentWorkflowStep.PERSIST_CONFIRMATION)
                        .contains(state.getStep())
                        && persistencePlanReady(state, metadata)
                        ? AgentWorkflowStep.PERSIST_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if ((prefix + ".confirm_plan").equals(action)) {
                if (!writable()) {
                    return AgentWorkflowStep.BLOCKED;
                }
                return Set.of(AgentWorkflowStep.DATA_QUERY,
                                AgentWorkflowStep.ARTIFACT_READY,
                                AgentWorkflowStep.PERSIST_CONFIRMATION)
                        .contains(state.getStep())
                        && persistencePlanReady(state, metadata)
                        ? AgentWorkflowStep.PERSIST_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if (looksLikeEntitySelection(part, metadata)) {
                return AgentWorkflowStep.ENTITY_SELECTION;
            }
            return AgentWorkflowStep.INTENT_CONFIRMATION;
        }
        if ("visualization-chart-preview".equals(part.getType())) {
            return AgentWorkflowStep.ARTIFACT_READY;
        }
        if (domainRecordTypes().contains(part.getType())) {
            return AgentWorkflowStep.VERIFIED;
        }
        if ("config".equals(part.getType())) {
            return AgentWorkflowStep.ARTIFACT_READY;
        }
        if ("info-steps".equals(part.getType())) {
            return looksLikeEntitySelection(part, metadata)
                    ? AgentWorkflowStep.ENTITY_SELECTION
                    : AgentWorkflowStep.INTENT_CONFIRMATION;
        }
        return null;
    }

    @Override
    public String resolveObjectType(AgentWorkflowState state,
                                    ChatMessagePart part,
                                    Map<String, Object> metadata) {
        String explicit = text(metadata.get("objectType"));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if ("visualization-chart-preview".equals(part.getType())) {
            return "chart";
        }
        String objectType = objectTypeForRecord(part.getType());
        return StringUtils.hasText(objectType) ? objectType : defaultObjectType();
    }

    @Override
    public List<String> allowedActions(AgentWorkflowStep step,
                                       Map<String, Object> metadata) {
        if (missingMeta(metadata)) {
            return List.of();
        }
        return switch (step) {
            case INTENT_CONFIRMATION, ENTITY_SELECTION -> List.of("submit");
            case QUERY_PLAN_CONFIRMATION, PERSIST_CONFIRMATION, MENU_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case ARTIFACT_READY -> VisualizationAgentTypes.isChart(agentType())
                    && VisualizationAgentTypes.addChartLibraryAction(agentType())
                    .equals(text(metadata.get("action")))
                    ? List.of("add_to_library", "retry")
                    : List.of("retry");
            case BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    protected boolean missingMeta(Map<String, Object> metadata) {
        String kind = text(metadata.get("kind")).toLowerCase().replace('-', '_');
        return "missing_meta".equals(kind)
                || "meta_missing".equals(kind)
                || "meta_not_found".equals(kind);
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> Set.of(AgentWorkflowStep.INTENT_CONFIRMATION,
                    AgentWorkflowStep.ENTITY_SELECTION).contains(step);
            case "approve", "reject" -> step == AgentWorkflowStep.QUERY_PLAN_CONFIRMATION
                    || (writable() && Set.of(AgentWorkflowStep.PERSIST_CONFIRMATION,
                    AgentWorkflowStep.MENU_CONFIRMATION).contains(step));
            case "revise" -> step == AgentWorkflowStep.QUERY_PLAN_CONFIRMATION
                    || step == AgentWorkflowStep.BLOCKED
                    || (writable() && Set.of(AgentWorkflowStep.PERSIST_CONFIRMATION,
                    AgentWorkflowStep.MENU_CONFIRMATION).contains(step));
            case "retry" -> Set.of(AgentWorkflowStep.BLOCKED,
                    AgentWorkflowStep.ARTIFACT_READY).contains(step);
            case "add_to_library" -> VisualizationAgentTypes.isChart(agentType())
                    && step == AgentWorkflowStep.ARTIFACT_READY;
            default -> false;
        };
    }

    @Override
    public AgentWorkflowStep transition(AgentWorkflowState state, String action) {
        AgentWorkflowStep current = state.getStep();
        return switch (action) {
            case "submit" -> current == AgentWorkflowStep.ENTITY_SELECTION
                    ? AgentWorkflowStep.ATTRIBUTE_META : AgentWorkflowStep.ENTITY_META;
            case "approve" -> switch (current) {
                case PERSIST_CONFIRMATION -> AgentWorkflowStep.PERSISTING;
                case MENU_CONFIRMATION -> AgentWorkflowStep.MENU_PUBLISHING;
                default -> AgentWorkflowStep.DATA_QUERY;
            };
            case "reject" -> current == AgentWorkflowStep.MENU_CONFIRMATION
                    ? AgentWorkflowStep.VERIFIED : AgentWorkflowStep.CANCELLED;
            case "revise" -> switch (current) {
                case MENU_CONFIRMATION -> AgentWorkflowStep.MENU_CONFIRMATION;
                case PERSIST_CONFIRMATION -> AgentWorkflowStep.ARTIFACT_READY;
                default -> AgentWorkflowStep.ENTITY_META;
            };
            case "retry" -> retryStep(state);
            default -> current;
        };
    }

    @Override
    public Map<String, Object> continuation(AgentWorkflowState state, String action) {
        return switch (state.getStep()) {
            case ENTITY_META -> continuation("已收到补充信息，平台将查询实体 Meta。",
                    "请进入实体 Meta 阶段。只能使用 retrieval_list_display_entity 或 retrieval_list_entity 的真实返回值生成严格实体选项。");
            case ATTRIBUTE_META -> continuation("已选择实体：" + text(state.getContext().get("selectedEntity")),
                    "平台已确认逻辑实体，请调用字段 Meta MCP，并只使用真实返回字段生成查询方案。");
            case DATA_QUERY -> continuation("已确认实体、字段和查询方案。",
                    "平台将严格执行已批准卡片中的 query.tool/query.request；不得重新生成查询参数或使用演示数据。");
            case PERSISTING -> continuation("已确认写入可视化配置。",
                    "请进入写入及读回校验阶段；只有写入成功且读回一致后才能记录成功。");
            case MENU_PUBLISHING -> continuation("已确认发布菜单入口。",
                    "请调用 menu_create，并在 menu_view 读回一致后输出菜单记录。");
            case MENU_CONFIRMATION -> "revise".equals(action)
                    ? continuation("已提交菜单调整要求。",
                    "只重新生成菜单确认卡；保留已成功的配置和 Dashboard，不得重复写入或伪造菜单记录。")
                    : Map.of();
            case ARTIFACT_READY -> continuation("已提交调整要求。",
                    "请基于调整要求重新生成方案，已有已验证产物必须保留。");
            case CANCELLED -> continuation("已取消当前流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试失败阶段。", "请从服务端工作流指定的检查点继续。")
                    : Map.of();
        };
    }

    @Override
    public void updateContext(AgentWorkflowState state,
                              ChatMessagePart part,
                              Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : state.getContext();
        state.setContext(context);
        if ("config".equals(part.getType()) && StringUtils.hasText(part.getContent())) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("kind", text(metadata.get("configKind")));
            candidate.put("content", part.getContent());
            candidate.put("digest", digest(part.getContent()));
            candidate.put("artifactId", "artifact:" + UUID.randomUUID());
            candidate.put("defaultFileName", text(metadata.get("defaultFileName")));
            context.put("persistenceCandidate", candidate);
            return;
        }
        copy(metadata, context, "entity");
        copy(metadata, context, "entities");
        copy(metadata, context, "fields");
        copy(metadata, context, "query");
        copy(metadata, context, "planId");
        if (domainRecordTypes().contains(part.getType())) {
            rememberVerifiedRecord(state, metadata);
        }
        String action = text(metadata.get("action"));
        String prefix = VisualizationAgentTypes.actionPrefix(agentType());
        if ((prefix + ".confirm_plan").equals(action)
                || (prefix + ".confirm_overwrite").equals(action)) {
            Map<String, Object> candidate = map(context.get("persistenceCandidate"));
            if (!candidate.isEmpty()) {
                metadata.put("candidateDigest", candidate.get("digest"));
                metadata.put("artifactId", candidate.get("artifactId"));
            }
            context.put("persistencePlan", persistencePlan(metadata));
        }
        if ((prefix + ".confirm_menu").equals(action)) {
            context.put("menuPlan", menuPlan(metadata));
        }
        if ("visualization-chart-preview".equals(part.getType())) {
            String artifactId = firstText(text(metadata.get("artifactId")),
                    text(metadata.get("id")), UUID.randomUUID().toString());
            metadata.put("artifactId", artifactId);
            metadata.put("source", "workflow");
            state.setArtifactId(artifactId);
            context.put("artifact", new LinkedHashMap<>(metadata));
        }
    }

    @Override
    public void rememberAnswers(AgentWorkflowState state,
                                List<Map<String, Object>> answers) {
        List<Map<String, Object>> safeAnswers = answers == null
                ? List.of()
                : answers.stream().map(answer -> (Map<String, Object>) new LinkedHashMap<>(answer)).toList();
        state.getContext().put("answers", safeAnswers);
        safeAnswers.stream()
                .filter(answer -> text(answer.get("id")).toLowerCase().contains("entity"))
                .map(answer -> text(answer.get("value")))
                .filter(StringUtils::hasText)
                .findFirst()
                .ifPresent(entity -> state.getContext().put("selectedEntity", entity));
    }

    @Override
    public String validateAction(AgentWorkflowState state,
                                 ChatMessagePart part,
                                 Map<String, Object> metadata,
                                 String action) {
        if (!"approve".equals(action)) {
            return "";
        }
        if (state.getStep() == AgentWorkflowStep.QUERY_PLAN_CONFIRMATION) {
            return Objects.equals(map(state.getContext().get("query")), map(metadata.get("query")))
                    && Objects.equals(text(state.getContext().get("planId")), text(metadata.get("planId")))
                    ? "" : "查询方案在生成后被修改，必须重新查询 Meta 并生成新方案";
        }
        if (state.getStep() == AgentWorkflowStep.PERSIST_CONFIRMATION) {
            Map<String, Object> candidate = map(state.getContext().get("persistenceCandidate"));
            if (!candidate.isEmpty() && !Objects.equals(
                    text(candidate.get("digest")), text(metadata.get("candidateDigest")))) {
                return "可视化配置在确认后被修改，必须重新生成写入确认卡";
            }
            return Objects.equals(map(state.getContext().get("persistencePlan")),
                    persistencePlan(metadata))
                    ? "" : "可视化写入参数与服务端锁定方案不一致";
        }
        if (state.getStep() == AgentWorkflowStep.MENU_CONFIRMATION) {
            if (!menuConfirmationReady(state)) {
                return "尚未完成可读回校验的配置或 Dashboard，不能确认菜单发布";
            }
            return Objects.equals(map(state.getContext().get("menuPlan")), menuPlan(metadata))
                    ? "" : "菜单参数在确认后被修改，必须重新生成菜单确认卡";
        }
        return "";
    }

    protected boolean looksLikeEntitySelection(ChatMessagePart part,
                                               Map<String, Object> metadata) {
        if (part.getTitle() != null && part.getTitle().contains("实体")) {
            return true;
        }
        Object steps = metadata.get("steps");
        if (!(steps instanceof List<?> list)) {
            return false;
        }
        return list.stream().anyMatch(value -> {
            Map<String, Object> step = map(value);
            return text(step.get("id")).toLowerCase().contains("entity")
                    || text(step.get("title")).contains("实体");
        });
    }

    private AgentWorkflowStep retryStep(AgentWorkflowState state) {
        List<Map<String, Object>> failures = state.getFailures();
        if (failures != null) {
            for (int index = failures.size() - 1; index >= 0; index--) {
                String retryStep = text(failures.get(index).get("retryStep"));
                try {
                    AgentWorkflowStep candidate = AgentWorkflowStep.valueOf(retryStep);
                    if (candidate == AgentWorkflowStep.BLOCKED || candidate == AgentWorkflowStep.CANCELLED
                            || (candidate == AgentWorkflowStep.DATA_QUERY && !hasRetryableQuery(state))) {
                        continue;
                    }
                    return candidate;
                } catch (RuntimeException ignored) {
                    // Try the previous actionable failure.
                }
            }
        }
        if (hasRetryableQuery(state)) {
            return AgentWorkflowStep.DATA_QUERY;
        }
        return state.getArtifactId() == null ? AgentWorkflowStep.ENTITY_META : AgentWorkflowStep.ARTIFACT_READY;
    }

    private boolean hasRetryableQuery(AgentWorkflowState state) {
        Map<String, Object> context = state.getContext();
        if (context == null) {
            return false;
        }
        Map<String, Object> query = map(context.get("query"));
        return StringUtils.hasText(text(query.get("tool"))) && !map(query.get("request")).isEmpty();
    }

    private Map<String, Object> continuation(String display, String request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display", display);
        if (StringUtils.hasText(request)) {
            result.put("request", request);
        }
        return result;
    }

    private Map<String, Object> persistencePlan(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("configKind", "configIndex", "configType", "fileName",
                "overwrite", "dashboard", "request")) {
            if (metadata.containsKey(key)) {
                result.put(key, metadata.get(key));
            }
        }
        return result;
    }

    private Map<String, Object> menuPlan(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("menu", "request", "targetConfigType", "targetConfigIndex")) {
            if (metadata.containsKey(key)) {
                result.put(key, metadata.get(key));
            }
        }
        return result;
    }

    protected boolean persistencePlanReady(AgentWorkflowState state,
                                           Map<String, Object> metadata) {
        Map<String, Object> candidate = map(state.getContext().get("persistenceCandidate"));
        if (!candidate.isEmpty()) {
            String configType = firstText(text(metadata.get("configType")),
                    text(metadata.get("configIndex")));
            String fileName = text(metadata.get("fileName"));
            if (!StringUtils.hasText(text(candidate.get("content")))
                    || !StringUtils.hasText(configType)
                    || !configType.matches("[A-Za-z0-9_-]+")
                    || !StringUtils.hasText(fileName)
                    || fileName.startsWith("/")
                    || fileName.contains("..")
                    || !fileName.matches("[A-Za-z0-9_./-]+")) {
                return false;
            }
        }
        Map<String, Object> dashboard = resourceRequest(metadata.get("dashboard"));
        Map<String, Object> menu = resourceRequest(metadata.get("menu"));
        boolean dashboardReady = dashboard.isEmpty()
                || (StringUtils.hasText(text(dashboard.get("name")))
                && StringUtils.hasText(text(dashboard.get("code"))));
        boolean dashboardAllowed = dashboard.isEmpty()
                || VisualizationAgentTypes.isDashboard(agentType());
        return dashboardAllowed && dashboardReady && menu.isEmpty()
                && (!candidate.isEmpty() || !dashboard.isEmpty() || !menu.isEmpty());
    }

    private void rememberVerifiedRecord(AgentWorkflowState state,
                                        Map<String, Object> metadata) {
        String recordKind = text(metadata.get("recordKind"));
        if (!StringUtils.hasText(recordKind)) {
            recordKind = "config";
        }
        Map<String, Object> context = state.getContext();
        if (VisualizationAgentTypes.isApp(agentType()) && "config".equals(recordKind)) {
            context.put(VERIFIED_CONFIG_KEY, true);
        } else if (VisualizationAgentTypes.isDashboard(agentType())) {
            if ("config".equals(recordKind)) {
                context.put(VERIFIED_CONFIG_KEY, true);
            } else if ("dashboard".equals(recordKind)) {
                context.put(VERIFIED_DASHBOARD_KEY, true);
            }
        }
    }

    private boolean menuConfirmationReady(AgentWorkflowState state) {
        if (!VisualizationAgentTypes.WRITABLE.contains(agentType())) {
            return false;
        }
        Map<String, Object> context = state.getContext();
        if (context == null || !Boolean.TRUE.equals(context.get(VERIFIED_CONFIG_KEY))) {
            return false;
        }
        return !VisualizationAgentTypes.isDashboard(agentType())
                || Boolean.TRUE.equals(context.get(VERIFIED_DASHBOARD_KEY));
    }

    private Map<String, Object> resourceRequest(Object value) {
        Map<String, Object> resource = map(value);
        Map<String, Object> request = map(resource.get("request"));
        return request.isEmpty() ? resource : request;
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    protected String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    protected String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    protected Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
