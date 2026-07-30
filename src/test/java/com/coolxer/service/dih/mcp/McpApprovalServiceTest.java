package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpApprovalServiceTest {

    private final Map<String, McpToolInvocation> invocations = new ConcurrentHashMap<>();
    private McpToolPolicyService policyService;
    private McpChatToolGrantService chatToolGrantService;
    private McpTaskToolGrantService taskToolGrantService;
    private McpApprovalService service;

    @BeforeEach
    void setUp() {
        McpToolInvocationRepository repository = mock(McpToolInvocationRepository.class);
        policyService = mock(McpToolPolicyService.class);
        when(repository.save(any(McpToolInvocation.class))).thenAnswer(call -> {
            McpToolInvocation invocation = call.getArgument(0);
            if (invocation.getCreateTime() == null) invocation.setCreateTime(new Date());
            invocations.put(invocation.getRequestId(), invocation);
            return invocation;
        });
        when(repository.saveAndFlush(any(McpToolInvocation.class))).thenAnswer(call -> {
            McpToolInvocation invocation = call.getArgument(0);
            if (invocation.getCreateTime() == null) invocation.setCreateTime(new Date());
            invocations.put(invocation.getRequestId(), invocation);
            return invocation;
        });
        when(repository.saveAllAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findByRequestId(any())).thenAnswer(call ->
                Optional.ofNullable(invocations.get(call.getArgument(0))));
        when(repository.findByChatIdAndRequesterUserIdAndToolKeyAndStatus(anyString(), any(), anyString(), any()))
                .thenAnswer(call -> invocations.values().stream()
                        .filter(item -> call.getArgument(0).equals(item.getChatId()))
                        .filter(item -> call.getArgument(1).equals(item.getRequesterUserId()))
                        .filter(item -> call.getArgument(2).equals(item.getToolKey()))
                        .filter(item -> call.getArgument(3) == item.getStatus())
                        .toList());
        when(repository.findByTaskExecutionIdAndToolKeyAndStatus(anyString(), anyString(), any()))
                .thenAnswer(call -> invocations.values().stream()
                        .filter(item -> call.getArgument(0).equals(item.getTaskExecutionId()))
                        .filter(item -> call.getArgument(1).equals(item.getToolKey()))
                        .filter(item -> call.getArgument(2) == item.getStatus())
                        .toList());
        chatToolGrantService = mock(McpChatToolGrantService.class);
        taskToolGrantService = mock(McpTaskToolGrantService.class);
        service = new McpApprovalService(
                repository, policyService, chatToolGrantService, taskToolGrantService, new ObjectMapper(), 3);
    }

    @Test
    void allowExecutesImmediatelyAndAuditsSuccess() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ALLOW);
        AtomicBoolean called = new AtomicBoolean();

        String result = service.execute(descriptor(), "{\"id\":1}", context(null), () -> {
            called.set(true);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(called).isTrue();
        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getStatus()).isEqualTo(McpInvocationStatus.SUCCEEDED);
            assertThat(invocation.getArguments()).isEqualTo("{\"id\":1}");
            assertThat(invocation.getResult()).isEqualTo("ok");
            assertThat(invocation.getResultLength()).isEqualTo(2L);
        });
    }

    @Test
    void denyNeverCallsDelegate() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.DENY);
        AtomicBoolean called = new AtomicBoolean();

        String result = service.execute(descriptor(), "{}", context(null), () -> {
            called.set(true);
            return "should-not-run";
        });

        assertThat(called).isFalse();
        assertThat(result).contains("denied");
        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getStatus()).isEqualTo(McpInvocationStatus.DENIED);
            assertThat(invocation.getResult()).isNull();
            assertThat(invocation.getResultLength()).isNull();
        });
    }

    @Test
    void backgroundAutoTaskApprovesAskButKeepsAuditScope() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        AtomicBoolean called = new AtomicBoolean();

        String result = service.execute(descriptor(), "{\"id\":1}", taskContext(AnalysisTaskApprovalMode.AUTO), () -> {
            called.set(true);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(called).isTrue();
        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getStatus()).isEqualTo(McpInvocationStatus.SUCCEEDED);
            assertThat(invocation.getApprovalScope()).isEqualTo(McpApprovalScope.TASK_AUTO);
            assertThat(invocation.getAnalysisTaskId()).isEqualTo(7);
            assertThat(invocation.getTaskExecutionId()).isEqualTo("execution-7");
            assertThat(invocation.getDecisionComment()).contains("自动批准");
        });
    }

    @Test
    void backgroundManualTaskWaitsWithoutChatTimeoutAndCanGrantCurrentExecution() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = user(42, false);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":1}", taskContext(AnalysisTaskApprovalMode.MANUAL), () -> "first"));
        McpToolInvocation pending = awaitPendingInvocation();
        assertThat(pending.getExpireTime()).isNull();

        service.decideTask(7, pending.getRequestId(), "approved_task", null, requester);

        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(pending.getApprovalScope()).isEqualTo(McpApprovalScope.TASK_RUN);
        verify(taskToolGrantService).grant(pending, 42);
    }

    @Test
    void auditStoresSensitiveArgumentsAndResultWithoutChangingToolResult() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ALLOW);

        String arguments = "{\"token\":\"secret-argument\"}";
        String result = service.execute(descriptor(), arguments, context(null),
                () -> "{\"accessToken\":\"secret-result\",\"value\":1}");

        assertThat(result).contains("secret-result");
        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getArguments()).isEqualTo(arguments);
            assertThat(invocation.getResult()).isEqualTo(result);
            assertThat(invocation.getResultLength())
                    .isEqualTo((long) result.getBytes(StandardCharsets.UTF_8).length);
        });
    }

    @Test
    void auditKeepsPayloadsBeyondLegacySummaryLimits() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ALLOW);
        String arguments = "{\"payload\":\"" + "a".repeat(5000) + "\"}";
        String expectedResult = "{\"message\":\"" + "中文🙂".repeat(2500) + "\"}";

        String result = service.execute(descriptor(), arguments, context(null), () -> expectedResult);

        assertThat(result).isEqualTo(expectedResult);
        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getArguments()).isEqualTo(arguments);
            assertThat(invocation.getResult()).isEqualTo(expectedResult);
            assertThat(invocation.getResult()).doesNotEndWith("...");
            assertThat(invocation.getResultLength())
                    .isEqualTo((long) expectedResult.getBytes(StandardCharsets.UTF_8).length);
        });
    }

    @Test
    void manualResultUsesFullSerializedJsonAndPreTruncationLength() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ALLOW);
        Map<String, Object> result = Map.of("message", "中文🙂", "ok", true);
        String serialized = new ObjectMapper().writeValueAsString(result);
        McpApprovalService.ManualGate gate = service.prepareManual(
                descriptor(), "{\"id\":1}", user(42, false), null);

        service.completeManual(gate.invocation().getRequestId(), () -> result);

        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getResult()).isEqualTo(serialized);
            assertThat(invocation.getResultLength())
                    .isEqualTo((long) serialized.getBytes(StandardCharsets.UTF_8).length);
        });
    }

    @Test
    void successfulNullResultKeepsLengthNull() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ALLOW);

        assertThat(service.execute(descriptor(), "{}", context(null), () -> null)).isNull();

        assertThat(invocations.values()).singleElement().satisfies(invocation -> {
            assertThat(invocation.getResult()).isNull();
            assertThat(invocation.getResultLength()).isNull();
        });
    }

    @Test
    void askWaitsForRequesterAndRejectsOnlyOnce() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        AtomicBoolean called = new AtomicBoolean();
        List<McpApprovalEvent> events = new CopyOnWriteArrayList<>();
        User requester = new User();
        requester.setId(42);

        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"token\":\"secret\",\"name\":\"demo\"}", context(events), () -> {
                    called.set(true);
                    return "should-not-run";
        }));

        McpToolInvocation pending = awaitPendingInvocation();
        assertThat(pending.getArguments()).isEqualTo("{\"token\":\"secret\",\"name\":\"demo\"}");
        service.decide(pending.getRequestId(), "rejected", "not now", requester);

        assertThat(result.get(2, TimeUnit.SECONDS)).contains("rejected");
        assertThat(called).isFalse();
        assertThat(events).extracting(McpApprovalEvent::event)
                .contains("approval_required", "approval_updated");
        assertThatThrownBy(() -> service.decide(pending.getRequestId(), "approved", null, requester))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    void approveOnceDoesNotGrantLaterCalls() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = user(42, false);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":1}", context(null), () -> "first"));
        McpToolInvocation firstPending = awaitPendingInvocation();
        service.decide(firstPending.getRequestId(), "approved", null, requester);

        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(firstPending.getApprovalScope())
                .isEqualTo(com.coolxer.commons.enums.McpApprovalScope.ONCE);

        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":2}", context(null), () -> "second"));
        McpToolInvocation secondPending = awaitPendingInvocation();
        assertThat(secondPending.getRequestId()).isNotEqualTo(firstPending.getRequestId());
        service.decide(secondPending.getRequestId(), "rejected", null, requester);

        assertThat(second.get(2, TimeUnit.SECONDS)).contains("rejected");
        verify(chatToolGrantService, never()).grant(any(), any());
    }

    @Test
    void sessionApprovalGrantsLaterCallsWithoutAnotherApprovalEvent() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        AtomicBoolean sessionGranted = new AtomicBoolean();
        when(chatToolGrantService.isGranted(any(), anyString())).thenAnswer(call -> sessionGranted.get());
        doAnswer(call -> {
            sessionGranted.set(true);
            return null;
        }).when(chatToolGrantService).grant(any(), any());
        List<McpApprovalEvent> events = new CopyOnWriteArrayList<>();
        User requester = user(42, false);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":1}", context(events), () -> "first"));
        McpToolInvocation pending = awaitPendingInvocation();
        service.decide(pending.getRequestId(), "approved_session", null, requester);

        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(pending.getApprovalScope())
                .isEqualTo(com.coolxer.commons.enums.McpApprovalScope.SESSION);
        assertThat(service.execute(descriptor(), "{\"id\":2}", context(events), () -> "second"))
                .isEqualTo("second");
        assertThat(events).filteredOn(event -> "approval_required".equals(event.event())).hasSize(1);
        assertThat(invocations.values()).filteredOn(item -> "{\"id\":2}".equals(item.getArguments()))
                .singleElement()
                .extracting(McpToolInvocation::getApprovalScope)
                .isEqualTo(com.coolxer.commons.enums.McpApprovalScope.SESSION);
    }

    @Test
    void deterministicDemoForcesOneTimeApprovalDespiteAllowPolicyAndChatGrant()
            throws Exception {
        when(policyService.effectivePolicy(anyString(), any()))
                .thenReturn(McpApprovalPolicy.ALLOW);
        when(chatToolGrantService.isGranted(any(), anyString()))
                .thenReturn(true);
        List<McpApprovalEvent> events = new CopyOnWriteArrayList<>();
        AtomicBoolean called = new AtomicBoolean();
        User requester = user(42, false);

        CompletableFuture<String> result = CompletableFuture.supplyAsync(
                () -> service.execute(
                        descriptor(),
                        "{\"id\":1}",
                        demoContext(events),
                        () -> {
                            called.set(true);
                            return "ok";
                        }));

        McpToolInvocation pending = awaitPendingInvocation();
        assertThat(called).isFalse();
        assertThat(pending.getPolicySnapshot())
                .isEqualTo(McpApprovalPolicy.ASK);
        assertThat(pending.getMcpClientInfo()).isEqualTo(
                McpInvocationContext.BUILTIN_DATA_VISUALIZATION_DEMO);
        assertThat(events)
                .filteredOn(event ->
                        "approval_required".equals(event.event()))
                .singleElement()
                .satisfies(event -> assertThat(
                        event.data().getSessionApprovalAllowed())
                        .isFalse());
        verify(chatToolGrantService, never())
                .isGranted(any(), anyString());

        assertThatThrownBy(() -> service.decide(
                pending.getRequestId(),
                "approved_session",
                null,
                requester))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("必须逐次审批");
        service.decide(
                pending.getRequestId(),
                "approved",
                null,
                requester);

        assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
        assertThat(called).isTrue();
        assertThat(pending.getApprovalScope())
                .isEqualTo(McpApprovalScope.ONCE);
    }

    @Test
    void deterministicDemoStillHonorsGlobalDeny() {
        when(policyService.effectivePolicy(anyString(), any()))
                .thenReturn(McpApprovalPolicy.DENY);
        AtomicBoolean called = new AtomicBoolean();

        String result = service.execute(
                descriptor(),
                "{}",
                demoContext(null),
                () -> {
                    called.set(true);
                    return "should-not-run";
                });

        assertThat(result).contains("denied");
        assertThat(called).isFalse();
        assertThat(invocations.values()).singleElement()
                .extracting(McpToolInvocation::getPolicySnapshot)
                .isEqualTo(McpApprovalPolicy.DENY);
    }

    @Test
    void sessionApprovalReleasesParallelPendingCalls() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = user(42, false);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":1}", context(null), () -> "first"));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{\"id\":2}", context(null), () -> "second"));

        List<McpToolInvocation> pending = awaitPendingInvocations(2);
        service.decide(pending.get(0).getRequestId(), "approved_session", null, requester);

        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("second");
        assertThat(pending).extracting(McpToolInvocation::getApprovalScope)
                .containsOnly(com.coolxer.commons.enums.McpApprovalScope.SESSION);
    }

    @Test
    void globalDenyOverridesExistingSessionGrant() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.DENY);
        when(chatToolGrantService.isGranted(any(), anyString())).thenReturn(true);
        AtomicBoolean called = new AtomicBoolean();

        String result = service.execute(descriptor(), "{}", context(null), () -> {
            called.set(true);
            return "should-not-run";
        });

        assertThat(result).contains("denied");
        assertThat(called).isFalse();
        verify(chatToolGrantService, never()).isGranted(any(), anyString());
    }

    @Test
    void sessionApprovalIsRejectedOutsideChatChannel() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = user(42, false);
        McpApprovalService.ManualGate pending = service.prepareManual(descriptor(), "{}", requester, null);

        assertThatThrownBy(() -> service.decide(
                pending.invocation().getRequestId(), "approved_session", null, requester))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅DIH聊天");
        verify(chatToolGrantService, never()).grant(any(), any());
    }

    @Test
    void superAdminSessionApprovalGrantsTheRequestersChat() throws Exception {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User admin = user(1, true);
        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> service.execute(
                descriptor(), "{}", context(null), () -> "ok"));
        McpToolInvocation pending = awaitPendingInvocation();

        service.decide(pending.getRequestId(), "approved_session", null, admin);

        assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
        assertThat(pending.getRequesterUserId()).isEqualTo(42);
        assertThat(pending.getDecisionBy()).isEqualTo(1);
        verify(chatToolGrantService).grant(pending, 1);
    }

    @Test
    void manualAskUsesCanonicalDigestAndCanExecuteOnlyOnce() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = new User();
        requester.setId(42);

        McpApprovalService.ManualGate pending = service.prepareManual(
                descriptor(), "{\"b\":2,\"a\":1}", requester, null);
        service.decide(pending.invocation().getRequestId(), "approved", null, requester);

        McpApprovalService.ManualGate approved = service.prepareManual(
                descriptor(), "{\"a\":1,\"b\":2}", requester, pending.invocation().getRequestId());
        assertThat(approved.executable()).isTrue();
        assertThat(approved.invocation().getStatus()).isEqualTo(McpInvocationStatus.RUNNING);
        assertThatThrownBy(() -> service.prepareManual(
                descriptor(), "{\"a\":1,\"b\":2}", requester, pending.invocation().getRequestId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("尚未批准");
    }

    @Test
    void onlyRequesterOrSuperAdminCanDecide() {
        when(policyService.effectivePolicy(anyString(), any())).thenReturn(McpApprovalPolicy.ASK);
        User requester = user(42, false);
        User other = user(43, false);
        User admin = user(1, true);
        McpApprovalService.ManualGate userRequest = service.prepareManual(descriptor(), "{}", requester, null);

        assertThatThrownBy(() -> service.decide(
                userRequest.invocation().getRequestId(), "approved", null, other))
                .isInstanceOf(ApiException.class);
        assertThat(service.decide(userRequest.invocation().getRequestId(), "approved", null, admin).getStatus())
                .isEqualTo(McpInvocationStatus.APPROVED);

        McpApprovalService.ManualGate backgroundRequest = service.prepareManual(descriptor(), "{\"id\":2}", null, null);
        assertThatThrownBy(() -> service.decide(
                backgroundRequest.invocation().getRequestId(), "rejected", null, requester))
                .isInstanceOf(ApiException.class);
        assertThat(service.decide(backgroundRequest.invocation().getRequestId(), "rejected", null, admin).getStatus())
                .isEqualTo(McpInvocationStatus.REJECTED);
    }

    private McpToolInvocation awaitPendingInvocation() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Optional<McpToolInvocation> pending = invocations.values().stream()
                    .filter(item -> item.getStatus() == McpInvocationStatus.PENDING)
                    .findFirst();
            if (pending.isPresent()) return pending.get();
            Thread.sleep(10);
        }
        throw new AssertionError("approval request was not created");
    }

    private List<McpToolInvocation> awaitPendingInvocations(int expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<McpToolInvocation> pending = invocations.values().stream()
                    .filter(item -> item.getStatus() == McpInvocationStatus.PENDING)
                    .toList();
            if (pending.size() >= expected) return pending;
            Thread.sleep(10);
        }
        throw new AssertionError("approval requests were not created");
    }

    private McpInvocationContext context(List<McpApprovalEvent> events) {
        return new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                42,
                "chat-1",
                "turn-1",
                "ask",
                null,
                null,
                events == null ? null : events::add
        );
    }

    private McpInvocationContext demoContext(
            List<McpApprovalEvent> events) {
        return new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                42,
                "chat-demo",
                "turn-demo",
                "agent_data_visualization",
                null,
                McpInvocationContext
                        .BUILTIN_DATA_VISUALIZATION_DEMO,
                events == null ? null : events::add
        );
    }

    private McpInvocationContext taskContext(AnalysisTaskApprovalMode mode) {
        return McpInvocationContext.backgroundTask(
                7,
                "execution-7",
                42,
                mode,
                null,
                () -> false
        );
    }

    private McpToolDescriptor descriptor() {
        return new McpToolDescriptor(
                "local::write_demo",
                McpToolSourceType.LOCAL,
                null,
                "local",
                "ZenVis",
                "write_demo",
                "write_demo",
                "Write demo",
                "writes demo data",
                false,
                true,
                com.coolxer.commons.enums.McpToolRiskLevel.HIGH,
                McpApprovalPolicy.ASK
        );
    }

    private User user(int id, boolean superAdmin) {
        User user = new User();
        user.setId(id);
        user.setIsSuperAdmin(superAdmin);
        return user;
    }
}
