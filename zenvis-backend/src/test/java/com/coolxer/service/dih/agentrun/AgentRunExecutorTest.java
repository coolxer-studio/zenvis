package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.dao.mysql.repository.UserRepository;
import com.coolxer.model.dih.ContextCompactionEventData;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihChatApplicationService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.workflow.DataPushPlanSnapshot;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunExecutorTest {

    private AgentRunService runService;
    private AgentRunRepository runRepository;
    private McpToolInvocationRepository invocationRepository;
    private AgentRunExecutor executor;

    @BeforeEach
    void setUp() {
        runService = mock(AgentRunService.class);
        runRepository = mock(AgentRunRepository.class);
        invocationRepository = mock(McpToolInvocationRepository.class);
        executor = new AgentRunExecutor(
                runService,
                runRepository,
                mock(AgentRunInputRepository.class),
                invocationRepository,
                mock(ChatSessionRepository.class),
                mock(UserRepository.class),
                mock(SkillService.class),
                mock(AgentMcpToolService.class),
                mock(DihChatApplicationService.class),
                mock(ReportDraftService.class),
                mock(ChatAttachmentService.class),
                mock(AgentRunInteractionService.class),
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

    @Test
    void contextCompactionObserverWritesTheUnifiedRunEvent() {
        ContextCompactionEventData data = ContextCompactionEventData.builder()
                .compactionId("compact-1")
                .stage(ContextCompactionEventData.STAGE_MODEL_INPUT)
                .reason("prompt_token_budget")
                .savedTokens(120)
                .build();

        ReflectionTestUtils.invokeMethod(
                executor, "recordCompactionEvent", "run-1", "2", data);

        verify(runService).recordEvent("run-1", "context.compacted", "2", data);
    }

    @Test
    void approvedDataPushCallFromPreviousTurnRunsOnceWithOriginalTurnAndCompletesAudit() {
        String config = "sources: {}\nsinks: {}";
        McpToolInvocation invocation = dataPushInvocation(config, "validation-1");
        ChatSession session = new ChatSession().setType("agent_data_push_service");
        when(invocationRepository.findByAgentRunIdAndStatusIn(
                "run-1", List.of(McpInvocationStatus.APPROVED)))
                .thenReturn(List.of(invocation));
        when(invocationRepository.findByRequestId("request-1"))
                .thenAnswer(ignored -> Optional.of(invocation));
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("push_task_create_and_start");
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call(anyString(), any(ToolContext.class))).thenReturn("{\"taskId\":31}");
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[]{callback});
        McpInvocationContext currentContext = new McpInvocationContext(
                McpInvocationChannel.BACKGROUND_AGENT,
                1,
                "chat-1",
                "run:run-1:2",
                "agent_data_push_service",
                null,
                null,
                null);
        McpToolContext toolContext = new McpToolContext(provider, "prompt")
                .withInvocationContext(currentContext);
        AgentRunExecutionState state = new AgentRunExecutionState("run-1", "2");

        ReflectionTestUtils.invokeMethod(executor, "resumeApprovedTools",
                new AgentRun().setRunId("run-1"), session, "2", toolContext, state);

        assertThat(invocation.getStatus()).isEqualTo(McpInvocationStatus.SUCCEEDED);
        assertThat(state.hasProgress()).isTrue();
        ArgumentCaptor<ToolContext> restoredContext = ArgumentCaptor.forClass(ToolContext.class);
        verify(callback).call(anyString(), restoredContext.capture());
        McpInvocationContext restored = (McpInvocationContext) restoredContext.getValue()
                .getContext().get(McpInvocationContext.TOOL_CONTEXT_KEY);
        assertThat(restored.turnId()).isEqualTo("run:run-1:1");

        when(invocationRepository.findByAgentRunIdAndStatusIn(
                "run-1", List.of(McpInvocationStatus.APPROVED))).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(executor, "resumeApprovedTools",
                new AgentRun().setRunId("run-1"), session, "2", toolContext,
                new AgentRunExecutionState("run-1", "2"));
        verify(callback, times(1)).call(anyString(), any(ToolContext.class));
    }

    private McpToolInvocation dataPushInvocation(String config, String validationDigest) {
        String metaContent = "{\"entity\":[]}";
        String candidateDigest = DataPushPlanSnapshot.digest(config);
        String metaDigest = DataPushPlanSnapshot.digest(metaContent);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "探针推送");
        request.put("config", config);
        request.put("candidateDigest", candidateDigest);
        request.put("metaDigest", metaDigest);
        request.put("validationDigest", validationDigest);
        request.put("mark", "data-push:workflow-1");
        request.put("metaFileName", "user_event.json");
        request.put("metaContent", metaContent);
        request.put("source", "SYSTEM");
        return new McpToolInvocation()
                .setRequestId("request-1")
                .setToolName("push_task_create_and_start")
                .setArguments(JacksonUtil.toJson(Map.of("request", request)))
                .setAgentRunId("run-1")
                .setTurnId("run:run-1:1")
                .setStatus(McpInvocationStatus.APPROVED);
    }

    private AgentRunPlanStep step(String id, String status) {
        return AgentRunPlanStep.builder().id(id).title("步骤" + id).status(status).build();
    }
}
