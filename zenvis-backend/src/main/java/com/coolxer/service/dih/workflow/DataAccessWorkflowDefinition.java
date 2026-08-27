package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.DataAccessAgent;
import org.springframework.stereotype.Component;
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

@Component
public class DataAccessWorkflowDefinition implements WorkflowDefinition {

    private static final Set<String> PART_TYPES = Set.of(
            "info-steps",
            "config",
            "code",
            "data-access-decision",
            "confirm",
            "metadata-config-record",
            "data-push-service-record");

    @Override
    public String agentType() {
        return DataAccessAgent.AGENT_TYPE;
    }

    @Override
    public String workflowType() {
        return "data_access";
    }

    @Override
    public String defaultObjectType() {
        return "data_access";
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
            return AgentWorkflowStep.ACCESS_INPUT_COLLECTION;
        }
        if ("data-access-decision".equals(part.getType())) {
            if ("push_task".equals(configKind(metadata))) {
                return pushPlanReady(state, metadata)
                        ? AgentWorkflowStep.PUSH_PLAN_CONFIRMATION
                        : AgentWorkflowStep.BLOCKED;
            }
            if (!candidateReady(state, "meta")
                    || !hasEvidence(state, "config_tree")
                    || !safeMetaFileName(text(metadata.get("fileName")))) {
                return AgentWorkflowStep.BLOCKED;
            }
            boolean overwrite = Boolean.TRUE.equals(metadata.get("overwrite"))
                    || Boolean.TRUE.equals(
                    state.getContext().get("overwriteRequired"));
            if (overwrite && (!Objects.equals(
                    text(state.getContext().get("planId")),
                    text(metadata.get("planId")))
                    || !Objects.equals(
                    text(map(state.getContext().get("candidate")).get("digest")),
                    text(metadata.get("candidateDigest"))))) {
                return AgentWorkflowStep.BLOCKED;
            }
            return overwrite
                    ? AgentWorkflowStep.META_OVERWRITE_CONFIRMATION
                    : AgentWorkflowStep.META_PLAN_CONFIRMATION;
        }
        if ("confirm".equals(part.getType())
                && ("data_access.confirm_push_plan".equals(text(metadata.get("action")))
                || "push_task".equals(configKind(metadata)))) {
            return pushPlanReady(state, metadata)
                    ? AgentWorkflowStep.PUSH_PLAN_CONFIRMATION
                    : AgentWorkflowStep.BLOCKED;
        }
        if ("metadata-config-record".equals(part.getType())) {
            return hasEvidence(state, "config_read")
                    && (hasEvidence(state, "config_apply")
                    || Boolean.TRUE.equals(state.getContext().get("candidateAlreadyApplied")))
                    ? AgentWorkflowStep.META_VERIFIED
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
        String kind = explicitConfigKind(metadata);
        if ("push_task".equals(kind)
                || "data-push-service-record".equals(part.getType())) {
            return "push_task";
        }
        if ("meta".equals(kind)
                || "metadata-config-record".equals(part.getType())) {
            return "meta_config";
        }
        return StringUtils.hasText(state.getObjectType())
                ? state.getObjectType() : defaultObjectType();
    }

