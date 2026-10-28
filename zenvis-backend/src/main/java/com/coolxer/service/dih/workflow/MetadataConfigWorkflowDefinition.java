package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
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
public class MetadataConfigWorkflowDefinition implements WorkflowDefinition {

    private static final Set<String> PART_TYPES = Set.of(
            "info-steps",
            "config",
            "metadata-config-decision",
            "confirm",
            "metadata-config-record");

    @Override
    public String agentType() {
        return MetadataConfigAgent.AGENT_TYPE;
    }

    @Override
    public String workflowType() {
        return "metadata_config";
    }

    @Override
    public String defaultObjectType() {
        return "meta_config";
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
            return AgentWorkflowStep.METADATA_INPUT_COLLECTION;
        }
        if ("metadata-config-decision".equals(part.getType())
                || isMetaConfirm(part, metadata)) {
            if (!candidateReady(state)
                    || !hasEvidence(state, "config_tree")
                    || !safeMetaFileName(text(metadata.get("fileName")))) {
                return AgentWorkflowStep.BLOCKED;
            }
            boolean overwrite = Boolean.TRUE.equals(metadata.get("overwrite"))
                    || Boolean.TRUE.equals(state.getContext().get("overwriteRequired"));
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
        if ("metadata-config-record".equals(part.getType())) {
            return hasEvidence(state, "config_read")
                    && (hasEvidence(state, "config_apply")
                    || Boolean.TRUE.equals(state.getContext().get("candidateAlreadyApplied")))
                    ? AgentWorkflowStep.META_VERIFIED
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
            case METADATA_INPUT_COLLECTION -> List.of("submit");
            case META_PLAN_CONFIRMATION, META_OVERWRITE_CONFIRMATION ->
                    List.of("approve", "reject", "revise");
            case BLOCKED -> List.of("retry", "revise");
            default -> List.of();
        };
    }

    @Override
    public boolean isAllowedForState(AgentWorkflowStep step, String action) {
        return switch (action) {
            case "submit" -> step == AgentWorkflowStep.METADATA_INPUT_COLLECTION;
            case "approve", "reject" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION).contains(step);
            case "revise" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION,
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
            return AgentWorkflowStep.META_DISCOVERY;
        }
        if ("approve".equals(action)) {
            return switch (current) {
                case META_PLAN_CONFIRMATION -> AgentWorkflowStep.META_PREWRITE_CHECK;
                case META_OVERWRITE_CONFIRMATION -> AgentWorkflowStep.META_APPLY;
                default -> current;
            };
        }
        if ("revise".equals(action)) {
            return AgentWorkflowStep.META_DISCOVERY;
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
                    "进入元数据配置 META_DISCOVERY 阶段。必须先调用 "
                            + "config_tree(type=\"meta\")，再基于真实结果生成完整 Meta JSON "
                            + "和 zenvis:metadata-config-decision 确认卡。");
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
            case CANCELLED -> continuation("已取消当前元数据配置流程。", "");
            default -> "retry".equals(action)
                    ? continuation("正在重试元数据配置阶段。", "请从失败账本指定的安全检查点继续。")
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
            rememberCandidate(context, part.getContent());
            return;
        }
        if ("metadata-config-decision".equals(part.getType())
                || "confirm".equals(part.getType())) {
            copy(metadata, context, "fileName");
            copy(metadata, context, "overwrite");
            String existingPlanId = text(context.get("planId"));
            String planId = Boolean.TRUE.equals(context.get("overwriteRequired"))
                    && StringUtils.hasText(existingPlanId)
                    ? existingPlanId
                    : firstText(text(metadata.get("planId")), "plan:" + UUID.randomUUID());
            metadata.put("planId", planId);
            context.put("planId", planId);
            Map<String, Object> candidate = map(context.get("candidate"));
            if (!candidate.isEmpty()) {
                metadata.put("candidateDigest", candidate.get("digest"));
                metadata.put("artifactId", candidate.get("artifactId"));
            }
            metadata.put("source", "workflow");
        }
        if ("metadata-config-record".equals(part.getType())) {
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

    private boolean candidateReady(AgentWorkflowState state) {
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        return StringUtils.hasText(text(candidate.get("content")))
                && StringUtils.hasText(text(candidate.get("digest")));
    }

    private boolean isMetaConfirm(ChatMessagePart part, Map<String, Object> metadata) {
        return "confirm".equals(part.getType())
                && ("metadata_config.confirm_plan".equals(text(metadata.get("action")))
                || "meta".equals(text(metadata.get("configKind"))));
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
                AgentWorkflowStep resolved = AgentWorkflowStep.valueOf(retryStep);
                if (Set.of(
                        AgentWorkflowStep.META_DISCOVERY,
                        AgentWorkflowStep.META_PREWRITE_CHECK,
                        AgentWorkflowStep.META_APPLY).contains(resolved)) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
                // Use the metadata-safe fallback below.
            }
        }
        return AgentWorkflowStep.META_DISCOVERY;
    }

    private void rememberCandidate(Map<String, Object> context, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        candidate.put("digest", digest(content));
        candidate.put("artifactId", "meta-config:" + digest(content));
        context.put("candidate", candidate);
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
