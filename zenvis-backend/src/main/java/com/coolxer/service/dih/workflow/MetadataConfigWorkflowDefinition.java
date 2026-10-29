package com.coolxer.service.dih.workflow;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.config.MetaConfigValidationService;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class MetadataConfigWorkflowDefinition implements WorkflowDefinition {

    @Autowired(required = false)
    private MetaConfigValidationService metaConfigValidationService;

    @Autowired(required = false)
    private ConfigService configService;

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
            if (isOperationMetadata(metadata)) {
                if (!hasEvidence(state, "config_tree")
                        || !prepareOperationForDecision(state, metadata)) {
                    return AgentWorkflowStep.BLOCKED;
                }
                return AgentWorkflowStep.META_OPERATION_CONFIRMATION;
            }
            boolean validationCompleted = validateCandidateForDecision(state, metadata);
            if (!validationCompleted
                    || !candidateReady(state)
                    || !hasEvidence(state, "config_tree")
                    || !safeMetaFileName(text(metadata.get("fileName")))) {
                return AgentWorkflowStep.BLOCKED;
            }
            boolean overwrite = Boolean.TRUE.equals(metadata.get("overwrite"))
                    || Boolean.TRUE.equals(state.getContext().get("overwriteRequired"));
            if (overwrite && !overwriteDecisionMatchesLockedCandidate(state, metadata)) {
                return AgentWorkflowStep.BLOCKED;
            }
            return overwrite
                    ? AgentWorkflowStep.META_OVERWRITE_CONFIRMATION
                    : AgentWorkflowStep.META_PLAN_CONFIRMATION;
        }
        if ("metadata-config-record".equals(part.getType())) {
            if (isOperationMetadata(metadata)) {
                String status = text(metadata.get("status"));
                return Set.of("renamed", "deleted").contains(status)
                        && hasEvidence(state, "config_tree")
                        && Boolean.TRUE.equals(state.getContext().get("metaOperationVerified"))
                        ? AgentWorkflowStep.META_VERIFIED
                        : AgentWorkflowStep.BLOCKED;
            }
            String status = text(metadata.get("status"));
            String validationStatus = text(metadata.get("validationStatus"));
            if ("written".equals(status)
                    || "failed".equals(validationStatus)
                    || "blocked".equals(validationStatus)) {
                return AgentWorkflowStep.BLOCKED;
            }
            return hasEvidence(state, "config_read")
                    && (hasEvidence(state, "config_apply")
                    || Boolean.TRUE.equals(state.getContext().get("candidateAlreadyApplied")))
                    ? AgentWorkflowStep.META_VERIFIED
                    : AgentWorkflowStep.BLOCKED;
        }
        return null;
    }

    private boolean overwriteDecisionMatchesLockedCandidate(
            AgentWorkflowState state,
            Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext();
        String planId = firstText(
                text(context.get("planId")),
                text(metadata.get("planId")),
                "plan:" + UUID.randomUUID());
        context.put("planId", planId);
        metadata.put("planId", planId);
        return Objects.equals(
                text(map(context.get("candidate")).get("digest")),
                text(metadata.get("candidateDigest")));
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
                    "failed".equals(text(metadata.get("validationStatus")))
                            ? List.of("force_approve", "reject", "revise")
                            : List.of("approve", "reject", "revise");
            case META_OPERATION_CONFIRMATION -> List.of("approve", "reject", "revise");
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
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION,
                    AgentWorkflowStep.META_OPERATION_CONFIRMATION).contains(step);
            case "force_approve" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION).contains(step);
            case "revise" -> Set.of(
                    AgentWorkflowStep.META_PLAN_CONFIRMATION,
                    AgentWorkflowStep.META_OVERWRITE_CONFIRMATION,
                    AgentWorkflowStep.META_OPERATION_CONFIRMATION,
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
        if ("force_approve".equals(action)) {
            state.getContext().put("forced", true);
            return switch (current) {
                case META_PLAN_CONFIRMATION -> AgentWorkflowStep.META_PREWRITE_CHECK;
                case META_OVERWRITE_CONFIRMATION -> AgentWorkflowStep.META_APPLY;
                default -> current;
            };
        }
        if ("submit".equals(action)) {
            return AgentWorkflowStep.META_DISCOVERY;
        }
        if ("approve".equals(action)) {
            return switch (current) {
                case META_PLAN_CONFIRMATION -> AgentWorkflowStep.META_PREWRITE_CHECK;
                case META_OVERWRITE_CONFIRMATION -> AgentWorkflowStep.META_APPLY;
                case META_OPERATION_CONFIRMATION -> AgentWorkflowStep.META_OPERATION_APPLY;
                default -> current;
            };
        }
        if ("revise".equals(action)) {
            return current == AgentWorkflowStep.META_OPERATION_CONFIRMATION
                    ? AgentWorkflowStep.META_OPERATION_DISCOVERY
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
                    "进入元数据配置 META_DISCOVERY 阶段。必须先调用 "
                            + "config_tree(type=\"meta\")，再基于真实结果生成完整 Meta JSON "
                            + "和 zenvis:metadata-config-decision 确认卡。");
            case META_OPERATION_DISCOVERY -> continuation(
                    "已收到元数据目录或文件操作要求，平台将重新检查真实配置树。",
                    "进入元数据操作发现阶段。必须先调用 config_tree(type=\"meta\")，"
                            + "确认源路径存在，并为重命名确认同一目录下尚未占用的目标路径。"
                            + "不得调用 config_add 或 config_apply。随后输出 "
                            + "zenvis:metadata-config-decision，携带 operation=rename|delete、"
                            + "sourcePath、targetPath、recursive、affectedPaths 和 operationDigest；"
                            + "删除非空目录时必须明确 recursive=true 并展示受影响文件。");
            case META_OPERATION_CONFIRMATION -> Map.of();
            case META_OPERATION_APPLY -> continuation(
                    "已确认元数据操作。",
                    "平台将使用工作流中锁定的路径和 operationDigest 执行操作，"
                            + "模型不得重新解释或替换参数。执行成功后必须核对配置树："
                            + "重命名要求源路径消失且目标路径存在，删除要求源路径消失。");
            case META_PREWRITE_CHECK -> continuation(
                    "已批准元数据候选配置。",
                    "严格应用工作流中已锁定的 Meta 候选，不得重新生成内容。"
                            + "\n目标文件：" + fileName
                            + "\n候选摘要：" + text(candidate.get("digest"))
                            + "\n完整候选配置：\n" + content
                            + "\n依次执行 config_tree；文件不存在时 config_add 只创建空文件；"
                            + "内容不同时先输出覆盖确认卡；允许写入时 config_apply，"
                            + "随后必须 config_read 并做 JSON 语义一致校验；"
                            + "若文件已写入但应用失败，仍须输出 status=written 的写入记录，"
                            + "但不得声明应用成功。"
                            + (Boolean.TRUE.equals(state.getContext().get("forced"))
                            ? "\n用户已显式忽略静态校验，config_apply 必须传 force=true；"
                            + "不得把校验未通过描述为校验通过。" : ""));
            case META_APPLY -> continuation(
                    "已确认覆盖现有元数据配置。",
                    "只允许使用已锁定候选调用 config_apply，随后调用 config_read；"
                            + "读回语义一致前不得输出应用成功记录；"
                            + "若读回证实文件已包含候选但应用失败，仍须输出 status=written 记录。"
                            + "\n目标文件："
                            + fileName + "\n完整候选配置：\n" + content
                            + (Boolean.TRUE.equals(state.getContext().get("forced"))
                            ? "\n用户已显式忽略静态校验，config_apply 必须传 force=true。"
                            : ""));
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
            if (isOperationMetadata(metadata)) {
                if (prepareOperationForDecision(state, metadata)) {
                    metadata.put("source", "workflow");
                }
                return;
            }
            copy(metadata, context, "fileName");
            copy(metadata, context, "overwrite");
            copy(metadata, context, "targetFileDigest");
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
            decorateValidationMetadata(state, metadata);
        }
        if ("metadata-config-record".equals(part.getType())) {
            if (isOperationMetadata(metadata)) {
                copyOperationMetadata(state, metadata);
            }
            decorateRecordMetadata(state, metadata);
        }
    }

    @Override
    public String validateAction(AgentWorkflowState state,
                                 ChatMessagePart part,
                                 Map<String, Object> metadata,
                                 String action) {
        if (!"metadata-config-decision".equals(part.getType())
                && !isMetaConfirm(part, metadata)) {
            return "";
        }
        Map<String, Object> context = state.getContext() == null
                ? new LinkedHashMap<>() : state.getContext();
        if (isOperationMetadata(metadata)) {
            if (!Set.of("approve", "force_approve").contains(action)) {
                return "";
            }
            if ("force_approve".equals(action)) {
                return "元数据重命名或删除不支持强制写入";
            }
            Map<String, Object> locked = map(context.get("operation"));
            if (locked.isEmpty()) {
                return "操作确认已失效，请重新检索配置树";
            }
            String expectedDigest = text(context.get("operationDigest"));
            String cardDigest = text(metadata.get("operationDigest"));
            if (!StringUtils.hasText(cardDigest)
                    || !Objects.equals(expectedDigest, cardDigest)) {
                return "操作目标或内容已变化，请重新生成确认卡";
            }
            Map<String, Object> current = operationSnapshot(locked);
            if (!text(current.get("error")).isEmpty()) {
                return text(current.get("error"));
            }
            if (!Objects.equals(expectedDigest, text(current.get("digest")))) {
                return "源文件、目录成员或目标占用状态在确认后已变化，请重新生成确认卡";
            }
            return "";
        }
        Map<String, Object> candidate = map(context.get("candidate"));
        String lockedDigest = text(candidate.get("digest"));
        String cardDigest = text(metadata.get("candidateDigest"));
        if (Set.of("approve", "force_approve").contains(action)
                && !StringUtils.hasText(cardDigest)) {
            return "确认卡缺少候选摘要，请重新生成";
        }
        if (StringUtils.hasText(cardDigest)
                && !Objects.equals(lockedDigest, cardDigest)) {
            return "候选配置已变化，请使用最新确认卡";
        }
        boolean failed = "failed".equals(text(context.get("validationStatus")));
        if ("approve".equals(action) && failed) {
            return "候选 Meta 未通过平台校验，只能补充修订或显式强制写入";
        }
        if ("force_approve".equals(action)) {
            if (!failed || !Boolean.TRUE.equals(context.get("forceAllowed"))) {
                return "当前候选没有可绕过的校验错误";
            }
            if (!StringUtils.hasText(cardDigest)
                    || !Objects.equals(lockedDigest, cardDigest)) {
                return "强制写入卡片的候选摘要已失效，请重新生成";
            }
            String expectedTarget = text(context.get("targetFileDigest"));
            String currentTarget = targetFileDigest(text(context.get("fileName")));
            if (StringUtils.hasText(expectedTarget)
                    && !Objects.equals(expectedTarget, currentTarget)) {
                return "目标 Meta 文件在确认后已变化，请重新生成并确认";
            }
        }
        return "";
    }

    @Override
    public void decorate(AgentWorkflowState state,
                         ChatMessagePart part,
                         Map<String, Object> metadata) {
        if ("metadata-config-decision".equals(part.getType())
                || isMetaConfirm(part, metadata)) {
            if (isOperationMetadata(metadata)) {
                decorateOperationMetadata(state, metadata);
                return;
            }
            decorateValidationMetadata(state, metadata);
            if ("failed".equals(text(metadata.get("validationStatus")))) {
                metadata.put("allowedActions", List.of(
                        "force_approve", "reject", "revise"));
            }
            return;
        }
        if ("metadata-config-record".equals(part.getType())) {
            if (isOperationMetadata(metadata)) {
                copyOperationMetadata(state, metadata);
            }
            decorateRecordMetadata(state, metadata);
        }
    }

    private boolean validateCandidateForDecision(AgentWorkflowState state,
                                                 Map<String, Object> metadata) {
        if (metaConfigValidationService == null || state.getContext() == null) {
            return true;
        }
        Map<String, Object> candidate = map(state.getContext().get("candidate"));
        String content = text(candidate.get("content"));
        String fileName = text(metadata.get("fileName"));
        if (!StringUtils.hasText(content) || !StringUtils.hasText(fileName)) {
            return true;
        }
        MetaConfigValidationService.ValidationResult validation;
        try {
            validation = metaConfigValidationService.validate(fileName, content);
        } catch (RuntimeException e) {
            List<Map<String, Object>> errors = List.of(diagnosticMetadata(
                    "META_VALIDATION_UNAVAILABLE", "ERROR", "$",
                    "平台 Meta 校验执行失败：" + e.getMessage(),
                    "检查平台校验服务后可重试；不得绕过校验直接写入。"));
            state.getContext().put("validationStatus", "blocked");
            state.getContext().put("forceAllowed", false);
            state.getContext().put("validationErrors", errors);
            state.getContext().put("validationDiagnostics", errors);
            state.getContext().put("validationRepairs", List.of());
            decorateValidationMetadata(state, metadata);
            return false;
        }
        Map<String, Object> normalizedCandidate = new LinkedHashMap<>(candidate);
        normalizedCandidate.put("content", validation.normalizedText());
        normalizedCandidate.put("digest", validation.candidateDigest());
        normalizedCandidate.put("artifactId", "meta-config:" + validation.candidateDigest());
        state.getContext().put("candidate", normalizedCandidate);
        state.getContext().put("validationStatus",
                validation.passed() ? "success" : "failed");
        state.getContext().put("forceAllowed", !validation.passed());
        List<Map<String, Object>> diagnostics = validation.diagnostics().stream()
                .map(this::diagnosticMetadata)
                .toList();
        List<Map<String, Object>> errors = diagnostics.stream()
                .filter(item -> "ERROR".equals(text(item.get("severity"))))
                .toList();
        List<Map<String, Object>> repairs = validation.repairs().stream()
                .map(this::repairMetadata)
                .toList();
        state.getContext().put("validationErrors", errors);
        state.getContext().put("validationDiagnostics", diagnostics);
        state.getContext().put("validationRepairs", repairs);
        state.getContext().put("validationDigest", validation.candidateDigest());
        String targetFileDigest = targetFileDigest(fileName);
        if (StringUtils.hasText(targetFileDigest)) {
            state.getContext().put("targetFileDigest", targetFileDigest);
        }
        metadata.put("candidateDigest", validation.candidateDigest());
        metadata.put("artifactId", normalizedCandidate.get("artifactId"));
        metadata.put("targetFileDigest", targetFileDigest);
        decorateValidationMetadata(state, metadata);
        return true;
    }

    private boolean prepareOperationForDecision(AgentWorkflowState state,
                                                Map<String, Object> metadata) {
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("operation", text(metadata.get("operation")));
        requested.put("sourcePath", firstText(
                text(metadata.get("sourcePath")), text(metadata.get("fileName"))));
        requested.put("targetPath", text(metadata.get("targetPath")));
        requested.put("recursive", Boolean.TRUE.equals(metadata.get("recursive")));
        String newFileName = text(metadata.get("newFileName"));
        if ("rename".equals(text(requested.get("operation")))
                && !StringUtils.hasText(text(requested.get("targetPath")))
                && StringUtils.hasText(newFileName)) {
            requested.put("targetPath", siblingPath(
                    text(requested.get("sourcePath")), newFileName));
        }
        Map<String, Object> snapshot = operationSnapshot(requested);
        String error = text(snapshot.get("error"));
        if (StringUtils.hasText(error)) {
            List<Map<String, Object>> errors = List.of(diagnosticMetadata(
                    "META_OPERATION_INVALID", "ERROR",
                    text(requested.get("sourcePath")),
                    error,
                    "重新读取 config_tree，确认源路径存在、目标路径未占用且两者位于同一目录。"));
            state.getContext().put("validationStatus", "blocked");
            state.getContext().put("forceAllowed", false);
            state.getContext().put("validationErrors", errors);
            state.getContext().put("validationDiagnostics", errors);
            state.getContext().put("validationRepairs", List.of());
            metadata.put("operation", text(requested.get("operation")));
            metadata.put("sourcePath", text(requested.get("sourcePath")));
            metadata.put("targetPath", text(requested.get("targetPath")));
            metadata.put("recursive", Boolean.TRUE.equals(requested.get("recursive")));
            metadata.put("validationStatus", "blocked");
            metadata.put("forceAllowed", false);
            metadata.put("validationErrors", errors);
            metadata.put("validationMessage", error);
            return false;
        }

        state.getContext().put("operation", snapshot);
        state.getContext().put("operationDigest", text(snapshot.get("digest")));
        state.getContext().put("validationStatus", "success");
        state.getContext().put("forceAllowed", false);
        state.getContext().put("validationErrors", List.of());
        state.getContext().put("validationDiagnostics", List.of());
        state.getContext().put("validationRepairs", List.of());
        state.getContext().put("validationDigest", text(snapshot.get("digest")));
        metadata.put("operation", text(snapshot.get("operation")));
        metadata.put("sourcePath", text(snapshot.get("sourcePath")));
        metadata.put("targetPath", text(snapshot.get("targetPath")));
        metadata.put("kind", text(snapshot.get("kind")));
        metadata.put("recursive", Boolean.TRUE.equals(snapshot.get("recursive")));
        metadata.put("affectedPaths", snapshot.getOrDefault("affectedPaths", List.of()));
        metadata.put("operationDigest", text(snapshot.get("digest")));
        metadata.put("validationStatus", "success");
        metadata.put("forceAllowed", false);
        metadata.put("validationErrors", List.of());
        metadata.put("validationDigest", text(snapshot.get("digest")));
        metadata.remove("candidateDigest");
        metadata.put("allowedActions", List.of("approve", "reject", "revise"));
        metadata.put("source", "workflow");
        return true;
    }

    private void decorateOperationMetadata(AgentWorkflowState state,
                                           Map<String, Object> metadata) {
        Map<String, Object> operation = map(state.getContext().get("operation"));
        if (operation.isEmpty()) {
            return;
        }
        operation.forEach(metadata::putIfAbsent);
        metadata.put("operation", text(operation.get("operation")));
        metadata.put("sourcePath", text(operation.get("sourcePath")));
        metadata.put("targetPath", text(operation.get("targetPath")));
        metadata.put("kind", text(operation.get("kind")));
        metadata.put("recursive", Boolean.TRUE.equals(operation.get("recursive")));
        metadata.put("affectedPaths", operation.getOrDefault("affectedPaths", List.of()));
        metadata.put("operationDigest", text(operation.get("digest")));
        metadata.put("validationStatus", "success");
        metadata.put("forceAllowed", false);
        metadata.put("validationErrors", List.of());
    }

    private void copyOperationMetadata(AgentWorkflowState state,
                                       Map<String, Object> metadata) {
        Map<String, Object> operation = map(state.getContext().get("operation"));
        if (operation.isEmpty()) {
            return;
        }
        metadata.put("operation", text(operation.get("operation")));
        metadata.put("sourcePath", text(operation.get("sourcePath")));
        metadata.put("targetPath", text(operation.get("targetPath")));
        metadata.put("kind", text(operation.get("kind")));
        metadata.put("recursive", Boolean.TRUE.equals(operation.get("recursive")));
        metadata.put("affectedPaths", operation.getOrDefault("affectedPaths", List.of()));
        metadata.put("operationDigest", text(operation.get("digest")));
    }

    private Map<String, Object> operationSnapshot(Map<String, Object> requested) {
        Map<String, Object> result = new LinkedHashMap<>();
        String operation = text(requested.get("operation"));
        String sourcePath;
        String targetPath = "";
        try {
            sourcePath = safeConfigRelativePath(text(requested.get("sourcePath")));
            if ("rename".equals(operation)) {
                targetPath = safeConfigRelativePath(text(requested.get("targetPath")));
            } else if (!"delete".equals(operation)) {
                return operationError("不支持的操作类型");
            }
        } catch (IllegalArgumentException e) {
            return operationError(e.getMessage());
        }
        if ("rename".equals(operation)) {
            if (sourcePath.equals(targetPath)
                    || !parentPath(sourcePath).equals(parentPath(targetPath))) {
                return operationError("重命名只允许在同一父目录内修改名称");
            }
            if (leafPath(sourcePath).equals(leafPath(targetPath))) {
                return operationError("新名称不能与原名称相同");
            }
        }
        if (configService == null) {
            return operationError("配置服务不可用");
        }
        List<ConfigVo> nodes = flattenConfigNodes(configService.getConfigFileTree("meta"));
        ConfigVo sourceNode = nodes.stream()
                .filter(node -> sourcePath.equals(text(node.getRelativePath())))
                .findFirst().orElse(null);
        if (sourceNode == null) {
            return operationError("源路径不存在：" + sourcePath);
        }
        String kind = Boolean.TRUE.equals(sourceNode.getIsDir()) ? "directory" : "file";
        boolean recursive = Boolean.TRUE.equals(requested.get("recursive"));
        if ("rename".equals(operation)
                && configService.fileExistsInConfigPath("meta", targetPath)) {
            return operationError("目标路径已存在，拒绝覆盖：" + targetPath);
        }
        List<String> affectedPaths = nodes.stream()
                .map(ConfigVo::getRelativePath)
                .filter(StringUtils::hasText)
                .filter(path -> path.equals(sourcePath)
                        || path.startsWith(sourcePath + "/"))
                .sorted(Comparator.naturalOrder())
                .toList();
        if ("delete".equals(operation)
                && "directory".equals(kind)
                && affectedPaths.size() > 1
                && !recursive) {
            return operationError("目录非空，必须显式设置 recursive=true 才能递归删除");
        }
        StringBuilder fingerprint = new StringBuilder()
                .append(operation).append('|')
                .append(sourcePath).append('|')
                .append(kind).append('|')
                .append(targetPath).append('|')
                .append(recursive).append('\n');
        Map<String, String> fileDigests = new LinkedHashMap<>();
        Map<String, String> nodeKinds = new LinkedHashMap<>();
        for (String path : affectedPaths) {
            ConfigVo node = nodes.stream()
                    .filter(item -> path.equals(text(item.getRelativePath())))
                    .findFirst().orElse(null);
            if (node != null && Boolean.TRUE.equals(node.getIsDir())) {
                nodeKinds.put(path, "directory");
                fingerprint.append("D|").append(path).append('\n');
                continue;
            }
            String content = configService.readFile("meta", path);
            String contentDigest = digest(content == null ? "" : content);
            fileDigests.put(path, contentDigest);
            nodeKinds.put(path, "file");
            fingerprint.append("F|").append(path).append('|')
                    .append(contentDigest).append('\n');
        }
        result.put("operation", operation);
        result.put("sourcePath", sourcePath);
        result.put("targetPath", targetPath);
        result.put("kind", kind);
        result.put("recursive", recursive);
        result.put("affectedPaths", affectedPaths);
        result.put("fileDigests", fileDigests);
        result.put("nodeKinds", nodeKinds);
        result.put("digest", digest(fingerprint.toString()));
        return result;
    }

    private Map<String, Object> operationError(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return result;
    }

    private List<ConfigVo> flattenConfigNodes(List<ConfigVo> roots) {
        List<ConfigVo> result = new ArrayList<>();
        if (roots == null) {
            return result;
        }
        for (ConfigVo root : roots) {
            flattenConfigNode(root, result);
        }
        return result;
    }

    private void flattenConfigNode(ConfigVo node, List<ConfigVo> result) {
        if (node == null) {
            return;
        }
        if (StringUtils.hasText(node.getRelativePath())) {
            result.add(node);
        }
        if (node.getNodes() != null) {
            node.getNodes().forEach(child -> flattenConfigNode(child, result));
        }
    }

    private String safeConfigRelativePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("配置路径不能为空");
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("配置路径必须是配置根下的相对路径");
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (!StringUtils.hasText(normalized) || ".".equals(normalized)) {
            throw new IllegalArgumentException("不能操作配置类型根目录");
        }
        for (String segment : normalized.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("配置路径包含非法路径片段");
            }
        }
        return normalized;
    }

    private String parentPath(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    private String leafPath(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private String siblingPath(String sourcePath, String targetName) {
        if (!StringUtils.hasText(sourcePath) || !StringUtils.hasText(targetName)
                || targetName.contains("/") || targetName.contains("\\")
                || ".".equals(targetName) || "..".equals(targetName)) {
            return "";
        }
        String parent = parentPath(sourcePath.trim().replace('\\', '/'));
        return parent.isEmpty() ? targetName : parent + "/" + targetName;
    }

    private void decorateValidationMetadata(AgentWorkflowState state,
                                            Map<String, Object> metadata) {
        Map<String, Object> context = state.getContext() == null
                ? Map.of() : state.getContext();
        if (!context.containsKey("validationStatus")) {
            return;
        }
        String status = text(context.get("validationStatus"));
        boolean failed = "failed".equals(status);
        boolean blocked = "blocked".equals(status);
        metadata.put("validationStatus", blocked ? "blocked" : failed ? "failed" : "success");
        metadata.put("forceAllowed", failed && Boolean.TRUE.equals(context.get("forceAllowed")));
        metadata.put("validationErrors", context.getOrDefault("validationErrors", List.of()));
        metadata.put("validationRepairs", context.getOrDefault("validationRepairs", List.of()));
        metadata.put("validationDigest", text(context.get("validationDigest")));
        metadata.put("targetFileDigest", text(context.get("targetFileDigest")));
        if (failed || blocked) {
            Object errors = context.get("validationErrors");
            metadata.put("validationMessage", validationMessage(errors));
        }
    }

    private void decorateRecordMetadata(AgentWorkflowState state,
                                        Map<String, Object> metadata) {
        if (isOperationMetadata(metadata)) {
            metadata.put("source", "workflow");
            metadata.put("forced", false);
            metadata.put("validationStatus",
                    Boolean.TRUE.equals(state.getContext().get("metaOperationVerified"))
                            ? "success" : "blocked");
            metadata.put("validationDigest",
                    text(state.getContext().get("operationDigest")));
            metadata.put("validationErrors", List.of());
            return;
        }
        metadata.put("source", "workflow");
        boolean forced = Boolean.TRUE.equals(state.getContext().get("forced"));
        boolean written = "written".equals(text(metadata.get("status")));
        metadata.put("forced", forced);
        metadata.put("validationStatus", written
                ? firstText(text(metadata.get("validationStatus")), "blocked")
                : state.getStep() == AgentWorkflowStep.BLOCKED
                ? "blocked"
                : forced
                ? "forced" : "success");
        metadata.put("candidateDigest",
                text(map(state.getContext().get("candidate")).get("digest")));
        metadata.put("validationDigest",
                text(state.getContext().get("validationDigest")));
        metadata.put("validationErrors",
                state.getContext().getOrDefault("validationErrors", List.of()));
        metadata.put("validationRepairs",
                state.getContext().getOrDefault("validationRepairs", List.of()));
    }

    private Map<String, Object> diagnosticMetadata(
            MetaConfigValidationService.Diagnostic diagnostic) {
        return diagnosticMetadata(diagnostic.code(), diagnostic.severity(),
                diagnostic.path(), diagnostic.message(), diagnostic.repairHint());
    }

    private Map<String, Object> diagnosticMetadata(String code,
                                                    String severity,
                                                    String path,
                                                    String message,
                                                    String repairHint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("severity", severity);
        item.put("path", path);
        item.put("message", message);
        item.put("repairHint", repairHint);
        return item;
    }

    private Map<String, Object> repairMetadata(MetaConfigValidationService.Repair repair) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", repair.path());
        item.put("before", repair.before());
        item.put("after", repair.after());
        item.put("reason", repair.reason());
        return item;
    }

    private String validationMessage(Object errors) {
        if (!(errors instanceof List<?> list) || list.isEmpty()) {
            return "候选 Meta 未通过平台校验";
        }
        String message = list.stream()
                .map(this::validationItemMessage)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> first + "；" + second)
                .orElse("");
        return StringUtils.hasText(message) ? message : "候选 Meta 未通过平台校验";
    }

    private String validationItemMessage(Object item) {
        if (item instanceof Map<?, ?> raw) {
            String path = text(raw.get("path"));
            String message = text(raw.get("message"));
            if (StringUtils.hasText(path) && StringUtils.hasText(message)) {
                return path + "：" + message;
            }
            return firstText(message, path, text(raw.get("code")));
        }
        return text(item);
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

    private String targetFileDigest(String fileName) {
        if (configService == null || !safeMetaFileName(fileName)) {
            return "";
        }
        try {
            if (!configService.fileExistsInConfigPath("meta", fileName)) {
                return "missing";
            }
            String content = configService.readFile("meta", fileName);
            return digest(content == null ? "" : content);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private boolean isMetaConfirm(ChatMessagePart part, Map<String, Object> metadata) {
        return "confirm".equals(part.getType())
                && ("metadata_config.confirm_plan".equals(text(metadata.get("action")))
                || "meta".equals(text(metadata.get("configKind"))));
    }

    private boolean isOperationMetadata(Map<String, Object> metadata) {
        return Set.of("rename", "delete").contains(text(metadata.get("operation")));
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
                        AgentWorkflowStep.META_OPERATION_DISCOVERY,
                        AgentWorkflowStep.META_OPERATION_APPLY,
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
