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
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.agentrun.AgentRunInteraction;
import com.coolxer.model.dih.dto.AgentRunInteractionResponseDto;
import com.coolxer.model.dih.vo.AgentRunInteractionResponseVo;
import com.coolxer.model.dih.vo.WorkflowActionVo;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.workflow.WorkflowActionService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentRunInteractionServiceTest {

    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentRunInputRepository inputRepository = mock(AgentRunInputRepository.class);
    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final WorkflowActionService workflowActionService = mock(WorkflowActionService.class);
    private final McpApprovalService mcpApprovalService = mock(McpApprovalService.class);
    private final ChatAttachmentService chatAttachmentService = mock(ChatAttachmentService.class);

    private AgentRunInteractionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AgentRunInteractionService(runService, inputRepository,
                chatSessionRepository, chatSessionService, workflowActionService,
                mcpApprovalService, chatAttachmentService);
        user = new User();
        user.setId(7);
        when(inputRepository.findFirstByRunIdAndInteractionIdOrderByCreateTimeAsc(
                anyString(), anyString())).thenReturn(Optional.empty());
        when(inputRepository.save(any(AgentRunInput.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void userInputResponseIsAuditedWithoutCreatingChatMessage() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_USER_INPUT);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);
        completeByRequeueing(run);

        AgentRunInteractionResponseVo response = service.respond(
                "run-1", interaction.getInteractionId(), request("补充参数 A"), user);

        assertThat(response.isAccepted()).isTrue();
        assertThat(response.isDuplicate()).isFalse();
        ArgumentCaptor<AgentRunInput> input = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(inputRepository).save(input.capture());
        assertThat(input.getValue().getInteractionId()).isEqualTo(interaction.getInteractionId());
        assertThat(input.getValue().getMessageId()).isEqualTo(interaction.getMessageId());
        assertThat(input.getValue().getPartId()).isEqualTo(interaction.getPartId());
        assertThat(input.getValue().getInteractionKind()).isEqualTo(AgentRunInteraction.KIND_USER_INPUT);
        assertThat(input.getValue().getStatus()).isEqualTo(AgentRunInputStatus.PENDING);
        assertThat(input.getValue().getContent()).isEqualTo("补充参数 A");
        verify(runService).completeInteraction(eq(run), eq(interaction.getInteractionId()),
                eq(run.getCurrentStepId()), eq(true), any());
        verifyNoInteractions(chatSessionRepository, chatSessionService);
    }

    @Test
    void identicalInteractionResponseIsIdempotentAndDifferentPayloadConflicts() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_USER_INPUT);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);
        completeByRequeueing(run);
        AgentRunInteractionResponseDto request = request("补充参数 A");

        service.respond("run-1", interaction.getInteractionId(), request, user);
        ArgumentCaptor<AgentRunInput> firstInput = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(inputRepository).save(firstInput.capture());
        when(inputRepository.findFirstByRunIdAndInteractionIdOrderByCreateTimeAsc(
                "run-1", interaction.getInteractionId())).thenReturn(Optional.of(firstInput.getValue()));

        AgentRunInteractionResponseVo duplicate = service.respond(
                "run-1", interaction.getInteractionId(), request("补充参数 A"), user);

        assertThat(duplicate.isDuplicate()).isTrue();
        assertThatThrownBy(() -> service.respond(
                "run-1", interaction.getInteractionId(), request("不同内容"), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不同内容");
        verify(inputRepository).save(any(AgentRunInput.class));
    }

    @Test
    void rejectsWrongInteractionIdRevisionAndCrossUserRun() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_CARD_DECISION);
        interaction.setWorkflowId("workflow-1");
        interaction.setStateRevision(5);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);

        AgentRunInteractionResponseDto wrongRevision = new AgentRunInteractionResponseDto();
        wrongRevision.setAction("approve");
        wrongRevision.setStateRevision(4);
        assertThatThrownBy(() -> service.respond(
                "run-1", interaction.getInteractionId(), wrongRevision, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("版本");
        AgentRunInteractionResponseDto missingRevision = new AgentRunInteractionResponseDto();
        missingRevision.setAction("approve");
        assertThatThrownBy(() -> service.respond(
                "run-1", interaction.getInteractionId(), missingRevision, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("版本");
        assertThatThrownBy(() -> service.respond(
                "run-1", "another-interaction", request("x"), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("失效");

        when(runService.lockOwnedRun("run-other", user))
                .thenThrow(new ApiException(404, "任务不存在"));
        assertThatThrownBy(() -> service.respond(
                "run-other", interaction.getInteractionId(), request("x"), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("任务不存在");
        verifyNoInteractions(workflowActionService, mcpApprovalService);
    }

    @Test
    void genericInfoStepsValidatesRequiredAndStrictOptions() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_CARD_DECISION);
        interaction.setStateRevision(3);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);
        ChatSession session = sessionWithPart(infoStepsPart());
        when(chatSessionRepository.findBySessionIdAndCreateByForUpdate("chat-1", 7))
                .thenReturn(Optional.of(session));
        completeByRequeueing(run);

        AgentRunInteractionResponseDto missing = cardRequest(3, "submit", List.of());
        assertThatThrownBy(() -> service.respond(
                "run-1", interaction.getInteractionId(), missing, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("必填项");

        AgentRunInteractionResponseDto strict = cardRequest(3, "submit",
                List.of(Map.of("id", "topic", "value", "未授权选项")));
        assertThatThrownBy(() -> service.respond(
                "run-1", interaction.getInteractionId(), strict, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("候选项");
        verify(inputRepository, never()).save(any(AgentRunInput.class));

        AgentRunInteractionResponseDto valid = cardRequest(3, "submit",
                List.of(Map.of("id", "topic", "value", "lab-topic")));
        AgentRunInteractionResponseVo response = service.respond(
                "run-1", interaction.getInteractionId(), valid, user);

        assertThat(response.getPartStatus()).isEqualTo("submitted");
        assertThat(session.getMessages()).contains("\"status\":\"submitted\"");
        List<Message> messages = JacksonUtil.toList(session.getMessages(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSender()).isEqualTo("ai");
        ArgumentCaptor<AgentRunInput> input = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(inputRepository).save(input.capture());
        assertThat(input.getValue().getInteractionId()).isEqualTo(interaction.getInteractionId());
    }

    @Test
    void workflowCardUsesExistingValidationAndPersistsContinuationAudit() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_CARD_DECISION);
        interaction.setWorkflowId("workflow-1");
        interaction.setStateRevision(8);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);
        ChatSession session = sessionWithPart(confirmPart());
        when(chatSessionRepository.findBySessionIdAndCreateByForUpdate("chat-1", 7))
                .thenReturn(Optional.of(session));
        when(workflowActionService.handleForAgentRun(any(), eq(user))).thenReturn(
                new WorkflowActionVo(true, "workflow-1", "APPLY", "approved",
                        Map.of("request", "继续执行已确认配置"), false, "{}"));
        completeByRequeueing(run);

        AgentRunInteractionResponseVo response = service.respond(
                "run-1", interaction.getInteractionId(), cardRequest(8, "approve", List.of()), user);

        assertThat(response.getPartStatus()).isEqualTo("approved");
        List<Message> messages = JacksonUtil.toList(session.getMessages(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
        assertThat(messages.get(0).getParts().get(0).getStatus()).isEqualTo("approved");
        assertThat(messages.get(0).getParts().get(0).getMetadata())
                .containsKey("interactionResponse");
        verify(workflowActionService).handleForAgentRun(any(), eq(user));
        verify(workflowActionService, never()).handle(any(), any());
        ArgumentCaptor<AgentRunInput> input = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(inputRepository).save(input.capture());
        assertThat(input.getValue().getContent()).isEqualTo("继续执行已确认配置");
        assertThat(input.getValue().getStatus()).isEqualTo(AgentRunInputStatus.PENDING);
        assertThat(input.getValue().getPartId()).isEqualTo(interaction.getPartId());
        assertThat(input.getValue().getWorkflowId()).isEqualTo("workflow-1");
    }

    @Test
    void approvalKeepsRunApprovalSemanticsAndAuditsDecision() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_MCP_APPROVAL);
        interaction.setRequestId("approval-1");
        AgentRun run = run(AgentRunStatus.WAITING_APPROVAL, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);

        AgentRunInteractionResponseDto request = new AgentRunInteractionResponseDto();
        request.setDecision("approved_run");
        request.setComment("本任务允许");
        AgentRunInteractionResponseVo response = service.respond(
                "run-1", interaction.getInteractionId(), request, user);

        assertThat(response.getPartStatus()).isEqualTo("approved_run");
        verify(mcpApprovalService).decide("approval-1", "approved_run", "本任务允许", user);
        ArgumentCaptor<AgentRunInput> input = ArgumentCaptor.forClass(AgentRunInput.class);
        verify(inputRepository).save(input.capture());
        assertThat(input.getValue().getStatus()).isEqualTo(AgentRunInputStatus.APPLIED);
        assertThat(input.getValue().getContent()).isEqualTo("本任务允许");
        assertThat(input.getValue().getRequestId()).isEqualTo("approval-1");
        verify(runService).completeInteraction(eq(run), eq(interaction.getInteractionId()),
                eq(run.getCurrentStepId()), eq(false), any());
    }

    @Test
    void genericCardWithoutAllowedActionsUsesSafeTypeDefaults() {
        AgentRunInteraction interaction = interaction(AgentRunInteraction.KIND_CARD_DECISION);
        interaction.setWorkflowId(null);
        interaction.setStateRevision(0);
        AgentRun run = run(AgentRunStatus.WAITING_USER, interaction);
        when(runService.lockOwnedRun("run-1", user)).thenReturn(run);
        ChatSession session = sessionWithPart(part("part-1", "confirm", "pending", Map.of()));
        when(chatSessionRepository.findBySessionIdAndCreateByForUpdate("chat-1", 7))
                .thenReturn(Optional.of(session));
        completeByRequeueing(run);

        assertThatThrownBy(() -> service.respond("run-1", interaction.getInteractionId(),
                cardRequest(0, "add_to_library", List.of()), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不允许");

        AgentRunInteractionResponseVo response = service.respond(
                "run-1", interaction.getInteractionId(), cardRequest(0, "approve", List.of()), user);
        assertThat(response.getPartStatus()).isEqualTo("approved");
        verify(inputRepository).save(any(AgentRunInput.class));
    }

    @Test
    void cardSelectionPrefersHighestWorkflowRevisionAndIsStable() {
        ChatMessagePart generic = part("generic", "info-steps", "pending",
                Map.of("allowedActions", List.of("submit")));
        ChatMessagePart oldWorkflow = part("old", "confirm", "pending",
                Map.of("workflowId", "workflow-1", "stateRevision", 2,
                        "allowedActions", List.of("approve")));
        ChatMessagePart currentWorkflow = part("current", "confirm", "pending",
                Map.of("workflowId", "workflow-1", "stateRevision", 4,
                        "allowedActions", List.of("approve")));
        ChatMessagePart resolved = part("resolved", "confirm", "force_approved",
                Map.of("allowedActions", List.of("approve")));
        Message message = message(resolved, generic, oldWorkflow, currentWorkflow);

        AgentRunInteraction selected = service.cardInteraction(message, "请确认");
        AgentRunInteraction selectedAgain = service.cardInteraction(message, "请确认");

        assertThat(selected.getPartId()).isEqualTo("current");
        assertThat(selected.getStateRevision()).isEqualTo(4);
        assertThat(selected.getInteractionId()).isNotBlank();
        assertThat(selectedAgain.getPartId()).isEqualTo("current");

        AgentRunInteraction genericSelected = service.cardInteraction(message(generic), "请补充");
        assertThat(genericSelected.getStateRevision()).isZero();
    }

    private void completeByRequeueing(AgentRun run) {
        doAnswer(invocation -> {
            run.setInteractionJson(null).setPendingAction(null).setStatus(AgentRunStatus.QUEUED);
            return null;
        }).when(runService).completeInteraction(eq(run), anyString(), any(), eq(true), any());
    }

    private AgentRunInteraction interaction(String kind) {
        return AgentRunInteraction.builder()
                .interactionId("interaction-1")
                .kind(kind)
                .messageId("message-1")
                .partId("part-1")
                .title("待处理")
                .prompt("请确认")
                .allowedActions(List.of("submit", "approve", "reject", "revise", "retry"))
                .createdAt(new Date())
                .build();
    }

    private AgentRun run(AgentRunStatus status, AgentRunInteraction interaction) {
        AgentRun run = new AgentRun()
                .setRunId("run-1")
                .setChatId("chat-1")
                .setChatType("agent_metadata_config")
                .setAgentType("agent_metadata_config")
                .setStatus(status)
                .setCurrentStepId("step-1")
                .setInteractionJson(JacksonUtil.toJson(interaction))
                .setLastEventSequence(1L);
        run.setCreateBy(7);
        return run;
    }

    private AgentRunInteractionResponseDto request(String message) {
        AgentRunInteractionResponseDto request = new AgentRunInteractionResponseDto();
        request.setMessage(message);
        return request;
    }

    private AgentRunInteractionResponseDto cardRequest(int revision,
                                                       String action,
                                                       List<Map<String, Object>> answers) {
        AgentRunInteractionResponseDto request = new AgentRunInteractionResponseDto();
        request.setAction(action);
        request.setAnswers(answers);
        request.setStateRevision(revision);
        return request;
    }

    private ChatSession sessionWithPart(ChatMessagePart part) {
        Message message = message(part);
        ChatSession session = new ChatSession()
                .setSessionId("chat-1")
                .setType("agent_metadata_config")
                .setMessages(JacksonUtil.toJson(List.of(message)))
                .setExtraData("{}");
        session.setId(1);
        session.setCreateBy(7);
        return session;
    }

    private Message message(ChatMessagePart... parts) {
        Message message = new Message("ai", "");
        message.setId("message-1");
        message.setRunId("run-1");
        message.setParts(List.of(parts));
        return message;
    }

    private ChatMessagePart infoStepsPart() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", "topic");
        step.put("title", "Topic");
        step.put("required", true);
        step.put("strictOptions", true);
        step.put("suggestions", List.of(Map.of("value", "lab-topic", "label", "实验 Topic")));
        return part("part-1", "info-steps", "pending", Map.of(
                "allowedActions", List.of("submit"),
                "steps", List.of(step)));
    }

    private ChatMessagePart confirmPart() {
        return part("part-1", "confirm", "pending", Map.of(
                "workflowId", "workflow-1",
                "stateRevision", 8,
                "allowedActions", List.of("approve", "reject", "revise")));
    }

    private ChatMessagePart part(String id, String type, String status, Map<String, Object> metadata) {
        return ChatMessagePart.builder()
                .id(id)
                .type(type)
                .status(status)
                .title("测试卡片")
                .content("测试内容")
                .metadata(metadata)
                .build();
    }
}
