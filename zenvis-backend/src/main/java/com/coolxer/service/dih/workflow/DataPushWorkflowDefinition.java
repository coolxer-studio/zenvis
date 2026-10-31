package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if ("info-steps".equals(part.getType())) {
            return AgentWorkflowStep.PUSH_INPUT_COLLECTION;
        }
        if ("data-push-service-decision".equals(part.getType())
                || isPushConfirm(part, metadata)) {
            List<Map<String, Object>> blockingReasons = new ArrayList<>();
            if (missingMeta(metadata)) {
                addBlockingReason(blockingReasons,
                        "PUSH_MISSING_META", "meta",
                        "缺少可只读引用的已发布 Meta。",
                        "先完成并发布 Meta，再重新生成推送配置。");
            } else {
                blockingReasons.addAll(pushPlanBlockingReasons(state, metadata));
            }
            if (!blockingReasons.isEmpty()) {
                metadata.put("blockingReasons", blockingReasons);
                metadata.put("validationMessage", blockingMessage(blockingReasons));
                metadata.put("validationStatus", "blocked");
                return AgentWorkflowStep.BLOCKED;
            }
            metadata.remove("blockingReasons");
            metadata.remove("validationMessage");
            return AgentWorkflowStep.PUSH_PLAN_CONFIRMATION;
        }
        if ("data-push-service-record".equals(part.getType())) {
            return "running".equalsIgnoreCase(text(metadata.get("status")))
                    && hasEvidence(state, "push_task_validate_config")
                    && hasEvidence(state, "push_task_list")
                    && (hasEvidence(state, "push_task_diagnose")
                    || hasEvidence(state, "push_task_get_log"))
                    && verifiedRecordMatches(state, metadata)
                    ? AgentWorkflowStep.PUSH_VERIFIED
                    : AgentWorkflowStep.BLOCKED;
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
                    "严格使用工作流锁定的完整配置和 format，不得重新生成或检测格式。"
                            + "先再次只读校验 metaRef，再调用 push_task_validate_config；"
                            + "按 SYSTEM + mark 调用 push_task_list，无任务时 create_and_start，"
                            + "已存在时按需 update_and_restart；随后用返回的 taskId 调用 "
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
            rememberCandidate(context, part.getContent(), part.getLanguage());
            context.remove("validation");
            context.remove("validationStatus");
            context.remove("validationDigest");
            return;
        }
        if ("data-push-service-decision".equals(part.getType())
                || "confirm".equals(part.getType())) {
            if (state.getStep() != AgentWorkflowStep.BLOCKED) {
                copy(metadata, context, "sourceMark");
                copy(metadata, context, "request");
                copy(metadata, context, "metaRef");
                copy(metadata, context, "validation");
                copy(metadata, context, "validationStatus");
                copy(metadata, context, "validationDigest");
            } else {
                copyIfAbsent(metadata, context, "sourceMark");
                copyIfAbsent(metadata, context, "request");
                copyIfAbsent(metadata, context, "metaRef");
            }
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

    private List<Map<String, Object>> pushPlanBlockingReasons(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        Map<String, Object> lockedMetaRef = map(state.getContext().get("metaRef"));
        Map<String, Object> cardMetaRef = map(metadata.get("metaRef"));
        Map<String, Object> metaRef = lockedMetaRef.isEmpty()
                ? cardMetaRef : lockedMetaRef;
        String lockedSourceMark = text(state.getContext().get("sourceMark"));
        String cardSourceMark = text(metadata.get("sourceMark"));
        String sourceMark = firstText(
                lockedSourceMark,
                cardSourceMark);
        Map<String, Object> validation = firstNonEmpty(
                map(metadata.get("validation")),
                map(state.getContext().get("validation")));
        String candidateContent = text(candidate.get("content"));
        String candidateDigest = text(candidate.get("digest"));
        String validationCandidateDigest = text(validation.get("candidateDigest"));
        String validationMetaDigest = text(validation.get("metaDigest"));
        String validationDigest = firstText(
                text(validation.get("validationDigest")),
                text(metadata.get("validationDigest")));
        String metaFileName = text(metaRef.get("fileName"));
        String metaContentDigest = text(metaRef.get("contentDigest"));

        if (!StringUtils.hasText(candidateContent)) {
            addBlockingReason(reasons, "PUSH_CANDIDATE_MISSING", "candidate",
                    "平台没有锁定完整的推送候选配置。",
                    "重新生成完整配置并重新校验。");
        }
        if (!StringUtils.hasText(candidateDigest)) {
            addBlockingReason(reasons, "PUSH_CANDIDATE_DIGEST_MISSING", "candidate",
                    "平台锁定候选缺少配置摘要。",
                    "重新生成并校验完整配置。");
        } else if (StringUtils.hasText(candidateContent)
                && !candidateDigest.equals(digest(candidateContent))) {
            addDigestMismatch(reasons, "PUSH_CANDIDATE_CONTENT_DIGEST_MISMATCH",
                    "candidate",
                    "平台锁定候选摘要与完整配置正文不一致。",
                    "重新生成完整配置并重新校验。",
                    candidateDigest, digest(candidateContent));
        }
        if (validation.isEmpty()) {
            addBlockingReason(reasons, "PUSH_VALIDATION_MISSING", "validation",
                    "卡片缺少 Vector 配置校验结果。",
                    "按锁定候选重新执行 push_task_validate_config。");
        } else if (Boolean.TRUE.equals(validation.get("blocked"))) {
            addBlockingReason(reasons, "PUSH_VALIDATION_UNAVAILABLE", "validation",
                    "Vector 配置校验服务不可用，平台按 fail-closed 处理。",
                    "恢复校验服务后重新校验；不得跳过门禁。");
        } else if (!Boolean.TRUE.equals(validation.get("passed"))) {
            addBlockingReason(reasons, "PUSH_VALIDATION_FAILED", "validation",
                    "配置未通过 Vector 原生校验。",
                    "按诊断项的路径和修复建议修订完整配置后重新校验。");
        }
        if (!validation.isEmpty() && !StringUtils.hasText(validationCandidateDigest)) {
            addBlockingReason(reasons, "PUSH_VALIDATION_CANDIDATE_DIGEST_MISSING",
                    "candidate",
                    "校验结果缺少候选配置摘要。",
                    "重新执行 push_task_validate_config 并保留完整回执。");
        }
        if (StringUtils.hasText(candidateDigest)
                && StringUtils.hasText(validationCandidateDigest)
                && !candidateDigest.equals(validationCandidateDigest)) {
            addDigestMismatch(reasons, "PUSH_CANDIDATE_DIGEST_MISMATCH", "candidate",
                    "卡片校验对应的候选与平台锁定候选不一致。",
                    "使用锁定候选重新校验并生成新的确认卡。",
                    candidateDigest, validationCandidateDigest);
        }
        if (!StringUtils.hasText(validationMetaDigest)) {
            addBlockingReason(reasons, "PUSH_META_DIGEST_MISSING", "meta",
                    "校验结果缺少 Meta 内容摘要。",
                    "重新只读 Meta 并重新校验。");
        }
        if (!StringUtils.hasText(metaFileName)
                || !StringUtils.hasText(metaContentDigest)) {
            addBlockingReason(reasons, "PUSH_META_REFERENCE_MISSING", "meta",
                    "平台锁定方案缺少 Meta 文件名或内容摘要。",
                    "重新执行 config_read 并生成新的确认卡。");
        } else if (StringUtils.hasText(validationMetaDigest)
                && !metaContentDigest.equals(validationMetaDigest)) {
            addDigestMismatch(reasons, "PUSH_META_DIGEST_MISMATCH", "meta",
                    "卡片校验对应的 Meta 与平台锁定 Meta 不一致。",
                    "重新读取最新 Meta 后重新校验。",
                    metaContentDigest, validationMetaDigest);
        }
        if (!StringUtils.hasText(validationDigest)) {
            addBlockingReason(reasons, "PUSH_VALIDATION_DIGEST_MISSING", "validation",
                    "校验结果缺少 validationDigest。",
                    "重新执行 push_task_validate_config。");
        }
        if (!StringUtils.hasText(sourceMark)) {
            addBlockingReason(reasons, "PUSH_SOURCE_MARK_MISSING", "workflow",
                    "候选方案缺少稳定的 sourceMark。",
                    "补充 sourceMark 后重新生成确认卡。");
        } else if (StringUtils.hasText(lockedSourceMark)
                && StringUtils.hasText(cardSourceMark)
                && !lockedSourceMark.equals(cardSourceMark)) {
            reasons.add(blockingReasonWithValues(
                    "PUSH_SOURCE_MARK_MISMATCH", "workflow",
                    "卡片 sourceMark 与平台锁定值不一致。",
                    "使用锁定 sourceMark 重新生成确认卡。",
                    lockedSourceMark, cardSourceMark));
        }
        if (!lockedMetaRef.isEmpty() && !cardMetaRef.isEmpty()
                && (!Objects.equals(text(lockedMetaRef.get("fileName")),
                text(cardMetaRef.get("fileName")))
                || !Objects.equals(text(lockedMetaRef.get("contentDigest")),
                text(cardMetaRef.get("contentDigest"))))) {
            reasons.add(blockingReasonWithValues(
                    "PUSH_META_REFERENCE_MISMATCH", "meta",
                    "卡片 Meta 引用与平台锁定 Meta 不一致。",
                    "重新读取锁定 Meta 后重新校验。",
                    text(lockedMetaRef.get("fileName")) + "@"
                            + abbreviateDigest(text(lockedMetaRef.get("contentDigest"))),
                    text(cardMetaRef.get("fileName")) + "@"
                            + abbreviateDigest(text(cardMetaRef.get("contentDigest")))));
        }
        addMissingEvidenceReason(reasons, state, "config_tree", "读取 Meta 配置树");
        addMissingEvidenceReason(reasons, state, "config_read", "读取已发布 Meta 原文");
        addMissingEvidenceReason(reasons, state, "push_task_validate_config", "执行配置校验");

        Map<String, Object> validationEvidence = latestEvidence(
                state, "push_task_validate_config");
        String evidenceSummary = text(validationEvidence.get("resultSummary"));
        if (!validationEvidence.isEmpty()) {
            String evidenceCandidate = jsonTextField(
                    evidenceSummary, "candidateDigest", "candidate_digest");
            String evidenceMeta = jsonTextField(
                    evidenceSummary, "metaDigest", "meta_digest");
            String evidenceValidation = jsonTextField(
                    evidenceSummary, "validationDigest", "validation_digest");
            if (!StringUtils.hasText(evidenceCandidate)
                    || !StringUtils.hasText(evidenceMeta)
                    || !StringUtils.hasText(evidenceValidation)) {
                addBlockingReason(reasons, "PUSH_VALIDATION_EVIDENCE_DIGEST_MISSING",
                        "evidence",
                        "校验工具回执缺少候选、Meta 或校验摘要，无法完成门禁核对。",
                        "重新执行 push_task_validate_config 并保留完整回执。");
            } else {
                if (StringUtils.hasText(candidateDigest)
                        && !candidateDigest.equals(evidenceCandidate)) {
                    addDigestMismatch(reasons,
                            "PUSH_VALIDATION_EVIDENCE_CANDIDATE_MISMATCH", "evidence",
                            "校验工具回执对应的候选与平台锁定候选不一致。",
                            "使用锁定候选重新校验。",
                            candidateDigest, evidenceCandidate);
                }
                if (StringUtils.hasText(metaContentDigest)
                        && !metaContentDigest.equals(evidenceMeta)) {
                    addDigestMismatch(reasons,
                            "PUSH_VALIDATION_EVIDENCE_META_MISMATCH", "evidence",
                            "校验工具回执对应的 Meta 与平台锁定 Meta 不一致。",
                            "重新读取 Meta 并重新校验。",
                            metaContentDigest, evidenceMeta);
                }
                if (StringUtils.hasText(validationDigest)
                        && !validationDigest.equals(evidenceValidation)) {
                    addDigestMismatch(reasons,
                            "PUSH_VALIDATION_EVIDENCE_DIGEST_MISMATCH", "evidence",
                            "卡片的 validationDigest 与校验工具回执不一致。",
                            "使用本次校验回执生成新的确认卡。",
                            validationDigest, evidenceValidation);
                }
            }
        }
        return reasons;
    }

    private boolean hasLockedPlan(AgentWorkflowState state) {
        Map<String, Object> context = state.getContext() == null
                ? Map.of() : state.getContext();
        Map<String, Object> candidate = map(context.get("candidate"));
        Map<String, Object> metaRef = map(context.get("metaRef"));
        return StringUtils.hasText(text(candidate.get("content")))
                && StringUtils.hasText(text(candidate.get("digest")))
                && StringUtils.hasText(text(context.get("sourceMark")))
                && !map(context.get("request")).isEmpty()
                && StringUtils.hasText(text(metaRef.get("fileName")))
                && StringUtils.hasText(text(metaRef.get("contentDigest")));
    }

    private void addMissingEvidenceReason(
            List<Map<String, Object>> reasons,
            AgentWorkflowState state,
            String tool,
            String action) {
        if (!hasEvidence(state, tool)) {
            addBlockingReason(reasons, "PUSH_EVIDENCE_MISSING", "evidence",
                    "缺少“" + action + "”的成功工具证据（" + tool + "）。",
                    "重新执行该只读校验或配置校验，再生成确认卡。");
        }
    }

    private void addDigestMismatch(
            List<Map<String, Object>> reasons,
            String code,
            String category,
            String message,
            String repairHint,
            String expected,
            String actual) {
        Map<String, Object> reason = blockingReason(code, category, message, repairHint);
        reason.put("expected", abbreviateDigest(expected));
        reason.put("actual", abbreviateDigest(actual));
        reasons.add(reason);
    }

    private void addBlockingReason(
            List<Map<String, Object>> reasons,
            String code,
            String category,
            String message,
            String repairHint) {
        reasons.add(blockingReason(code, category, message, repairHint));
    }

    private Map<String, Object> blockingReasonWithValues(
            String code,
            String category,
            String message,
            String repairHint,
            String expected,
            String actual) {
        Map<String, Object> reason = blockingReason(code, category, message, repairHint);
        reason.put("expected", expected);
        reason.put("actual", actual);
        return reason;
    }

    private Map<String, Object> blockingReason(
            String code,
            String category,
            String message,
            String repairHint) {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("code", code);
        reason.put("category", category);
        reason.put("message", message);
        reason.put("repairHint", repairHint);
        return reason;
    }

    private String blockingMessage(List<Map<String, Object>> reasons) {
        int visible = Math.min(3, reasons.size());
        StringBuilder message = new StringBuilder("平台门禁未通过，共 ")
                .append(reasons.size()).append(" 项：");
        for (int index = 0; index < visible; index++) {
            if (index > 0) {
                message.append("；");
            }
            message.append(text(reasons.get(index).get("message")));
        }
        if (reasons.size() > visible) {
            message.append("；等 ").append(reasons.size()).append(" 项");
        }
        return message.toString();
    }

    private Map<String, Object> latestEvidence(
            AgentWorkflowState state,
            String tool) {
        Map<String, Object> latest = Map.of();
        if (state.getEvidenceRefs() == null) {
            return latest;
        }
        for (Map<String, Object> item : state.getEvidenceRefs()) {
            if (item != null
                    && tool.equals(text(item.get("tool")))
                    && "succeeded".equals(text(item.get("status")))) {
                latest = item;
            }
        }
        return latest;
    }

    private String jsonTextField(String json, String... fields) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        for (String field : fields) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(field)
                            + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private String abbreviateDigest(String value) {
        String normalized = text(value);
        return normalized.length() <= 12
                ? normalized : normalized.substring(0, 12) + "…";
    }

    private boolean verifiedRecordMatches(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        String taskId = text(metadata.get("taskId"));
        String runId = text(metadata.get("runId"));
        String sourceMark = text(metadata.get("sourceMark"));
        String validationDigest = text(metadata.get("validationDigest"));
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : state.getContext();
        return StringUtils.hasText(taskId)
                && StringUtils.hasText(runId)
                && StringUtils.hasText(validationDigest)
                && StringUtils.hasText(sourceMark)
                && sourceMark.equals(text(context.get("sourceMark")))
                && validationDigest.equals(text(context.get("validationDigest")))
                && text(context.get("taskId")).equals(taskId);
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

    private void rememberCandidate(Map<String, Object> context, String content, String format) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        candidate.put("digest", digest(content));
        candidate.put("artifactId", "push-task:" + digest(content));
        String normalizedFormat = text(format).toLowerCase();
        if (StringUtils.hasText(normalizedFormat) && !"vector".equals(normalizedFormat)) {
            candidate.put("format", "yml".equals(normalizedFormat) ? "yaml" : normalizedFormat);
        }
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

    private void copyIfAbsent(
            Map<String, Object> source,
            Map<String, Object> target,
            String key) {
        if (source.containsKey(key) && !target.containsKey(key)) {
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
