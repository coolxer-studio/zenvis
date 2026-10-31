package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DataPushWorkflowDefinition implements WorkflowDefinition {

    private static final Set<String> PART_TYPES = Set.of(
            "info-steps",
            "code",
            "data-push-service-decision",
            "confirm");

    @Override
    public String agentType() {
        return DataPushServiceAgent.AGENT_TYPE;
    }

    @Override
    public String workflowType() {
        return "data_push";
    }

    @Override
    public String defaultObjectType() {
        return "push_task";
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
        if ("info-steps".equals(part.getType())) {
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("data-push-service-decision".equals(part.getType())
                || isPushConfirm(part, metadata)) {
            DataPushPlanSnapshot snapshot = snapshotForGate(state, metadata);
            applySnapshotMetadata(snapshot, metadata);
            metadata.remove("blockingReasons");
            metadata.remove("validationMessage");
            return AgentWorkflowStep.PUSH_PLAN_CONFIRMATION;
        }
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        return null;
    }

    @Override
    public String resolveObjectType(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        return defaultObjectType();
    }

    @Override
    public List<String> allowedActions(
            AgentWorkflowStep step,
            Map<String, Object> metadata) {
        return switch (step) {
            case PUSH_INPUT_COLLECTION -> List.of("submit");
            case PUSH_PLAN_CONFIRMATION, PUSH_CONFLICT_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> step == AgentWorkflowStep.PUSH_INPUT_COLLECTION;
            case "approve", "reject" -> Set.of(
                    AgentWorkflowStep.PUSH_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PUSH_CONFLICT_CONFIRMATION).contains(step);
            case "revise" -> Set.of(
                    AgentWorkflowStep.PUSH_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PUSH_CONFLICT_CONFIRMATION,
                    AgentWorkflowStep.BLOCKED).contains(step);
            case "retry" -> step == AgentWorkflowStep.BLOCKED;
            default -> false;
        };
    }

    @Override
    public AgentWorkflowStep transition(AgentWorkflowState state, String action) {
        AgentWorkflowStep current = state.getStep();
        if ("reject".equals(action)) {
            return AgentWorkflowStep.CANCELLED;
        }
        if ("submit".equals(action)) {
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("approve".equals(action)) {
            state.getContext().remove("revalidateOnly");
            return switch (current) {
                case PUSH_PLAN_CONFIRMATION -> AgentWorkflowStep.PUSH_CONFIG_VALIDATION;
                case PUSH_CONFLICT_CONFIRMATION -> AgentWorkflowStep.PUSH_EXECUTING;
                default -> current;
            };
        }
        if ("revise".equals(action)) {
            state.getContext().remove("revalidateOnly");
            unlockForRevision(state);
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("retry".equals(action)) {
            if (current == AgentWorkflowStep.BLOCKED && hasLockedPlan(state)) {
                state.getContext().put("revalidateOnly", true);
                return AgentWorkflowStep.PUSH_CONFIG_VALIDATION;
            }
            return retryStep(state);
        }
        return current;
    }

    @Override
    public Map<String, Object> continuation(
            AgentWorkflowState state,
            String action) {
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        String content = text(candidate.get("content"));
        return switch (state.getStep()) {
            case PUSH_INPUT_COLLECTION -> continuation(
                    "已收到数据推送补充信息。",
                    "请基于已确认信息生成完整 PushTask/Vectum 配置，输出完整配置和 "
                            + "action=data_push.confirm_plan 的 zenvis:data-push-service-decision；"
                            + "确认前不得创建或启动任务。");
            case PUSH_CONFIG_VALIDATION -> Boolean.TRUE.equals(
                    state.getContext().get("revalidateOnly"))
                    ? continuation(
                    "正在重新校验锁定的数据推送候选。",
                    "使用服务端锁定配置重新读取 Meta 并调用 push_task_validate_config；"
                            + "本轮只重新校验并生成新的确认卡，不得创建或启动任务。\n候选摘要："
                            + text(candidate.get("digest")))
                    : continuation(
                    "已确认数据推送方案，正在执行最终门禁。",
                    "平台将重新校验锁定候选、Meta 摘要与 Vector 原生 validate/test；"
                            + "通过后才允许创建或更新任务。\n候选摘要："
                            + text(candidate.get("digest")));
            case PUSH_FORMAT_CHECK -> continuation(
                    "已批准数据推送候选配置。",
                    "平台已重新读取锁定 Meta 并完成执行前 Vector 校验，"
                            + "严格使用工作流锁定的完整配置和 format，不得重新生成或检测格式。"
                            + "平台会按 SYSTEM + mark 读回任务，无任务时 create_and_start，"
                            + "已存在且配置一致时直接复用；随后用返回的 taskId 调用 "
                            + "push_task_get 和 push_task_diagnose。"
                            + "\n候选摘要：" + text(candidate.get("digest"))
                            + "\n完整配置：\n" + content);
            case CANCELLED -> continuation("已取消当前数据推送服务流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试数据推送服务阶段。",
                    "请从失败账本指定的安全检查点继续；重试前必须先按 sourceMark 读回现状。")
                    : Map.of();
        };
    }

    @Override
    public void updateContext(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext();
        if ("code".equals(part.getType())
                && Set.of("toml", "yaml", "yml", "json", "vector")
                .contains(text(part.getLanguage()).toLowerCase())
                && StringUtils.hasText(part.getContent())) {
            DataPushPlanSnapshot snapshot = DataPushPlanSnapshot.load(context);
            if (!snapshot.sealed()) {
                String content = part.getContent();
                snapshot = snapshot.withCandidate(
                        content,
                        DataPushPlanSnapshot.digest(content),
                        part.getLanguage(),
                        "push-task:" + DataPushPlanSnapshot.digest(content));
                snapshot.persist(context);
            }
            return;
        }
        if ("data-push-service-decision".equals(part.getType())
                || "confirm".equals(part.getType())) {
            DataPushPlanSnapshot snapshot = DataPushPlanSnapshot.load(context);
            if (!StringUtils.hasText(snapshot.sourceMark())
                    && StringUtils.hasText(state.getWorkflowId())) {
                snapshot = snapshot.withMetaRef(
                        snapshot.metaRef(), "data-push:" + state.getWorkflowId());
            }
            snapshot = withCardMetaRef(snapshot, metadata);
            if (state.getStep() != AgentWorkflowStep.BLOCKED) {
                Map<String, Object> request = map(metadata.get("request"));
                if (!request.isEmpty() && !snapshot.hasRequest()) {
                    snapshot = snapshot.withRequest(request);
                }
            }
            snapshot.persist(context);
            applySnapshotMetadata(snapshot, metadata);
            metadata.put("source", "workflow");
        }
    }

    @Override
    public void rememberAnswers(
            AgentWorkflowState state,
            List<Map<String, Object>> answers) {
        List<Map<String, Object>> safeAnswers = answers == null
                ? List.of()
                : answers.stream()
                .map(LinkedHashMap::new)
                .map(item -> (Map<String, Object>) item)
                .toList();
        state.getContext().put("answers", safeAnswers);
        state.getContext().remove("revision");
    }

    private DataPushPlanSnapshot snapshotForGate(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : state.getContext();
        DataPushPlanSnapshot snapshot = DataPushPlanSnapshot.load(context);
        if (!StringUtils.hasText(snapshot.sourceMark())
                && StringUtils.hasText(state.getWorkflowId())) {
            snapshot = snapshot.withMetaRef(
                    snapshot.metaRef(), "data-push:" + state.getWorkflowId());
        }
        snapshot = withCardMetaRef(snapshot, metadata);
        Map<String, Object> cardRequest = map(metadata.get("request"));
        if (!snapshot.hasRequest() && !cardRequest.isEmpty()) {
            snapshot = snapshot.withRequest(cardRequest);
        }
        snapshot = withValidationEvidence(snapshot, state.getEvidenceRefs());
        snapshot.persist(context);
        return snapshot;
    }

    private DataPushPlanSnapshot withCardMetaRef(
            DataPushPlanSnapshot snapshot,
            Map<String, Object> metadata) {
        if (snapshot.hasMetaRef()) {
            return snapshot;
        }
        Map<String, Object> cardMetaRef = map(metadata.get("metaRef"));
        if (!StringUtils.hasText(text(cardMetaRef.get("fileName")))
                || !StringUtils.hasText(text(cardMetaRef.get("contentDigest")))) {
            return snapshot;
        }
        return snapshot.withMetaRef(cardMetaRef, snapshot.sourceMark());
    }

    private boolean hasLockedPlan(AgentWorkflowState state) {
        Map<String, Object> context = state.getContext() == null
                ? Map.of() : state.getContext();
        return DataPushPlanSnapshot.load(context).complete();
    }

    private void unlockForRevision(AgentWorkflowState state) {
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : state.getContext();
        DataPushPlanSnapshot snapshot = DataPushPlanSnapshot.load(context);
        snapshot.withoutValidation().withoutRequest().persist(context);
        state.setContext(context);
    }

    private void applySnapshotMetadata(DataPushPlanSnapshot snapshot,
                                       Map<String, Object> metadata) {
        metadata.remove("candidateDigest");
        metadata.remove("artifactId");
        metadata.remove("sourceMark");
        metadata.remove("metaRef");
        metadata.remove("validation");
        metadata.remove("validationDigest");
        metadata.remove("request");
        metadata.remove("format");
        if (StringUtils.hasText(snapshot.planId())) {
            metadata.put("planId", snapshot.planId());
        }
        if (StringUtils.hasText(snapshot.sourceMark())) {
            metadata.put("sourceMark", snapshot.sourceMark());
        }
        if (!snapshot.metaRef().isEmpty()) {
            metadata.put("metaRef", snapshot.metaRef());
        }
        if (!snapshot.candidate().isEmpty()) {
            metadata.put("candidateDigest", snapshot.candidate().get("digest"));
            metadata.put("artifactId", snapshot.candidate().get("artifactId"));
            metadata.put("format", snapshot.candidate().get("format"));
        }
        if (!snapshot.validation().isEmpty()) {
            metadata.put("validation", snapshot.validation());
            metadata.put("validationDigest",
                    snapshot.validation().get("validationDigest"));
        }
        if (!snapshot.request().isEmpty()) {
            metadata.put("request", snapshot.request());
        }
    }

    private DataPushPlanSnapshot withValidationEvidence(
            DataPushPlanSnapshot snapshot,
            List<Map<String, Object>> evidenceRefs) {
        Map<String, Object> latest = Map.of();
        Map<String, Object> matching = Map.of();
        if (evidenceRefs == null) {
            return snapshot;
        }
        for (Map<String, Object> item : evidenceRefs) {
            if (item != null
                    && "push_task_validate_config".equals(text(item.get("tool")))
                    && "succeeded".equals(text(item.get("status")))) {
                Map<String, Object> validation = map(item.get("validation"));
                if (!validation.isEmpty()) {
                    latest = validation;
                    if (matchesSnapshot(validation, snapshot)) {
                        matching = validation;
                    }
                }
            }
        }
        Map<String, Object> selected = matching.isEmpty() ? latest : matching;
        return selected.isEmpty() ? snapshot : snapshot.withValidation(selected);
    }

    private boolean matchesSnapshot(Map<String, Object> validation,
                                    DataPushPlanSnapshot snapshot) {
        String candidateDigest = text(snapshot.candidate().get("digest"));
        String metaDigest = text(snapshot.metaRef().get("contentDigest"));
        return StringUtils.hasText(candidateDigest)
                && candidateDigest.equals(text(validation.get("candidateDigest")))
                && StringUtils.hasText(metaDigest)
                && metaDigest.equals(text(validation.get("metaDigest")));
    }

    private boolean isPushConfirm(ChatMessagePart part, Map<String, Object> metadata) {
        return "confirm".equals(part.getType())
                && ("data_push.confirm_plan".equals(text(metadata.get("action")))
                || "push_task".equals(text(metadata.get("configKind"))));
    }

    private AgentWorkflowStep retryStep(AgentWorkflowState state) {
        List<Map<String, Object>> failures = state.getFailures();
        if (failures != null && !failures.isEmpty()) {
            String retryStep = text(failures.get(failures.size() - 1).get("retryStep"));
            try {
                AgentWorkflowStep resolved = AgentWorkflowStep.valueOf(retryStep);
                if (Set.of(
                        AgentWorkflowStep.PUSH_INPUT_COLLECTION,
                        AgentWorkflowStep.PUSH_CONFIG_VALIDATION,
                        AgentWorkflowStep.PUSH_FORMAT_CHECK,
                        AgentWorkflowStep.PUSH_EXECUTING,
                        AgentWorkflowStep.PUSH_DIAGNOSIS,
                        AgentWorkflowStep.PUSH_RUNTIME_DIAGNOSIS,
                        AgentWorkflowStep.PUSH_REPAIR).contains(resolved)) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
                // Use the push-safe fallback below.
            }
        }
        return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
    }

    private Map<String, Object> continuation(String display, String request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display", display);
        if (StringUtils.hasText(request)) {
            result.put("request", request);
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