    @Override
    public List<String> allowedActions(
            AgentWorkflowStep step,
            Map<String, Object> metadata) {
        return switch (step) {
            case ACCESS_INPUT_COLLECTION -> List.of("submit");
            case META_PLAN_CONFIRMATION, META_OVERWRITE_CONFIRMATION,
                    PUSH_PLAN_CONFIRMATION, PUSH_CONFLICT_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> step == AgentWorkflowStep.ACCESS_INPUT_COLLECTION;
            case "approve", "reject" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION,
                    AgentWorkflowStep.PUSH_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PUSH_CONFLICT_CONFIRMATION).contains(step);
            case "revise" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION,
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
            return "push_task".equals(state.getObjectType())
                    ? AgentWorkflowStep.PUSH_INPUT_COLLECTION
                    : AgentWorkflowStep.META_DISCOVERY;
        }
        if ("approve".equals(action)) {
            return switch (current) {
                case META_PLAN_CONFIRMATION -> AgentWorkflowStep.META_PREWRITE_CHECK;
                case META_OVERWRITE_CONFIRMATION -> AgentWorkflowStep.META_APPLY;
                case PUSH_PLAN_CONFIRMATION -> AgentWorkflowStep.PUSH_FORMAT_CHECK;
                case PUSH_CONFLICT_CONFIRMATION -> AgentWorkflowStep.PUSH_EXECUTING;
                default -> current;
            };
        }
        if ("revise".equals(action)) {
            return Set.of(
                    AgentWorkflowStep.PUSH_PLAN_CONFIRMATION,
                    AgentWorkflowStep.PUSH_CONFLICT_CONFIRMATION)
                    .contains(current)
                    ? AgentWorkflowStep.PUSH_INPUT_COLLECTION
                    : AgentWorkflowStep.META_DISCOVERY;
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
        String fileName = text(state.getContext().get("fileName"));
        return switch (state.getStep()) {
            case META_DISCOVERY -> continuation(
                    "已收到元数据定义，平台将检查现有 Meta 配置。",
                    "进入普通数据接入 META_DISCOVERY 阶段。必须先调用 "
                            + "config_tree(type=\"meta\")，再根据真实结果生成完整 Meta JSON 和确认卡。");
            case META_PREWRITE_CHECK -> continuation(
                    "已批准元数据候选配置。",
                    "严格应用工作流中已锁定的 Meta 候选，不得重新生成内容。"
                            + "\n目标文件：" + fileName
                            + "\n候选摘要：" + text(candidate.get("digest"))
                            + "\n完整候选配置：\n" + content
                            + "\n依次执行 config_tree；文件不存在时 config_add；"
                            + "内容不同时先输出覆盖确认卡；允许写入时 config_apply，"
                            + "随后必须 config_read 并做 JSON 语义一致校验。");
            case META_APPLY -> continuation(
                    "已确认覆盖现有元数据配置。",
                    "只允许使用已锁定候选调用 config_apply，随后调用 config_read；"
                            + "读回语义一致前不得输出成功记录。\n目标文件："
                            + fileName + "\n完整候选配置：\n" + content);
            case PUSH_INPUT_COLLECTION -> continuation(
                    "已收到数据推送补充信息。",
                    "请基于已确认信息生成完整 PushTask/Vectum 配置，"
                            + "输出配置和 action=data_access.confirm_push_plan 的确认卡；"
                            + "确认前不得创建或启动任务。");
            case PUSH_FORMAT_CHECK -> continuation(
                    "已批准数据推送候选配置。",
                    "严格使用工作流锁定的完整配置，不得重新生成。先调用 "
                            + "push_task_detect_format，再按锁定 sourceMark 调用 "
                            + "push_task_list_by_source_mark；无任务时 create_and_start，"
                            + "创建返回后无论成功失败均再次查询，取得唯一 taskId 后读取 system 日志。"
                            + "\n候选摘要：" + text(candidate.get("digest"))
                            + "\n完整配置：\n" + content);
            case CANCELLED -> continuation("已取消当前数据接入流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试失败阶段。", "请从失败账本指定的安全检查点继续。")
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
                && "meta-config".equals(text(metadata.get("configKind")))) {
            rememberCandidate(context, "meta", part.getContent());
            return;
        }
        if ("code".equals(part.getType())
                && Set.of("toml", "yaml", "yml", "vector")
                .contains(text(part.getLanguage()).toLowerCase())
                && StringUtils.hasText(part.getContent())) {
            rememberCandidate(context, "push_task", part.getContent());
            return;
        }
        if ("data-access-decision".equals(part.getType())
                || "confirm".equals(part.getType())) {
            copy(metadata, context, "fileName");
            copy(metadata, context, "overwrite");
            copy(metadata, context, "sourceMark");
            copy(metadata, context, "request");
            copy(metadata, context, "continuePushTask");
            String existingPlanId = text(context.get("planId"));
            String planId = Boolean.TRUE.equals(
                    context.get("overwriteRequired"))
                    && StringUtils.hasText(existingPlanId)
                    ? existingPlanId
                    : firstText(
                    text(metadata.get("planId")),
                    "plan:" + UUID.randomUUID());
            metadata.put("planId", planId);
            context.put("planId", planId);
            Map<String, Object> candidate = map(context.get("candidate"));
            if (!candidate.isEmpty()) {
                metadata.put("candidateDigest", candidate.get("digest"));
                metadata.put("artifactId", candidate.get("artifactId"));
            }
            metadata.put("source", "workflow");
        }
        if (Set.of("metadata-config-record", "data-push-service-record")
                .contains(part.getType())) {
            metadata.put("source", "workflow");
            metadata.put("validationStatus",
                    state.getStep() == AgentWorkflowStep.BLOCKED
                            ? "blocked" : "success");
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
        String combined = safeAnswers.stream()
                .map(answer -> text(answer.get("value")))
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase();
        boolean push = combined.contains("push")
                || combined.contains("vectum")
                || combined.contains("vector")
                || combined.contains("数据推送")
                || combined.contains("推送服务");
        boolean meta = combined.contains("meta")
                || combined.contains("元数据")
                || combined.contains("实体")
                || combined.contains("字段");
        boolean both = combined.contains("完整接入")
                || combined.contains("两者")
                || combined.contains("都要")
                || (push && meta);
        state.setObjectType(push && !both ? "push_task" : "meta_config");
        state.getContext().put("intentMode",
                both ? "meta_then_push" : push ? "push_task" : "meta");
        if (both) {
            state.getContext().put("continuePushTask", true);
        }
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
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        if (candidate.isEmpty()) {
            return "服务端缺少已锁定的数据接入候选配置";
        }
        if (!Objects.equals(
                text(candidate.get("digest")),
                text(metadata.get("candidateDigest")))) {
            return "数据接入候选配置在确认后被修改，必须重新生成确认卡";
        }
        if (!Objects.equals(
                text(state.getContext().get("planId")),
                text(metadata.get("planId")))) {
            return "数据接入方案标识不匹配";
        }
        return "";
    }

    @Override
    public void decorate(
            AgentWorkflowState state,
            ChatMessagePart part,
            Map<String, Object> metadata) {
        if (state.getStep() == AgentWorkflowStep.BLOCKED) {
            metadata.putIfAbsent(
                    "validationMessage",
                    "数据接入候选配置或所需 MCP 证据不完整，请重试当前阶段。");
        }
    }

    private void rememberCandidate(
            Map<String, Object> context,
            String kind,
            String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("kind", kind);
        candidate.put("content", content);
        candidate.put("digest", digest(content));
        candidate.put("artifactId", "artifact:" + UUID.randomUUID());
        context.put("candidate", candidate);
    }

    private boolean candidateReady(AgentWorkflowState state, String kind) {
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        return kind.equals(text(candidate.get("kind")))
                && StringUtils.hasText(text(candidate.get("content")))
                && StringUtils.hasText(text(candidate.get("digest")));
    }

    private boolean pushPlanReady(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        Map<String, Object> request = map(metadata.get("request"));
        String sourceMark = text(metadata.get("sourceMark"));
        return candidateReady(state, "push_task")
                && StringUtils.hasText(sourceMark)
                && StringUtils.hasText(text(request.get("name")))
                && (text(request.get("mark")).isEmpty()
                || Objects.equals(sourceMark, text(request.get("mark"))))
                && (text(request.get("source")).isEmpty()
                || "SYSTEM".equals(text(request.get("source"))));
    }

    private boolean safeMetaFileName(String fileName) {
        return StringUtils.hasText(fileName)
                && !fileName.contains("..")
                && fileName.matches("[A-Za-z0-9._-]+\\.json");
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
                AgentWorkflowStep resolved =
                        AgentWorkflowStep.valueOf(retryStep);
                if (Set.of(
                        AgentWorkflowStep.META_DISCOVERY,
                        AgentWorkflowStep.META_PREWRITE_CHECK,
                        AgentWorkflowStep.META_APPLY,
                        AgentWorkflowStep.PUSH_INPUT_COLLECTION,
                        AgentWorkflowStep.PUSH_FORMAT_CHECK,
                        AgentWorkflowStep.PUSH_EXECUTING)
                        .contains(resolved)) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
                // Use object-specific safe fallback.
            }
        }
        return "push_task".equals(state.getObjectType())
                ? AgentWorkflowStep.PUSH_INPUT_COLLECTION
                : AgentWorkflowStep.META_DISCOVERY;
    }

    private String configKind(Map<String, Object> metadata) {
        String kind = explicitConfigKind(metadata);
        return StringUtils.hasText(kind) ? kind : "meta";
    }

    private String explicitConfigKind(Map<String, Object> metadata) {
        String kind = text(metadata.get("configKind"))
                .toLowerCase()
                .replace('-', '_');
        if (Set.of("push_task", "pushtask", "vectum", "vector").contains(kind)) {
            return "push_task";
        }
        if (Set.of("meta", "meta_config").contains(kind)) {
            return "meta";
        }
        return "";
    }

    private Map<String, Object> continuation(String display, String request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("display", display);
        if (StringUtils.hasText(request)) {
            result.put("request", request);
        }
        return result;
    }

    private void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String key) {
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
