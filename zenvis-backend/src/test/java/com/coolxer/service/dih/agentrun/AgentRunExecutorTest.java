package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.dao.mysql.repository.UserRepository;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.DihChatApplicationService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunExecutorTest {

    private AgentRunService runService;
    private AgentRunRepository runRepository;
    private AgentRunExecutor executor;

    @BeforeEach
    void setUp() {
        runService = mock(AgentRunService.class);
        runRepository = mock(AgentRunRepository.class);
        executor = new AgentRunExecutor(
                runService,
                runRepository,
                mock(AgentRunInputRepository.class),
                mock(McpToolInvocationRepository.class),
                mock(ChatSessionRepository.class),
                mock(ChatSessionService.class),
                mock(UserRepository.class),
                mock(SkillService.class),
                mock(AgentMcpToolService.class),
                mock(DihChatApplicationService.class),
                mock(ReportDraftService.class),
                mock(ChatAttachmentService.class),
                mock(AgentRunContextAssembler.class),
                60L);
    }

    @Test
    void planIsCompleteOnlyWhenEveryStepIsDoneOrSkipped() {
        AgentRunPlan complete = AgentRunPlan.builder().steps(List.of(
                step("1", "DONE"), step("2", "SKIPPED"))).build();
        AgentRunPlan blocked = AgentRunPlan.builder().steps(List.of(
                step("1", "DONE"), step("2", "BLOCKED"))).build();
        AgentRunPlan empty = AgentRunPlan.builder().steps(List.of()).build();

        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor, "planComplete", complete))
                .isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor, "planComplete", blocked))
                .isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor, "planComplete", empty))
                .isFalse();
    }

    @Test
    void truncationMarkerNeverEntersAssistantTextAndIsRecordedAsStepIncomplete() {
        StringBuilder answer = new StringBuilder();
        AtomicBoolean truncated = new AtomicBoolean();

        ReflectionTestUtils.invokeMethod(executor, "handleStreamValue",
                "run-1", "1", "安全的部分内容", answer, truncated);
        ReflectionTestUtils.invokeMethod(executor, "handleStreamValue",
                "run-1", "1", AIChatService.OUTPUT_TRUNCATED_MARKER_PREFIX + "length",
                answer, truncated);

        assertThat(answer.toString()).isEqualTo("安全的部分内容");
        assertThat(truncated).isTrue();
        verify(runService).recordEvent(eq("run-1"), eq("assistant.delta"), eq("1"), any(Map.class));
    }

    @Test
    void cancellationSignalStopsAnActiveModelStream() {
        when(runRepository.findByRunId("run-1")).thenReturn(
                Optional.of(new AgentRun().setStatus(AgentRunStatus.RUNNING)),
                Optional.of(new AgentRun().setStatus(AgentRunStatus.CANCELING)));
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);

        Flux<String> stopped = (Flux<String>) ReflectionTestUtils.invokeMethod(
                executor, "stopWhenCancellationRequested", Flux.<String>never(), "run-1",
                cancellationRequested);

        assertThat(stopped.collectList().block(Duration.ofSeconds(5))).isEmpty();
        assertThat(cancellationRequested).isTrue();
    }

    @Test
    void modelConfigurationErrorsAreNotRetriedButTransientErrorsAre() {
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor,
                "isRetryableModelFailure",
                new IllegalStateException(
                        "404 Not Found from POST http://10.10.5.71:8080/v1/chat/completions")))
                .isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor,
                "isRetryableModelFailure",
                new IllegalStateException("本地模型请求失败，HTTP 401")))
                .isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor,
                "isRetryableModelFailure",
                new IllegalStateException("本地模型请求失败，HTTP 429")))
                .isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor,
                "isRetryableModelFailure",
                new IllegalStateException("本地模型请求失败，HTTP 503")))
                .isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(executor,
                "isRetryableModelFailure",
                new IllegalStateException("connection timed out")))
                .isTrue();
    }

    private AgentRunPlanStep step(String id, String status) {
        return AgentRunPlanStep.builder().id(id).title("步骤" + id).status(status).build();
    }
}
