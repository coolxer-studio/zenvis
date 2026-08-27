package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class DataVisualizationWorkflowDefinition implements WorkflowDefinition {

    private static final Set<String> PART_TYPES = Set.of(
            "config",
            "info-steps",
            "confirm",
            "visualization-chart-preview",
            "visualization-chart-record",
            "visualization-config-record",
            "dashboard-config-record",
            "menu-config-record");

    @Override
    public String agentType() {
        return DataVisualizationAgent.AGENT_TYPE;
    }

    @Override
    public String workflowType() {
        return "data_visualization";
    }

    @Override
    public String defaultObjectType() {
        return "visualization";
    }

    @Override
    public boolean supportsPart(ChatMessagePart part) {
        return part != null && PART_TYPES.contains(part.getType());
    }

    @Override
    public AgentWorkflowStep resolveStep(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        String action = text(metadata.get("action"));
        if ("info-steps".equals(part.getType())) {
            return "data_visualization.select_entity_from_meta".equals(action)
                    || looksLikeEntitySelection(part, metadata)
                    ? AgentWorkflowStep.ENTITY_SELECTION
                    : AgentWorkflowStep.INTENT_CONFIRMATION;
        }
        if ("confirm".equals(part.getType())) {
            if ("data_visualization.confirm_query_plan".equals(action)) {
                return AgentWorkflowStep.QUERY_PLAN_CONFIRMATION;
            }
            if ("data_visualization.apply_config".equals(action)) {
                return Set.of(
                        AgentWorkflowStep.DATA_QUERY,
                        AgentWorkflowStep.ARTIFACT_READY,
                        AgentWorkflowStep.PERSIST_CONFIRMATION)
                        .contains(state.getStep())
                        && persistencePlanReady(state, metadata)
                        ? AgentWorkflowStep.PERSIST_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            return null;
        }
        if ("visualization-chart-preview".equals(part.getType())) {
            return AgentWorkflowStep.ARTIFACT_READY;
        }
        if (Set.of("visualization-chart-record", "visualization-config-record",
                        "dashboard-config-record", "menu-config-record")
                .contains(part.getType())) {
            return AgentWorkflowStep.VERIFIED;
        }
        return null;
    }

    @Override
    public String resolveObjectType(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        String explicit = text(metadata.get("objectType"));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return switch (part.getType()) {
            case "visualization-chart-preview", "visualization-chart-record" ->
                    Set.of("data_application", "dashboard").contains(state.getObjectType())
                            ? state.getObjectType() : "chart";
            case "visualization-config-record" -> "visualization_config";
            case "dashboard-config-record" -> "dashboard";
            case "menu-config-record" -> "menu";
            default -> StringUtils.hasText(state.getObjectType())
                    ? state.getObjectType() : defaultObjectType();
        };
    }

    @Override
    public List<String> allowedActions(
            AgentWorkflowStep step,
            Map<String, Object> metadata) {
        return switch (step) {
            case INTENT_CONFIRMATION, ENTITY_SELECTION -> List.of("submit");
            case QUERY_PLAN_CONFIRMATION, PERSIST_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case ARTIFACT_READY -> "data_visualization.add_chart_library"
                    .equals(text(metadata.get("action")))
                    ? List.of("add_to_library", "retry")
                    : List.of("retry");
            case BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> Set.of(
                    AgentWorkflowStep.INTENT_CONFIRMATION,
                    AgentWorkflowStep.ENTITY_SELECTION).contains(step);
            case "approve", "reject" -> Set.of(
                    AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PERSIST_CONFIRMATION).contains(step);
            case "revise" -> Set.of(
                    AgentWorkflowStep.QUERY_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PERSIST_CONFIRMATION,
                    AgentWorkflowStep.BLOCKED).contains(step);
            case "retry" -> Set.of(
                    AgentWorkflowStep.BLOCKED,
                    AgentWorkflowStep.ARTIFACT_READY).contains(step);
            case "add_to_library" -> step == AgentWorkflowStep.ARTIFACT_READY;
            default -> false;
        };
    }

    @Override
    public AgentWorkflowStep transition(AgentWorkflowState state, String action) {
        AgentWorkflowStep current = state.getStep();
        return switch (action) {
            case "submit" -> current == AgentWorkflowStep.ENTITY_SELECTION
                    ? AgentWorkflowStep.ATTRIBUTE_META : AgentWorkflowStep.ENTITY_META;
            case "approve" -> current == AgentWorkflowStep.PERSIST_CONFIRMATION
                    ? AgentWorkflowStep.PERSISTING : AgentWorkflowStep.DATA_QUERY;
            case "reject" -> AgentWorkflowStep.CANCELLED;
            case "revise" -> current == AgentWorkflowStep.PERSIST_CONFIRMATION
                    ? AgentWorkflowStep.ARTIFACT_READY : AgentWorkflowStep.ENTITY_META;
            case "retry" -> retryStep(state);
            default -> current;
        };
    }

    @Override
    public Map<String, Object> continuation(
            AgentWorkflowState state,
            String action) {
        return switch (state.getStep()) {
            case ENTITY_META -> continuation(
                    "已收到补充信息，平台将查询实体 Meta。",
                    "请进入实体 Meta 阶段。只能使用 retrieval_list_display_entity "
                            + "或 retrieval_list_entity 的真实返回值生成严格实体选项。");
            case ATTRIBUTE_META -> {
                String entity = text(state.getContext().get("selectedEntity"));
                yield continuation(
                        "已选择实体：" + entity,
                        "平台已确认逻辑实体 " + entity
                                + "。请调用字段 Meta MCP，并只使用其真实返回字段生成查询方案。");
            }
            case DATA_QUERY -> continuation(
                    "已确认实体、字段和查询方案。",
                    "平台将严格执行已批准卡片中的 query.tool/query.request；"
                            + "不得重新生成查询参数或使用演示数据。");
            case PERSISTING -> continuation(
                    "已确认写入可视化配置。",
                    "请进入写入及读回校验阶段；只有创建成功且查看接口读回一致后才能记录成功。");
            case ARTIFACT_READY -> continuation(
                    "已提交调整要求。",
                    "请基于调整要求重新生成方案，已有已验证图表快照必须保留。");
            case CANCELLED -> continuation("已取消当前流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试失败阶段。", "请从服务端工作流指定的检查点继续。")
                    : Map.of();
        };
    }

    @Override
    public void updateContext(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext();
        if ("config".equals(part.getType())
                && StringUtils.hasText(part.getContent())) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("kind", text(metadata.get("configKind")));
            candidate.put("content", part.getContent());
            candidate.put("digest", digest(part.getContent()));
            candidate.put("artifactId", "artifact:" + UUID.randomUUID());
            candidate.put("defaultFileName",
                    text(metadata.get("defaultFileName")));
            context.put("persistenceCandidate", candidate);
            return;
        }
        copy(metadata, context, "entity");
        copy(metadata, context, "entities");
        copy(metadata, context, "fields");
        copy(metadata, context, "query");
        copy(metadata, context, "planId");
        if ("confirm".equals(part.getType())
                && "data_visualization.apply_config".equals(
                text(metadata.get("action")))) {
            Map<String, Object> candidate =
                    map(context.get("persistenceCandidate"));
            if (!candidate.isEmpty()) {
                metadata.put("candidateDigest", candidate.get("digest"));
                metadata.put("artifactId", candidate.get("artifactId"));
            }
            Map<String, Object> persistencePlan = new LinkedHashMap<>();
            for (String key : List.of(
                    "configKind", "configIndex", "configType", "fileName",
                    "overwrite", "dashboard", "menu", "request")) {
                if (metadata.containsKey(key)) {
                    persistencePlan.put(key, metadata.get(key));
                }
            }
            context.put("persistencePlan", persistencePlan);
        }
        if ("visualization-chart-preview".equals(part.getType())) {
            String artifactId = firstText(
                    text(metadata.get("artifactId")),
                    text(metadata.get("id")),
                    UUID.randomUUID().toString());
            metadata.put("artifactId", artifactId);
            metadata.put("source", "workflow");
            state.setArtifactId(artifactId);
            context.put("artifact", new LinkedHashMap<>(metadata));
        }
    }

    @Override
    public void rememberAnswers(
            AgentWorkflowState state,
            List<Map<String, Object>> answers) {
        List<Map<String, Object>> safeAnswers = answers == null
                ? List.of()
                : answers.stream()
                .map(answer -> (Map<String, Object>) new LinkedHashMap<>(answer))
                .toList();
        state.getContext().put("answers", safeAnswers);
        safeAnswers.stream()
                .filter(answer -> text(answer.get("id")).toLowerCase().contains("entity"))
                .map(answer -> text(answer.get("value")))
                .filter(StringUtils::hasText)
                .findFirst()
                .ifPresent(entity -> state.getContext().put("selectedEntity", entity));
        safeAnswers.stream()
                .filter(answer -> text(answer.get("id")).toLowerCase()
                        .contains("visualization_goal"))
                .map(answer -> text(answer.get("value")))
                .filter(StringUtils::hasText)
                .findFirst()
                .ifPresent(value -> state.setObjectType(visualizationObjectType(value)));
    }

    @Override
    public String validateAction(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata,
            String action) {
        if (!"approve".equals(action)) {
            return "";
        }
        if (state.getStep() == AgentWorkflowStep.QUERY_PLAN_CONFIRMATION) {
            return Objects.equals(
                    map(state.getContext().get("query")),
                    map(metadata.get("query")))
                    && Objects.equals(
                    text(state.getContext().get("planId")),
                    text(metadata.get("planId")))
                    ? "" : "查询方案在生成后被修改，必须重新查询 Meta 并生成新方案";
        }
        if (state.getStep() == AgentWorkflowStep.PERSIST_CONFIRMATION) {
            Map<String, Object> candidate =
                    map(state.getContext().get("persistenceCandidate"));
            if (!candidate.isEmpty()
                    && !Objects.equals(
                    text(candidate.get("digest")),
                    text(metadata.get("candidateDigest")))) {
                return "可视化配置在确认后被修改，必须重新生成写入确认卡";
            }
            return Objects.equals(
                    map(state.getContext().get("persistencePlan")),
                    persistencePlan(metadata))
                    ? "" : "可视化写入参数与服务端锁定方案不一致";
        }
        return "";
    }

    private boolean looksLikeEntitySelection(
            ChatMessagePart part,
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
        if (failures != null && !failures.isEmpty()) {
            String retryStep = text(failures.get(failures.size() - 1).get("retryStep"));
            try {
                return AgentWorkflowStep.valueOf(retryStep);
            } catch (RuntimeException ignored) {
                // Use deterministic fallback.
            }
        }
        return state.getArtifactId() == null
                ? AgentWorkflowStep.ENTITY_META : AgentWorkflowStep.ARTIFACT_READY;
    }

    private String visualizationObjectType(String value) {
        if (value.contains("大屏") || value.contains("看板")) {
            return "dashboard";
        }
        if (value.contains("应用") || value.contains("页面")) {
            return "data_application";
        }
        return "chart";
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
        for (String key : List.of(
                "configKind", "configIndex", "configType", "fileName",
                "overwrite", "dashboard", "menu", "request")) {
            if (metadata.containsKey(key)) {
                result.put(key, metadata.get(key));
            }
        }
        return result;
    }

    private boolean persistencePlanReady(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        Map<String, Object> candidate =
                map(state.getContext().get("persistenceCandidate"));
        if (!candidate.isEmpty()) {
            String configType = firstText(
                    text(metadata.get("configType")),
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
        Map<String, Object> dashboard =
                resourceRequest(metadata.get("dashboard"));
        Map<String, Object> menu =
                resourceRequest(metadata.get("menu"));
        boolean dashboardReady = dashboard.isEmpty()
                || (StringUtils.hasText(text(dashboard.get("name")))
                && StringUtils.hasText(text(dashboard.get("code"))));
        boolean menuReady = menu.isEmpty()
                || StringUtils.hasText(text(menu.get("name")));
        return dashboardReady
                && menuReady
                && (!candidate.isEmpty()
                || !dashboard.isEmpty()
                || !menu.isEmpty());
    }

    private Map<String, Object> resourceRequest(Object value) {
        Map<String, Object> resource = map(value);
        Map<String, Object> request = map(resource.get("request"));
        return request.isEmpty() ? resource : request;
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
