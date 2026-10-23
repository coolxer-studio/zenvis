package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.AgentRunEvent;
import com.coolxer.dao.mysql.entity.AgentRunInput;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.AgentRunEventRepository;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.model.dih.dto.AgentRunSteerDto;
import com.coolxer.model.dih.dto.AgentRunRequestDto;
import com.coolxer.model.dih.vo.AgentRunVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.DihChatExecutionPolicy;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.demo.BuiltinDemoRequestDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunServiceTest {

    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentRunEventRepository eventRepository = mock(AgentRunEventRepository.class);
    private final AgentRunInputRepository inputRepository = mock(AgentRunInputRepository.class);
    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final SkillService skillService = mock(SkillService.class);
    private final BuiltinDemoRequestDetector builtinDemoRequestDetector =
            mock(BuiltinDemoRequestDetector.class);
    private final McpToolInvocationRepository invocationRepository =
            mock(McpToolInvocationRepository.class);
    private final DataSource mysqlDataSource = mock(DataSource.class);
    private final PlatformTransactionManager mysqlTransactionManager =
            mock(PlatformTransactionManager.class);

    private AgentRunService service;
    private final List<AgentRunEvent> appendedEvents = new ArrayList<>();
    private final AtomicLong maxSequence = new AtomicLong();

    @BeforeEach
    void setUp() {
        service = new AgentRunService(runRepository, eventRepository, inputRepository,
                chatSessionRepository, chatSessionService, skillService,
                builtinDemoRequestDetector, invocationRepository, mysqlDataSource,
                mysqlTransactionManager);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "enabledTypes", "*");
        ReflectionTestUtils.setField(service, "disabledTypes", "");
        ReflectionTestUtils.setField(service, "defaultMaxSteps", 30);
        ReflectionTestUtils.setField(service, "defaultMaxDurationSeconds", 1800);
        ReflectionTestUtils.setField(service, "defaultMaxToolCalls", 80);
        ReflectionTestUtils.setField(service, "leaseSeconds", 60L);
        when(runRepository.save(any(AgentRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.maxSequence(anyString()))
                .thenAnswer(invocation -> maxSequence.get());
        when(eventRepository.save(any(AgentRunEvent.class))).thenAnswer(invocation -> {
            AgentRunEvent event = invocation.getArgument(0);
            appendedEvents.add(event);
            maxSequence.set(Math.max(maxSequence.get(), event.getSequence()));
            return event;
        });
        when(invocationRepository.findByAgentRunIdAndStatusIn(anyString(), any()))
                .thenReturn(List.of());
    }

    @Test
    void eventSequenceIsStrictlyIncreasingAndSurvivesStaleSnapshot() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        run.setLastEventSequence(2L);
        maxSequence.set(4L);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        service.recordEvent("run-1", "step.started", "1", java.util.Map.of("step", "1"));
        service.recordEvent("run-1", "step.finished", "1", java.util.Map.of("step", "1"));

        assertThat(appendedEvents).extracting(AgentRunEvent::getSequence).containsExactly(5L, 6L);
        assertThat(run.getLastEventSequence()).isEqualTo(6L);
    }

    @Test
    void duplicateSteerClientMessageIsIdempotentAndWritesNothing() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        User user = user(7);
        AgentRunInput existing = new AgentRunInput();
        existing.setId(1);
        existing.setRunId("run-1");
        existing.setClientMessageId("client-1");
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));
        when(inputRepository.findByRunIdAndClientMessageId("run-1", "client-1"))
                .thenReturn(Optional.of(existing));

        AgentRunSteerDto dto = new AgentRunSteerDto();
        dto.setMessage("补充指令");
        dto.setClientMessageId("client-1");
        AgentRunVo result = service.steer("run-1", dto, user);

        assertThat(result.isSteered()).isTrue();
        assertThat(appendedEvents).isEmpty();
        verify(chatSessionService, never()).appendMessage(any(), any(), any());
        verify(inputRepository, never()).save(any());
    }

    @Test
    void steerWaitingUserClearsPendingQuestionAndRequeuesRun() {
        AgentRun run = run("run-1", AgentRunStatus.WAITING_USER);
        run.setPausedReason("请补充配置值");
        run.setPendingAction("{\"type\":\"user\",\"question\":\"请补充配置值\"}");
        User user = user(7);
        ChatSession session = new ChatSession();
        session.setId(11);
        session.setSessionId("chat-1");
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));
        when(inputRepository.findByRunIdAndClientMessageId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.findBySessionIdAndCreateByForUpdate("chat-1", 7))
                .thenReturn(Optional.of(session));
        when(chatSessionService.appendMessage(any(ChatSession.class), any(), any(User.class)))
                .thenReturn(session);

        AgentRunSteerDto dto = new AgentRunSteerDto();
        dto.setMessage("Source=example; Topic=demo");
        dto.setClientMessageId("client-waiting");
        AgentRunVo result = service.steer("run-1", dto, user);

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(result.getPausedReason()).isNull();
        assertThat(result.getPendingAction()).isNull();
        assertThat(appendedEvents).extracting(AgentRunEvent::getType).containsExactly("steer.queued");
    }

    @Test
    void pauseResumeAndApprovalDecisionApplyDefinedStateTransitions() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        User user = user(7);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        AgentRunVo paused = service.pause("run-1", "人工检查", user);
        assertThat(paused.getStatus()).isEqualTo(AgentRunStatus.PAUSED);
        assertThat(paused.getPausedReason()).isEqualTo("人工检查");

        AgentRunVo resumed = service.resume("run-1", user);
        assertThat(resumed.getStatus()).isEqualTo(AgentRunStatus.QUEUED);

        run.setStatus(AgentRunStatus.WAITING_APPROVAL);
        service.markApprovalUpdated("run-1", "approval-1", "APPROVED", "1");
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(run.getPendingAction()).isNull();
    }

    @Test
    void runningRunEntersCancelingAndTerminalRunIsNotChangedAgain() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        run.setLeaseId("lease-1").setLeaseUntil(new Date(System.currentTimeMillis() + 60_000));
        User user = user(7);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        AgentRunVo canceling = service.cancel("run-1", user);
        assertThat(canceling.getStatus()).isEqualTo(AgentRunStatus.CANCELING);
        assertThat(run.getLeaseId()).isEqualTo("lease-1");
        assertThat(run.getLeaseUntil()).isNotNull();

        AgentRunVo repeated = service.cancel("run-1", user);
        assertThat(repeated.getStatus()).isEqualTo(AgentRunStatus.CANCELING);
        assertThat(run.getLeaseId()).isEqualTo("lease-1");

        run.setStatus(AgentRunStatus.SUCCEEDED);
        AgentRunVo terminal = service.cancel("run-1", user);
        assertThat(terminal.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
    }

    @Test
    void cancelingRunRejectsNewSteerInsteadOfAcceptingAnInputItCannotExecute() {
        AgentRun run = run("run-1", AgentRunStatus.CANCELING);
        User user = user(7);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        AgentRunSteerDto dto = new AgentRunSteerDto();
        dto.setMessage("取消后追加");

        assertThatThrownBy(() -> service.steer("run-1", dto, user))
                .hasMessageContaining("正在取消");
        assertThat(appendedEvents).isEmpty();
        verify(chatSessionService, never()).appendMessage(any(), any(), any());
    }

    @Test
    void lateFailureCannotOverwriteSucceededRun() {
        AgentRun run = run("run-1", AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("已完成");
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        AgentRun result = service.markFailed("run-1", "晚到的心跳失败", "1");

        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(result.getLastError()).isNull();
        assertThat(appendedEvents).isEmpty();
    }

    @Test
    void checkpointTracksNoProgressAndRunWideBudget() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        service.checkpointStep("run-1", 1200, 3, false, null, "1");
        service.checkpointStep("run-1", 800, 2, true, null, "1");

        assertThat(run.getDecisionSteps()).isEqualTo(2);
        assertThat(run.getToolCalls()).isEqualTo(5);
        assertThat(run.getEffectiveMillis()).isEqualTo(2000L);
        assertThat(run.getNoProgressCount()).isZero();
    }

    @Test
    void failedModelAttemptDoesNotConsumeDecisionStepBudget() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        int failures = service.checkpointFailure(
                "run-1", 1200, 0, "404 Not Found", "1");

        assertThat(failures).isEqualTo(1);
        assertThat(run.getDecisionSteps()).isZero();
        assertThat(run.getEffectiveMillis()).isEqualTo(1200L);
        assertThat(run.getNoProgressCount()).isEqualTo(1);
        assertThat(run.getLastError()).isEqualTo("404 Not Found");
    }

    @Test
    void resumingPausedRunGrantsBudgetSoItDoesNotPauseAgainImmediately() {
        AgentRun run = run("run-1", AgentRunStatus.PAUSED);
        run.setDecisionSteps(30)
                .setMaxSteps(30)
                .setEffectiveMillis(1_800_000L)
                .setMaxDurationSeconds(1800)
                .setNoProgressCount(3)
                .setLastError("模型调用连续失败")
                .setPausedReason("已达到最大决策步数");
        User user = user(7);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        AgentRunVo resumed = service.resume("run-1", user);

        assertThat(resumed.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(resumed.getMaxSteps()).isEqualTo(60);
        assertThat(resumed.getMaxDurationSeconds()).isEqualTo(3600);
        assertThat(run.getNoProgressCount()).isZero();
        assertThat(resumed.getLastError()).isNull();
        assertThat(resumed.getPausedReason()).isNull();
    }

    @Test
    void truncationIsCountedPerStepAndStoresIncompleteMarker() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        when(runRepository.findByRunIdForUpdate("run-1")).thenReturn(Optional.of(run));

        assertThat(service.recordTruncation("run-1", "2", "半截内容")).isEqualTo(1);
        assertThat(service.recordTruncation("run-1", "2", "半截内容")).isEqualTo(2);
        assertThat(run.getContextCheckpoint()).contains("不完整", "禁止视为已完成");
        assertThat(service.recordTruncation("run-1", "3", "另一章")).isEqualTo(1);
    }

    @Test
    void allAgentTypesAreEnabledByDefaultAndCanBeRestrictedForRollback() {
        assertThat(service.isEnabledFor("agent_report")).isTrue();
        assertThat(service.isEnabledFor("agent_data_access")).isTrue();
        assertThat(service.isEnabledFor("agent_data_visualization")).isTrue();
        assertThat(service.isEnabledFor("agent_skill")).isTrue();

        ReflectionTestUtils.setField(service, "enabledTypes", "agent_report");
        assertThat(service.isEnabledFor("agent_report")).isTrue();
        assertThat(service.isEnabledFor("agent_data_access")).isFalse();

        ReflectionTestUtils.setField(service, "enabledTypes", "*");
        ReflectionTestUtils.setField(service, "disabledTypes", "agent_data_access");
        assertThat(service.isEnabledFor("agent_report")).isTrue();
        assertThat(service.isEnabledFor("agent_data_access")).isFalse();

        ReflectionTestUtils.setField(service, "enabledTypes", "agent_report,agent_data_access");
        ReflectionTestUtils.setField(service, "disabledTypes", "");
        assertThat(service.isEnabledFor("agent_data_access")).isTrue();
    }

    @Test
    void builtinDemoPromptsNeverCreatePersistentRuns() {
        User user = user(7);
        ChatSession session = new ChatSession();
        session.setId(11);
        session.setSessionId("chat-1");
        AgentRunRequestDto request = new AgentRunRequestDto();
        request.setChatId("chat-1");
        request.setType("agent_report");
        String message = "请基于用户事件数据生成一份分析报告，包含摘要、数据概览、关键发现、图表占位、结论与建议。";
        request.setMessage(message);
        DihChatExecutionPolicy policy = DihChatExecutionPolicy.resolve("agent_report").orElseThrow();
        when(chatSessionRepository.findBySessionIdAndCreateByForUpdate("chat-1", 7))
                .thenReturn(Optional.of(session));
        when(builtinDemoRequestDetector.isBuiltinDemoRequest(
                "agent_report", "chat-1", message, user, session))
                .thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "createLocked", request, message, policy, user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("内置演示");
        verify(runRepository, never()).save(any(AgentRun.class));
        verify(chatSessionService, never()).appendMessage(any(), any(), any());
    }

    @Test
    void activityEventQueryFiltersTypesAndContinuesFromLastSequence() {
        AgentRun run = run("run-1", AgentRunStatus.RUNNING);
        User user = user(7);
        when(runRepository.findByRunIdAndCreateBy("run-1", 7)).thenReturn(Optional.of(run));
        List<AgentRunEvent> firstPage = IntStream.rangeClosed(1, 500)
                .mapToObj(sequence -> event("run-1", sequence, "rag.search"))
                .toList();
        List<AgentRunEvent> secondPage = List.of(
                event("run-1", 501, "tool.started"),
                event("run-1", 502, "context.compacted"));
        List<String> expectedTypes = List.of("rag.search", "tool.started", "context.compacted");
        when(eventRepository.findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
                "run-1", expectedTypes, 0L)).thenReturn(firstPage);
        when(eventRepository.findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
                "run-1", expectedTypes, 500L)).thenReturn(secondPage);

        var first = service.events("run-1", 0,
                List.of(" rag.search ", "", "tool.started", "rag.search", "context.compacted"),
                user);
        var second = service.events("run-1", first.get(first.size() - 1).getSequence(),
                expectedTypes, user);

        assertThat(first).hasSize(500);
        assertThat(second).extracting(event -> event.getType())
                .containsExactly("tool.started", "context.compacted");
        verify(eventRepository)
                .findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
                        "run-1", expectedTypes, 500L);
    }

    @Test
    void eventQueryWithoutTypesKeepsLegacyFullReplayBehavior() {
        AgentRun run = run("run-1", AgentRunStatus.SUCCEEDED);
        User user = user(7);
        when(runRepository.findByRunIdAndCreateBy("run-1", 7)).thenReturn(Optional.of(run));
        when(eventRepository.findTop500ByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
                "run-1", 12L)).thenReturn(List.of(event("run-1", 13, "assistant.delta")));

        var events = service.events("run-1", 12L, user);

        assertThat(events).singleElement()
                .satisfies(event -> assertThat(event.getType()).isEqualTo("assistant.delta"));
        verify(eventRepository).findTop500ByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
                "run-1", 12L);
    }

    @Test
    void eventQueryRejectsRunsOwnedByAnotherUserBeforeReadingEvents() {
        User user = user(7);
        when(runRepository.findByRunIdAndCreateBy("run-1", 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.events("run-1", 0,
                List.of("rag.search"), user))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("任务不存在");
        verify(eventRepository, never())
                .findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
                        anyString(), any(), eq(0L));
    }

    private AgentRunEvent event(String runId, long sequence, String type) {
        return new AgentRunEvent()
                .setRunId(runId)
                .setSequence(sequence)
                .setType(type)
                .setStepId("1")
                .setPayload("{}");
    }

    private AgentRun run(String runId, AgentRunStatus status) {
        AgentRun run = new AgentRun()
                .setRunId(runId)
                .setChatId("chat-1")
                .setChatType("agent_report")
                .setAgentType("agent_report")
                .setStatus(status)
                .setPlanRevision(1)
                .setDecisionSteps(0)
                .setToolCalls(0)
                .setEffectiveMillis(0L)
                .setMaxSteps(30)
                .setMaxDurationSeconds(1800)
                .setMaxToolCalls(80)
                .setNoProgressCount(0)
                .setPlanJson("{\"goal\":\"报表\",\"steps\":[{\"id\":\"1\",\"title\":\"完成\",\"status\":\"RUNNING\"}]}")
                .setCurrentStepId("1")
                .setLastEventSequence(0L);
        run.setId(1);
        run.setCreateBy(7);
        run.setUpdateBy(7);
        run.setCreateTime(new Date());
        return run;
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
