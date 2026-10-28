package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class DataPushWorkflowDefinition implements WorkflowDefinition {

    private static final Set<String> PART_TYPES = Set.of(
            "info-steps",
            "code",
            "data-push-service-decision",
            "confirm",
            "data-push-service-record");

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
        if ("blocked".equals(part.getStatus())
                || "blocked".equals(text(metadata.get("validationStatus")))) {
            return AgentWorkflowStep.BLOCKED;
        }
        if ("info-steps".equals(part.getType())) {
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("data-push-service-decision".equals(part.getType())
                || isPushConfirm(part, metadata)) {
            if (missingMeta(metadata)) {
                metadata.put("validationStatus", "blocked");
                return AgentWorkflowStep.BLOCKED;
            }
            return pushPlanReady(state, metadata)
                    ? AgentWorkflowStep.PUSH_PLAN_CONFIRMATION
                    : AgentWorkflowStep.BLOCKED;
        }
        if ("data-push-service-record".equals(part.getType())) {
            return "running".equalsIgnoreCase(text(metadata.get("status")))
                    && hasEvidence(state, "push_task_list_by_source_mark")
                    && hasEvidence(state, "push_task_get_log")
                    ? AgentWorkflowStep.PUSH_VERIFIED
                    : AgentWorkflowStep.BLOCKED;
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
            return switch (current) {
                case PUSH_PLAN_CONFIRMATION -> AgentWorkflowStep.PUSH_FORMAT_CHECK;
                case PUSH_CONFLICT_CONFIRMATION -> AgentWorkflowStep.PUSH_EXECUTING;
                default -> current;
            };
        }
        if ("revise".equals(action)) {
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("retry".equals(action)) {
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
            case PUSH_FORMAT_CHECK -> continuation(
                    "已批准数据推送候选配置。",
                    "严格使用工作流锁定的完整配置，不得重新生成。先再次只读校验 metaRef，"
                            + "然后调用 push_task_detect_format，按锁定 sourceMark 调用 "
                            + "push_task_list_by_source_mark；无任务时 create_and_start，"
                            + "创建返回后无论成功失败均再次查询，取得唯一 taskId 后读取 system 日志。"
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
            rememberCandidate(context, part.getContent());
            return;
        }
        if ("data-push-service-decision".equals(part.getType())
                || "confirm".equals(part.getType())) {
            copy(metadata, context, "sourceMark");
            copy(metadata, context, "request");
            copy(metadata, context, "metaRef");
            String planId = firstText(text(metadata.get("planId")), "plan:" + UUID.randomUUID());
            metadata.put("planId", planId);
            context.put("planId", planId);
            Map<String, Object> candidate = map(context.get("candidate"));
            if (!candidate.isEmpty()) {
                metadata.put("candidateDigest", candidate.get("digest"));
                metadata.put("artifactId", candidate.get("artifactId"));
            }
            metadata.put("source", "workflow");
        }
        if ("data-push-service-record".equals(part.getType())) {
            metadata.put("source", "workflow");
            metadata.put("validationStatus",
                    state.getStep() == AgentWorkflowStep.BLOCKED ? "blocked" : "success");
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

    private boolean pushPlanReady(AgentWorkflowState state, Map<String, Object> metadata) {
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        Map<String, Object> metaRef = firstNonEmpty(
                map(metadata.get("metaRef")),
                map(state.getContext().get("metaRef")));
        String sourceMark = firstText(
                text(state.getContext().get("sourceMark")),
                text(metadata.get("sourceMark")));
        return StringUtils.hasText(text(candidate.get("content")))
                && StringUtils.hasText(text(candidate.get("digest")))
                && StringUtils.hasText(sourceMark)
                && StringUtils.hasText(text(metaRef.get("fileName")))
                && StringUtils.hasText(text(metaRef.get("contentDigest")))
                && hasEvidence(state, "config_tree")
                && hasEvidence(state, "config_read");
    }

    private boolean missingMeta(Map<String, Object> metadata) {
        String kind = text(metadata.get("kind")).toLowerCase().replace('-', '_');
        return Set.of("missing_meta", "meta_missing", "meta_not_found").contains(kind);
    }

    private boolean isPushConfirm(ChatMessagePart part, Map<String, Object> metadata) {
        return "confirm".equals(part.getType())
                && ("data_push.confirm_plan".equals(text(metadata.get("action")))
                || "push_task".equals(text(metadata.get("configKind"))));
    }

    private boolean hasEvidence(AgentWorkflowState state, String tool) {
        return state.getEvidenceRefs() != null
                && state.getEvidenceRefs().stream().anyMatch(item ->
                tool.equals(text(item.get("tool")))
                        && "succeeded".equals(text(item.get("status"))));
    }

    private AgentWorkflowStep retryStep(AgentWorkflowState state) {
        List<Map<String, Object>> failures = state.getFailures();
        if (failures != null && !failures.isEmpty()) {
            String retryStep = text(failures.get(failures.size() - 1).get("retryStep"));
            try {
                AgentWorkflowStep resolved = AgentWorkflowStep.valueOf(retryStep);
                if (Set.of(
                        AgentWorkflowStep.PUSH_INPUT_COLLECTION,
                        AgentWorkflowStep.PUSH_FORMAT_CHECK,
                        AgentWorkflowStep.PUSH_EXECUTING,
                        AgentWorkflowStep.PUSH_DIAGNOSIS,
                        AgentWorkflowStep.PUSH_REPAIR).contains(resolved)) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
                // Use the push-safe fallback below.
            }
        }
        return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
    }

    private void rememberCandidate(Map<String, Object> context, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        candidate.put("digest", digest(content));
        candidate.put("artifactId", "push-task:" + digest(content));
        context.put("candidate", candidate);
    }

    private Map<String, Object> firstNonEmpty(
            Map<String, Object> first,
            Map<String, Object> second) {
        return first.isEmpty() ? second : first;
    }

    private Map<String, Object> continuation(String display, String request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display", display);
        if (StringUtils.hasText(request)) {
            result.put("request", request);
        }
        return result;
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
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
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
