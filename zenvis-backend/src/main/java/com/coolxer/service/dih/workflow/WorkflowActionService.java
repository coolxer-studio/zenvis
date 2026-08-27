package com.coolxer.service.dih.workflow;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.WorkflowActionDto;
import com.coolxer.model.dih.dto.WorkflowTelemetryDto;
import com.coolxer.model.dih.vo.WorkflowActionVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkflowActionService {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "submit", "approve", "reject", "revise", "retry", "add_to_library");

    private static final Set<String> SAFE_CHART_TOOLS = Set.of(
            "entity_overview",
            "entity_summary",
            "entity_trend",
            "entity_distribution",
            "entity_aggregate",
            "entity_histogram",
            "entity_scatter",
            "entity_relations",
            "entity_relation_timeline",
            "entity_list",
            "retrieval_search"
    );

    private final ChatSessionService chatSessionService;
    private final WorkflowStateStore stateStore;

    @Autowired(required = false)
    private WorkflowMetrics workflowMetrics;

    @Autowired(required = false)
    private WorkflowOrchestrator workflowOrchestrator;

    public WorkflowActionService(
            ChatSessionService chatSessionService,
            WorkflowStateStore stateStore) {
        this.chatSessionService = chatSessionService;
        this.stateStore = stateStore;
    }

    public WorkflowActionVo handle(WorkflowActionDto request, User currentUser) {
        require(request != null, "工作流动作请求不能为空");
        require(SUPPORTED_ACTIONS.contains(request.getAction()), "不支持的工作流动作");
        ChatSession chatSession = chatSessionService.getChatSessionBySessionId(
                request.getChatId(), currentUser);
        require(chatSession != null, "会话不存在或无权访问");

        List<Message> messages = parseMessages(chatSession.getMessages());
        LocatedPart located = locateExact(messages, request.getMessageId(), request.getPartId());
        require(located != null, "消息或工作流卡片不存在");
        Map<String, Object> metadata = located.part().getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(located.part().getMetadata());
        located.part().setMetadata(metadata);
        require(Objects.equals(request.getWorkflowId(), text(metadata.get("workflowId"))),
                "工作流与卡片不匹配");

        AgentWorkflowState state = stateStore.load(chatSession, request.getWorkflowId())
                .orElseThrow(() -> invalid("工作流不存在或已过期"));
        WorkflowDefinition definition = workflowDefinition(state.getAgentType());
        require(definition != null, "当前智能体没有可用的共享工作流定义");
        if (state.getContext() == null) {
            state.setContext(new LinkedHashMap<>());
        }
        require(Objects.equals(state.getPartId(), request.getPartId()),
                "该卡片已不是工作流的当前可操作卡片");
        if (metadata.get("stateRevision") instanceof Number revision) {
            require(revision.longValue() == state.getStateRevision(),
                    "工作流卡片已经过期，请使用最新卡片");
        }
        boolean cardAllows = isAllowed(metadata, request.getAction());
        boolean stateAllows = definition.isAllowedForState(
                state.getStep(), request.getAction());
        if (!cardAllows || !stateAllows) {
            if (workflowMetrics != null) {
                workflowMetrics.invalidTransition(state.getStep(), request.getAction());
            }
            throw invalid(cardAllows
                    ? "动作与服务端工作流状态不匹配"
                    : "当前状态不允许执行该动作");
        }

        if ("submit".equals(request.getAction())) {
            validateAnswers(state, metadata, request.getAnswers());
            definition.rememberAnswers(state, request.getAnswers());
        }
        String validationError = definition.validateAction(
                state, located.part(), metadata, request.getAction());
        require(!StringUtils.hasText(validationError), validationError);
        if ("revise".equals(request.getAction())) {
            require(StringUtils.hasText(request.getRevision()), "请提供调整要求");
            state.getContext().put("revision", request.getRevision().trim());
        }

        Map<String, Object> continuation;
        if ("add_to_library".equals(request.getAction())) {
            require(DataVisualizationAgent.AGENT_TYPE.equals(state.getAgentType()),
                    "只有数据可视化工作流支持加入图表库");
            addChartToLibrary(chatSession, state, located.part(), metadata);
            continuation = Map.of();
        } else {
            AgentWorkflowStep next = definition.transition(
                    state, request.getAction());
            state.setStep(next);
            state.setStatus(terminalStatus(next));
            continuation = definition.continuation(
                    state, request.getAction());
        }

        String partStatus = partStatus(request.getAction());
        located.part().setStatus(partStatus);
        metadata.put("validationStatus",
                "reject".equals(request.getAction()) ? "cancelled" : "success");
        state.setMessageId(request.getMessageId());
        state.setPartId(request.getPartId());
        state.setStateRevision(state.getStateRevision() + 1);
        state.setUpdatedAt(Instant.now().toString());
        stateStore.upsert(chatSession, state);
        chatSession.setMessages(JacksonUtil.toJson(messages));
        chatSessionService.updateWorkflowState(
                (long) chatSession.getId(),
                chatSession.getMessages(),
                chatSession.getExtraData(),
                currentUser);

        return new WorkflowActionVo(
                true,
                state.getWorkflowId(),
                state.getStep().name(),
                partStatus,
                continuation,
                "retry".equals(request.getAction())
                        || state.getStep() == AgentWorkflowStep.BLOCKED,
                chatSession.getExtraData());
    }

    private WorkflowDefinition workflowDefinition(String agentType) {
        if (workflowOrchestrator != null) {
            WorkflowDefinition definition =
                    workflowOrchestrator.definition(agentType);
            if (definition != null) {
                return definition;
            }
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(agentType)) {
            return new DataVisualizationWorkflowDefinition();
        }
        if (DataAccessAgent.AGENT_TYPE.equals(agentType)) {
            return new DataAccessWorkflowDefinition();
        }
        return null;
    }

    private String terminalStatus(AgentWorkflowStep step) {
        return switch (step) {
            case VERIFIED, META_VERIFIED, PUSH_VERIFIED, COMPLETED ->
                    "completed";
            case CANCELLED -> "cancelled";
            case BLOCKED -> "blocked";
            default -> "active";
        };
    }

    public void recordTelemetry(WorkflowTelemetryDto request, User currentUser) {
        require(request != null, "工作流遥测请求不能为空");
        require("chart_render_failed".equals(request.getEvent()),
                "不支持的工作流遥测事件");
        ChatSession chatSession = chatSessionService.getChatSessionBySessionId(
                request.getChatId(), currentUser);
        require(chatSession != null, "会话不存在或无权访问");
        AgentWorkflowState state = stateStore.load(chatSession, request.getWorkflowId())
                .orElseThrow(() -> invalid("工作流不存在或已过期"));
        require(DataVisualizationAgent.AGENT_TYPE.equals(state.getAgentType()),
                "当前工作流不属于数据可视化智能体");
        require(state.getStep() == AgentWorkflowStep.ARTIFACT_READY,
                "当前工作流阶段没有可渲染图表");
        List<Map<String, Object>> failures = state.getFailures() == null
                ? new ArrayList<>() : new ArrayList<>(state.getFailures());
        if (failures.size() >= 50) {
            failures.remove(0);
        }
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("stage", AgentWorkflowStep.ARTIFACT_READY.name());
        failure.put("retryStep", AgentWorkflowStep.ARTIFACT_READY.name());
        String renderError = firstText(
                request.getDetail(), "ECharts 客户端渲染失败");
        failure.put("error", renderError.length() <= 1000
                ? renderError : renderError.substring(0, 1000) + "…");
        failure.put("retryable", true);
        failure.put("preservedArtifactId", state.getArtifactId());
        failure.put("occurredAt", Instant.now().toString());
        failures.add(failure);
        state.setFailures(failures);
        state.setUpdatedAt(Instant.now().toString());
        stateStore.upsert(chatSession, state);
        chatSessionService.updateWorkflowState(
                (long) chatSession.getId(),
                chatSession.getMessages(),
                chatSession.getExtraData(),
                currentUser);
        if (workflowMetrics != null) {
            workflowMetrics.chartRenderFailure();
        }
    }

    private void validateAnswers(
            AgentWorkflowState state,
            Map<String, Object> metadata,
            List<Map<String, Object>> answers) {
        List<Map<String, Object>> steps = listOfMaps(metadata.get("steps"));
        Map<String, Map<String, Object>> answersById = new LinkedHashMap<>();
        if (answers != null) {
            for (Map<String, Object> answer : answers) {
                String id = text(answer.get("id"));
                require(StringUtils.hasText(id), "提交答案缺少步骤标识");
                require(!answersById.containsKey(id), "同一步骤不能重复提交");
                answersById.put(id, answer);
            }
        }
        for (Map<String, Object> step : steps) {
            String id = text(step.get("id"));
            boolean required = Boolean.TRUE.equals(step.get("required"));
            Map<String, Object> answer = answersById.get(id);
            String value = answer == null ? "" : text(answer.get("value"));
            if (required) {
                require(StringUtils.hasText(value), "必填项未提交：" + text(step.get("title")));
            }
            if (Boolean.TRUE.equals(step.get("strictOptions")) && StringUtils.hasText(value)) {
                Set<String> allowedValues = persistedStrictValues(
                        state, id, step.get("suggestions"));
                require(allowedValues.contains(value),
                        "提交值不在当前 MCP 返回的候选项中：" + value);
            }
        }
        require(answersById.keySet().stream()
                        .allMatch(id -> steps.stream().anyMatch(step ->
                                Objects.equals(id, text(step.get("id"))))),
                "提交内容包含当前卡片不存在的步骤");
    }

    private Set<String> persistedStrictValues(
            AgentWorkflowState state,
            String stepId,
            Object cardSuggestions) {
        Set<String> cardValues = optionValues(cardSuggestions);
        Map<String, Object> persisted =
                map(state.getContext().get("strictOptions"));
        Set<String> persistedValues = new LinkedHashSet<>();
        for (Object value : list(persisted.get(stepId))) {
            if (StringUtils.hasText(text(value))) {
                persistedValues.add(text(value));
            }
        }
        require(!persistedValues.isEmpty(),
                "服务端缺少该严格选项的 MCP 候选快照，请重新查询");
        require(cardValues.equals(persistedValues),
                "卡片候选项已变更，请重新查询 Meta");
        return persistedValues;
    }

    private Set<String> optionValues(Object suggestions) {
        Set<String> values = new LinkedHashSet<>();
        for (Object rawOption : list(suggestions)) {
            if (rawOption instanceof String option) {
                values.add(option);
            } else if (rawOption instanceof Map<?, ?> option) {
                values.add(text(option.get("value")));
            }
        }
        values.remove("");
        return values;
    }

    private void addChartToLibrary(
            ChatSession chatSession,
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        require(state.getStep() == AgentWorkflowStep.ARTIFACT_READY,
                "只有已生成的图表产物可以加入图表库");
        require("visualization-chart-preview".equals(part.getType()),
                "当前卡片不是图表预览产物");
        require("success".equals(text(metadata.get("validationStatus"))),
                "图表产物尚未通过真实查询校验");
        require("workflow".equals(text(metadata.get("source"))),
                "普通图表库只接受共享工作流生成的真实查询产物");
        String artifactId = firstText(
                text(metadata.get("artifactId")),
                state.getArtifactId());
        require(StringUtils.hasText(artifactId), "图表产物缺少 artifactId");
        require(StringUtils.hasText(text(metadata.get("planId"))), "图表产物缺少 planId");
        Map<String, Object> query = map(metadata.get("query"));
        require(SAFE_CHART_TOOLS.contains(text(query.get("tool"))),
                "图表产物使用了非白名单查询工具");
        require(!map(query.get("request")).isEmpty(), "图表产物缺少安全查询参数");
        require(!map(metadata.get("echartsOption")).isEmpty(),
                "图表产物缺少可渲染的 ECharts 配置");

        Map<String, Object> root = stateStore.parse(chatSession.getExtraData());
        Map<String, Object> visualization = map(root.get("dataVisualization"));
        List<Map<String, Object>> records =
                new ArrayList<>(listOfMaps(visualization.get("chartLibrary")));
        Map<String, Object> record = new LinkedHashMap<>(metadata);
        record.put("id", artifactId);
        record.put("artifactId", artifactId);
        record.put("name", firstText(
                text(metadata.get("title")),
                part.getTitle(),
                "临时可视化图表"));
        record.put("description", firstText(
                text(metadata.get("description")),
                part.getContent()));
        record.put("status", "temporary");
        record.put("source", "workflow");
        record.put("addedAt", Instant.now().toString());
        records.removeIf(item -> artifactId.equals(firstText(
                text(item.get("artifactId")), text(item.get("id")))));
        records.add(record);
        visualization.put("chartLibrary", records);
        root.put("dataVisualization", visualization);
        chatSession.setExtraData(JacksonUtil.toJson(root));
        state.setArtifactId(artifactId);
        state.getContext().put("libraryArtifactId", artifactId);
    }

    private boolean isAllowed(Map<String, Object> metadata, String action) {
        return list(metadata.get("allowedActions")).stream()
                .map(this::text)
                .anyMatch(action::equals);
    }

    private LocatedPart locateExact(
            List<Message> messages,
            String messageId,
            String partId) {
        for (Message message : messages) {
            if (!Objects.equals(message.getId(), messageId) || message.getParts() == null) {
                continue;
            }
            for (ChatMessagePart part : message.getParts()) {
                if (Objects.equals(part.getId(), partId)) {
                    return new LocatedPart(message, part);
                }
            }
        }
        return null;
    }

    private List<Message> parseMessages(String raw) {
        try {
            return new ArrayList<>(JacksonUtil.toList(
                    raw, new TypeReference<List<Message>>() {
                    }));
        } catch (RuntimeException e) {
            throw invalid("会话消息无法解析");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> mapped = map(item);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return result;
    }

    private List<?> list(Object value) {
        return value instanceof List<?> raw ? raw : List.of();
    }

    private String partStatus(String action) {
        return switch (action) {
            case "submit" -> "submitted";
            case "approve" -> "approved";
            case "reject" -> "rejected";
            case "revise" -> "revise";
            case "retry" -> "pending";
            case "add_to_library" -> "added";
            default -> "pending";
        };
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

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(400, message);
    }

    private record LocatedPart(Message message, ChatMessagePart part) {
    }
}
