package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.AgentRunInput;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.agentrun.AgentRunInteraction;
import com.coolxer.model.dih.dto.AgentRunInteractionResponseDto;
import com.coolxer.model.dih.dto.WorkflowActionDto;
import com.coolxer.model.dih.vo.AgentRunInteractionResponseVo;
import com.coolxer.model.dih.vo.AgentRunVo;
import com.coolxer.model.dih.vo.WorkflowActionVo;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.workflow.WorkflowActionService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 统一处理持久化 Run 的卡片决策、自由文本和 MCP 审批响应。
 */
@Slf4j
@Service
public class AgentRunInteractionService {

    private static final Set<String> INTERACTIVE_CARD_TYPES = Set.of(
            "confirm",
            "info-steps",
            "metadata-config-decision",
            "data-push-service-decision",
            "visualization-chart-decision",
            "visualization-app-decision",
            "visualization-dashboard-decision");

    private static final Set<String> RESOLVED_CARD_STATUSES = Set.of(
            "approved", "force_approved", "rejected", "revise", "cancelled",
            "completed", "saved", "submitted", "added");

    private static final Set<String> DECISION_CARD_TYPES = Set.of(
            "confirm",
            "metadata-config-decision",
            "data-push-service-decision",
            "visualization-chart-decision",
            "visualization-app-decision",
            "visualization-dashboard-decision");

    private static final Set<String> CARD_ACTIONS = Set.of(
            "submit", "approve", "force_approve", "reject", "revise", "retry", "add_to_library");

    private final AgentRunService runService;
    private final AgentRunInputRepository inputRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionService chatSessionService;
    private final WorkflowActionService workflowActionService;
    private final McpApprovalService mcpApprovalService;
    private final ChatAttachmentService chatAttachmentService;

    public AgentRunInteractionService(AgentRunService runService,
                                      AgentRunInputRepository inputRepository,
                                      ChatSessionRepository chatSessionRepository,
                                      ChatSessionService chatSessionService,
                                      WorkflowActionService workflowActionService,
                                      McpApprovalService mcpApprovalService,
                                      ChatAttachmentService chatAttachmentService) {
        this.runService = runService;
        this.inputRepository = inputRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionService = chatSessionService;
        this.workflowActionService = workflowActionService;
        this.mcpApprovalService = mcpApprovalService;
        this.chatAttachmentService = chatAttachmentService;
    }

    /**
     * 从已持久化的模型消息中选择当前唯一可操作卡片。
     */
    public AgentRunInteraction cardInteraction(Message message, String prompt) {
        if (message == null || message.getParts() == null) {
            return null;
        }
        ChatMessagePart selected = null;
        ChatMessagePart currentWorkflowCard = null;
        int currentRevision = Integer.MIN_VALUE;
        for (ChatMessagePart part : message.getParts()) {
            if (!isPendingCard(part)) {
                continue;
            }
            if (selected == null) {
                selected = part;
            }
            if (StringUtils.isNotBlank(text(metadata(part).get("workflowId")))
                    && !effectiveAllowedActions(part).isEmpty()) {
                Integer revision = integer(metadata(part).get("stateRevision"));
                int candidateRevision = revision == null ? 0 : revision;
                if (currentWorkflowCard == null || candidateRevision > currentRevision) {
                    currentWorkflowCard = part;
                    currentRevision = candidateRevision;
                }
            }
        }
        if (currentWorkflowCard != null) {
            selected = currentWorkflowCard;
        }
        if (selected == null) {
            return null;
        }
        Map<String, Object> metadata = metadata(selected);
        Integer stateRevision = integer(metadata.get("stateRevision"));
        return AgentRunInteraction.builder()
                .interactionId(UUID.randomUUID().toString())
                .kind(AgentRunInteraction.KIND_CARD_DECISION)
                .messageId(message.getId())
                .partId(selected.getId())
                .workflowId(textOrNull(metadata.get("workflowId")))
                .stateRevision(stateRevision == null ? 0 : stateRevision)
                .title(StringUtils.defaultIfBlank(selected.getTitle(), "需要处理"))
                .prompt(StringUtils.defaultIfBlank(prompt,
                        StringUtils.defaultIfBlank(selected.getContent(), selected.getTitle())))
                .allowedActions(effectiveAllowedActions(selected))
                .createdAt(new Date())
                .build();
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunInteractionResponseVo respond(String runId,
                                                 String interactionId,
                                                 AgentRunInteractionResponseDto request,
                                                 User currentUser) {
        AgentRun run = runService.lockOwnedRun(runId, currentUser);
        if (request == null) {
            throw new ApiException(400, "交互响应不能为空");
        }
        Map<String, Object> response = responsePayload(request);
        String responseJson = JacksonUtil.toJson(response);
        AgentRunInput existing = inputRepository
                .findFirstByRunIdAndInteractionIdOrderByCreateTimeAsc(runId, interactionId)
                .orElse(null);
        if (existing != null) {
            if (!Objects.equals(responseJson, existing.getResponseJson())) {
                throw new ApiException(409, "该交互已使用不同内容响应，请刷新后重试");
            }
            return duplicateResponse(run, interactionId, existing);
        }

        AgentRunInteraction interaction = AgentRunInteraction.fromRun(run);
        if (interaction == null || !StringUtils.equals(interactionId, interaction.getInteractionId())) {
            throw new ApiException(409, "交互已处理或已失效，请刷新后重试");
        }
        if (run.getStatus().isTerminal()) {
            throw new ApiException(409, "任务已结束，不能继续响应");
        }

        return switch (StringUtils.defaultString(interaction.getKind())) {
            case AgentRunInteraction.KIND_USER_INPUT ->
                    respondUserInput(run, interaction, request, responseJson, currentUser);
            case AgentRunInteraction.KIND_CARD_DECISION ->
                    respondCard(run, interaction, request, responseJson, currentUser);
            case AgentRunInteraction.KIND_MCP_APPROVAL ->
                    respondApproval(run, interaction, request, responseJson, currentUser);
            default -> throw new ApiException(400, "不支持的交互类型");
        };
    }

    private AgentRunInteractionResponseVo respondUserInput(AgentRun run,
                                                           AgentRunInteraction interaction,
                                                           AgentRunInteractionResponseDto request,
                                                           String responseJson,
                                                           User currentUser) {
        requireStatus(run, AgentRunStatus.WAITING_USER);
        String message = StringUtils.trimToEmpty(request.getMessage());
        List<ChatAttachment> attachments = request.getAttachments() == null
                ? List.of() : request.getAttachments();
        if (StringUtils.isBlank(message) && attachments.isEmpty()) {
            throw new ApiException(400, "补充消息或附件不能为空");
        }
        if (StringUtils.isBlank(message)) {
            message = "请分析补充的附件内容。";
        }
        if (!attachments.isEmpty()) {
            chatAttachmentService.ensureSendable(attachments, currentUser);
            chatAttachmentService.bindToChat(attachments, currentUser, run.getChatId());
        }
        saveInput(run, interaction, message, attachments, responseJson,
                AgentRunInputStatus.PENDING, currentUser);
        complete(run, interaction, true, Map.of(
                "kind", interaction.getKind(),
                "message", message,
                "attachmentCount", attachments.size()));
        return responseVo(run, interaction, true, "submitted", "补充信息已提交");
    }

    private AgentRunInteractionResponseVo respondCard(AgentRun run,
                                                      AgentRunInteraction interaction,
                                                      AgentRunInteractionResponseDto request,
                                                      String responseJson,
                                                      User currentUser) {
        requireStatus(run, AgentRunStatus.WAITING_USER);
        String action = StringUtils.trimToEmpty(request.getAction()).toLowerCase();
        if (!CARD_ACTIONS.contains(action)) {
            throw new ApiException(400, "不支持的卡片动作");
        }
        Integer expectedRevision = interaction.getStateRevision() == null
                ? 0 : interaction.getStateRevision();
        if (!Objects.equals(expectedRevision, request.getStateRevision())) {
            throw new ApiException(409, "卡片版本已过期，请刷新后使用最新卡片");
        }
        if (StringUtils.isNotBlank(interaction.getWorkflowId())) {
            return respondWorkflowCard(run, interaction, request, responseJson, currentUser, action);
        }
        return respondGenericCard(run, interaction, request, responseJson, currentUser, action);
    }

    private AgentRunInteractionResponseVo respondWorkflowCard(AgentRun run,
                                                              AgentRunInteraction interaction,
                                                              AgentRunInteractionResponseDto request,
                                                              String responseJson,
                                                              User currentUser,
                                                              String action) {
        WorkflowActionDto actionRequest = new WorkflowActionDto();
        actionRequest.setChatId(run.getChatId());
        actionRequest.setMessageId(interaction.getMessageId());
        actionRequest.setPartId(interaction.getPartId());
        actionRequest.setWorkflowId(interaction.getWorkflowId());
        actionRequest.setAction(action);
        actionRequest.setAnswers(request.getAnswers());
        actionRequest.setRevision(request.getRevision());
        WorkflowActionVo result = workflowActionService.handleForAgentRun(actionRequest, currentUser);
        String continuation = continuationRequest(result.getContinuation());
        saveInput(run, interaction, continuation, List.of(), responseJson,
                AgentRunInputStatus.PENDING, currentUser);
        decorateCardResponse(run, interaction, action, result.getPartStatus(),
                genericSummary(action, request), currentUser, request.getRevision());
        complete(run, interaction, true, Map.of(
                "kind", interaction.getKind(),
                "action", action,
                "partStatus", StringUtils.defaultString(result.getPartStatus()),
                "workflowId", StringUtils.defaultString(result.getWorkflowId())));
        return responseVo(run, interaction, true,
                result.getPartStatus(), continuation);
    }

    private AgentRunInteractionResponseVo respondGenericCard(AgentRun run,
                                                             AgentRunInteraction interaction,
                                                             AgentRunInteractionResponseDto request,
                                                             String responseJson,
                                                             User currentUser,
                                                             String action) {
        ChatSession session = lockedSession(run, currentUser);
        LocatedPart located = locate(session, interaction.getMessageId(), interaction.getPartId());
        if (located == null || !isPendingCard(located.part())) {
            throw new ApiException(409, "卡片已处理或已失效，请刷新后重试");
        }
        List<String> allowed = effectiveAllowedActions(located.part());
        if (!allowed.contains(action)) {
            throw new ApiException(400, "当前卡片不允许执行该动作");
        }
        if ("submit".equals(action)) {
            validateInfoStepAnswers(located.part(), request.getAnswers());
        }
        if ("revise".equals(action) && StringUtils.isBlank(request.getRevision())) {
            throw new ApiException(400, "请提供调整要求");
        }
        String partStatus = genericPartStatus(action);
        located.part().setStatus(partStatus);
        Map<String, Object> metadata = metadata(located.part());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", action);
        audit.put("summary", genericSummary(action, request));
        audit.put("respondedAt", new Date().toInstant().toString());
        audit.put("respondedBy", currentUser.getId());
        if (request.getAnswers() != null) {
            audit.put("answers", request.getAnswers());
        }
        if (StringUtils.isNotBlank(request.getRevision())) {
            audit.put("revision", request.getRevision().trim());
        }
        metadata.put("interactionResponse", audit);
        metadata.put("allowedActions", List.of());
        located.part().setMetadata(metadata);
        session.setMessages(JacksonUtil.toJson(located.messages()));
        chatSessionService.updateWorkflowState(
                (long) session.getId(), session.getMessages(), session.getExtraData(), currentUser);

        String continuation = genericContinuation(located.part(), request, action);
        saveInput(run, interaction, continuation, List.of(), responseJson,
                AgentRunInputStatus.PENDING, currentUser);
        complete(run, interaction, true, Map.of(
                "kind", interaction.getKind(),
                "action", action,
                "partStatus", partStatus));
        return responseVo(run, interaction, true, partStatus, continuation);
    }

    private AgentRunInteractionResponseVo respondApproval(AgentRun run,
                                                          AgentRunInteraction interaction,
                                                          AgentRunInteractionResponseDto request,
                                                          String responseJson,
                                                          User currentUser) {
        requireStatus(run, AgentRunStatus.WAITING_APPROVAL);
        if (StringUtils.isBlank(interaction.getRequestId())) {
            throw new ApiException(409, "审批请求已失效，请刷新后重试");
        }
        String decision = StringUtils.trimToEmpty(request.getDecision());
        mcpApprovalService.decide(interaction.getRequestId(), decision, request.getComment(), currentUser);
        saveInput(run, interaction, StringUtils.defaultIfBlank(request.getComment(),
                        "MCP 审批：" + decision), List.of(), responseJson,
                AgentRunInputStatus.APPLIED, currentUser);
        complete(run, interaction, false, Map.of(
                "kind", interaction.getKind(),
                "decision", decision));
        return responseVo(run, interaction, true, decision, "");
    }

    private void saveInput(AgentRun run,
                           AgentRunInteraction interaction,
                           String content,
                           List<ChatAttachment> attachments,
                           String responseJson,
                           AgentRunInputStatus status,
                           User currentUser) {
        AgentRunInput input = new AgentRunInput()
                .setRunId(run.getRunId())
                .setClientMessageId("interaction:" + interaction.getInteractionId())
                .setInteractionId(interaction.getInteractionId())
                .setInteractionKind(interaction.getKind())
                .setMessageId(interaction.getMessageId())
                .setPartId(interaction.getPartId())
                .setWorkflowId(interaction.getWorkflowId())
                .setRequestId(interaction.getRequestId())
                .setContent(StringUtils.left(StringUtils.defaultString(content), 24_000))
                .setAttachments(JacksonUtil.toJson(attachments == null ? List.of() : attachments))
                .setResponseJson(responseJson)
                .setStatus(status);
        if (status == AgentRunInputStatus.APPLIED) {
            input.setAppliedAt(new Date());
        }
        input.setCreateBy(currentUser.getId());
        input.setUpdateBy(currentUser.getId());
        inputRepository.save(input);
    }

    private void complete(AgentRun run,
                          AgentRunInteraction interaction,
                          boolean requeue,
                          Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>(payload);
        event.put("interactionId", interaction.getInteractionId());
        runService.completeInteraction(run, interaction.getInteractionId(),
                run.getCurrentStepId(), requeue, event);
    }

    private AgentRunInteractionResponseVo responseVo(AgentRun run,
                                                     AgentRunInteraction interaction,
                                                     boolean accepted,
                                                     String partStatus,
                                                     String summary) {
        return new AgentRunInteractionResponseVo(
                accepted, false, interaction.getInteractionId(),
                StringUtils.defaultString(partStatus), StringUtils.defaultString(summary),
                AgentRunVo.from(run));
    }

    private AgentRunInteractionResponseVo duplicateResponse(AgentRun run,
                                                            String interactionId,
                                                            AgentRunInput existing) {
        String partStatus = "";
        Map<String, Object> response = Map.of();
        try {
            response = JacksonUtil.toMap(existing.getResponseJson(),
                    new TypeReference<Map<String, Object>>() {});
            partStatus = text(response.get("partStatus"));
        } catch (RuntimeException ignored) {
            // Duplicate detection does not depend on being able to re-read the audit payload.
        }
        return new AgentRunInteractionResponseVo(
                true, true, interactionId, partStatus,
                StringUtils.defaultString(existing.getContent()), AgentRunVo.from(run));
    }

    private void decorateCardResponse(AgentRun run,
                                      AgentRunInteraction interaction,
                                      String action,
                                      String partStatus,
                                      String summary,
                                      User currentUser,
                                      String revision) {
        if (StringUtils.isBlank(interaction.getMessageId())
                || StringUtils.isBlank(interaction.getPartId())) {
            return;
        }
        ChatSession session = lockedSession(run, currentUser);
        LocatedPart located = locate(session, interaction.getMessageId(), interaction.getPartId());
        if (located == null) {
            return;
        }
        located.part().setStatus(StringUtils.defaultIfBlank(partStatus, located.part().getStatus()));
        Map<String, Object> metadata = metadata(located.part());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("action", action);
        audit.put("summary", StringUtils.defaultString(summary));
        audit.put("respondedAt", new Date().toInstant().toString());
        audit.put("respondedBy", currentUser.getId());
        if (StringUtils.isNotBlank(revision)) {
            audit.put("revision", revision.trim());
        }
        metadata.put("interactionResponse", audit);
        located.part().setMetadata(metadata);
        session.setMessages(JacksonUtil.toJson(located.messages()));
        chatSessionService.updateWorkflowState(
                (long) session.getId(), session.getMessages(), session.getExtraData(), currentUser);
    }

    private ChatSession lockedSession(AgentRun run, User currentUser) {
        return chatSessionRepository
                .findBySessionIdAndCreateByForUpdate(run.getChatId(), currentUser.getId())
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
    }

    private LocatedPart locate(ChatSession session, String messageId, String partId) {
        if (session == null || StringUtils.isBlank(messageId) || StringUtils.isBlank(partId)) {
            return null;
        }
        List<Message> messages = parseMessages(session.getMessages());
        for (Message message : messages) {
            if (!Objects.equals(messageId, message.getId()) || message.getParts() == null) {
                continue;
            }
            for (ChatMessagePart part : message.getParts()) {
                if (Objects.equals(partId, part.getId())) {
                    return new LocatedPart(messages, message, part);
                }
            }
        }
        return null;
    }

    private List<Message> parseMessages(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    json, new TypeReference<List<Message>>() {});
            return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        } catch (RuntimeException e) {
            throw new ApiException(500, "会话消息解析失败");
        }
    }

    private void validateInfoStepAnswers(ChatMessagePart part,
                                         List<Map<String, Object>> answers) {
        List<Map<String, Object>> steps = listOfMaps(metadata(part).get("steps"));
        if (steps.isEmpty()) {
            return;
        }
        Map<String, String> submitted = new LinkedHashMap<>();
        for (Map<String, Object> answer : answers == null ? List.<Map<String, Object>>of() : answers) {
            String id = text(answer.get("id"));
            String value = text(answer.get("value"));
            if (StringUtils.isBlank(id) || submitted.containsKey(id)) {
                throw new ApiException(400, "提交答案缺少标识或存在重复项");
            }
            submitted.put(id, value);
        }
        for (Map<String, Object> step : steps) {
            String id = text(step.get("id"));
            if (Boolean.TRUE.equals(step.get("required")) && StringUtils.isBlank(submitted.get(id))) {
                throw new ApiException(400, "必填项未提交：" + text(step.get("title")));
            }
            if (Boolean.TRUE.equals(step.get("strictOptions"))
                    && StringUtils.isNotBlank(submitted.get(id))
                    && !optionValues(step.get("suggestions")).contains(submitted.get(id))) {
                throw new ApiException(400, "提交值不在当前卡片的候选项中");
            }
        }
        if (!submitted.keySet().stream().allMatch(id -> steps.stream()
                .anyMatch(step -> Objects.equals(id, text(step.get("id")))))) {
            throw new ApiException(400, "提交内容包含当前卡片不存在的步骤");
        }
    }

    private Set<String> optionValues(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    String value = text(map.get("value"));
                    if (StringUtils.isNotBlank(value)) {
                        values.add(value);
                    }
                } else if (item != null && StringUtils.isNotBlank(item.toString())) {
                    values.add(item.toString().trim());
                }
            }
        }
        return Set.copyOf(values);
    }

    private String genericContinuation(ChatMessagePart part,
                                       AgentRunInteractionResponseDto request,
                                       String action) {
        String title = StringUtils.defaultIfBlank(part.getTitle(), "当前卡片");
        return switch (action) {
            case "submit" -> "用户已提交「" + title + "」补充信息：\n"
                    + JacksonUtil.toJson(request.getAnswers() == null ? List.of() : request.getAnswers())
                    + "\n请基于已提交信息继续当前任务，不要重复询问已填写项。";
            case "revise" -> "用户要求调整「" + title + "」：\n"
                    + StringUtils.defaultIfBlank(request.getRevision(), "请重新生成当前方案。")
                    + "\n请保留已完成的有效结果并继续当前任务。";
            case "retry" -> "用户要求重试「" + title + "」当前失败阶段。请从安全检查点继续。";
            case "force_approve" -> "用户明确知悉风险并确认强制继续「" + title + "」。请严格执行卡片锁定参数。";
            case "reject" -> "用户已拒绝「" + title + "」当前操作。请停止对应操作并说明当前状态。";
            default -> "用户已确认「" + title + "」。请继续执行卡片指定的后续步骤。";
        };
    }

    private String genericSummary(String action, AgentRunInteractionResponseDto request) {
        return switch (action) {
            case "submit" -> "已补充信息";
            case "revise" -> "已提交调整要求";
            case "retry" -> "已请求重试";
            case "reject" -> "已拒绝";
            case "force_approve" -> "已强制确认";
            default -> "已确认";
        };
    }

    private String genericPartStatus(String action) {
        return switch (action) {
            case "submit" -> "submitted";
            case "revise" -> "revise";
            case "retry" -> "pending";
            case "reject" -> "rejected";
            case "force_approve" -> "force_approved";
            default -> "approved";
        };
    }

    private String continuationRequest(Map<String, Object> continuation) {
        if (continuation == null || continuation.isEmpty()) {
            return "";
        }
        String request = text(continuation.get("request"));
        return StringUtils.defaultIfBlank(request, text(continuation.get("display")));
    }

    private Map<String, Object> responsePayload(AgentRunInteractionResponseDto request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", StringUtils.defaultString(request.getMessage()));
        response.put("attachments", request.getAttachments() == null
                ? List.of() : request.getAttachments());
        response.put("action", StringUtils.defaultString(request.getAction()));
        response.put("answers", request.getAnswers() == null ? List.of() : request.getAnswers());
        response.put("stateRevision", request.getStateRevision());
        response.put("revision", StringUtils.defaultString(request.getRevision()));
        response.put("decision", StringUtils.defaultString(request.getDecision()));
        response.put("comment", StringUtils.defaultString(request.getComment()));
        return response;
    }

    private boolean isPendingCard(ChatMessagePart part) {
        if (part == null || !INTERACTIVE_CARD_TYPES.contains(part.getType())) {
            return false;
        }
        String status = StringUtils.trimToEmpty(part.getStatus()).toLowerCase();
        return StringUtils.isBlank(status) || !RESOLVED_CARD_STATUSES.contains(status);
    }

    private List<String> allowedActions(ChatMessagePart part) {
        Object raw = metadata(part).get("allowedActions");
        return stringActions(raw);
    }

    /**
     * 业务卡继续使用服务端显式动作白名单；旧通用卡没有白名单时，只开放
     * 卡片类型本身支持的最小动作集，避免空列表被误解为“全部允许”。
     */
    private List<String> effectiveAllowedActions(ChatMessagePart part) {
        List<String> explicit = allowedActions(part);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        List<String> legacy = stringActions(metadata(part).get("actions"));
        if (!legacy.isEmpty()) {
            return legacy;
        }
        if ("info-steps".equals(part.getType())) {
            return List.of("submit");
        }
        if (DECISION_CARD_TYPES.contains(part.getType())) {
            return List.of("approve", "reject", "revise");
        }
        return List.of();
    }

    private List<String> stringActions(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        for (Object value : values) {
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                actions.add(value.toString().trim());
            }
        }
        return actions;
    }

    private Map<String, Object> metadata(ChatMessagePart part) {
        return part.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(part.getMetadata());
    }

    private void requireStatus(AgentRun run, AgentRunStatus status) {
        if (run.getStatus() != status) {
            throw new ApiException(409, "任务当前状态不能响应该交互");
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String textOrNull(Object value) {
        String text = text(value);
        return StringUtils.isBlank(text) ? null : text;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    map.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private record LocatedPart(List<Message> messages, Message message, ChatMessagePart part) {
    }
}
