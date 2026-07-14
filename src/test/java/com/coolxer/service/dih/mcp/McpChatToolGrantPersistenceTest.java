package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpToolRiskLevel;
import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.dao.mysql.entity.McpTaskToolGrant;
import com.coolxer.dao.mysql.entity.McpTaskToolGrantId;
import com.coolxer.dao.mysql.entity.McpChatToolGrant;
import com.coolxer.dao.mysql.entity.McpChatToolGrantId;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.McpChatToolGrantRepository;
import com.coolxer.dao.mysql.repository.McpTaskToolGrantRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpInvocationSearchDto;
import com.coolxer.model.dih.vo.McpApprovalVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpChatToolGrantPersistenceTest {

    @Autowired
    private McpChatToolGrantService grantService;

    @Autowired
    private McpChatToolGrantRepository grantRepository;

    @Autowired
    private McpTaskToolGrantService taskGrantService;

    @Autowired
    private McpTaskToolGrantRepository taskGrantRepository;

    @Autowired
    private McpToolInvocationRepository invocationRepository;

    @Autowired
    private McpApprovalService approvalService;

    @Test
    void sessionGrantUpsertRunsInMysqlTransactionAndCanBeRevoked() {
        String suffix = UUID.randomUUID().toString();
        String chatId = "approval-test-" + suffix;
        String toolKey = "local::approval_test_" + suffix;
        Integer requesterUserId = -10001;
        Integer grantedBy = -10002;
        String requestId = UUID.randomUUID().toString();
        McpChatToolGrantId grantId = new McpChatToolGrantId(chatId, requesterUserId, toolKey);
        McpToolInvocation invocation = new McpToolInvocation()
                .setChatId(chatId)
                .setRequesterUserId(requesterUserId)
                .setToolKey(toolKey)
                .setRequestId(requestId);

        try {
            grantService.grant(invocation, grantedBy);

            McpChatToolGrant grant = grantRepository.findById(grantId).orElseThrow();
            assertThat(grant.getGrantedBy()).isEqualTo(grantedBy);
            assertThat(grant.getSourceRequestId()).isEqualTo(requestId);

            grantService.revokeChat(chatId, requesterUserId);
            assertThat(grantRepository.existsById(grantId)).isFalse();
        } finally {
            grantService.revokeChat(chatId, requesterUserId);
        }
    }

    @Test
    void taskGrantUpsertIsScopedToExecutionAndCanBeRevoked() {
        String suffix = UUID.randomUUID().toString();
        String executionId = "task-execution-" + suffix;
        String toolKey = "local::task_approval_test_" + suffix;
        String requestId = UUID.randomUUID().toString();
        McpTaskToolGrantId grantId = new McpTaskToolGrantId(executionId, toolKey);
        McpToolInvocation invocation = new McpToolInvocation()
                .setAnalysisTaskId(-20001)
                .setTaskExecutionId(executionId)
                .setRequesterUserId(-20002)
                .setToolKey(toolKey)
                .setRequestId(requestId);

        try {
            taskGrantService.grant(invocation, -20003);

            McpTaskToolGrant grant = taskGrantRepository.findById(grantId).orElseThrow();
            assertThat(grant.getAnalysisTaskId()).isEqualTo(-20001);
            assertThat(grant.getGrantedBy()).isEqualTo(-20003);
            assertThat(grant.getSourceRequestId()).isEqualTo(requestId);

            taskGrantService.revokeExecution(executionId);
            assertThat(taskGrantRepository.existsById(grantId)).isFalse();
        } finally {
            taskGrantService.revokeExecution(executionId);
        }
    }

    @Test
    void approvedTaskDecisionPersistsGrantAndTaskAuditFields() {
        String suffix = UUID.randomUUID().toString();
        String executionId = "task-decision-" + suffix;
        String toolKey = "local::task_decision_test_" + suffix;
        String requestId = UUID.randomUUID().toString();
        Integer taskId = -20004;
        Integer requesterUserId = -20005;
        McpTaskToolGrantId grantId = new McpTaskToolGrantId(executionId, toolKey);
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId(requestId)
                .setToolKey(toolKey)
                .setToolName("task_decision_test")
                .setRiskLevel(McpToolRiskLevel.HIGH)
                .setChannel(McpInvocationChannel.BACKGROUND_AGENT)
                .setPolicySnapshot(McpApprovalPolicy.ASK)
                .setStatus(McpInvocationStatus.PENDING)
                .setRequesterUserId(requesterUserId)
                .setAnalysisTaskId(taskId)
                .setTaskExecutionId(executionId)
                .setTurnId(executionId);
        User requester = new User();
        requester.setId(requesterUserId);

        try {
            invocationRepository.saveAndFlush(invocation);

            McpApprovalVo result = approvalService.decideTask(
                    taskId, requestId, "approved_task", null, requester);

            assertThat(result.getStatus()).isEqualTo(McpInvocationStatus.APPROVED);
            assertThat(result.getApprovalScope()).isEqualTo(McpApprovalScope.TASK_RUN);
            assertThat(result.getAnalysisTaskId()).isEqualTo(taskId);
            assertThat(result.getTaskExecutionId()).isEqualTo(executionId);
            assertThat(taskGrantRepository.existsById(grantId)).isTrue();
            McpToolInvocation persisted = invocationRepository.findByRequestId(requestId).orElseThrow();
            assertThat(persisted.getApprovalScope()).isEqualTo(McpApprovalScope.TASK_RUN);
            assertThat(persisted.getDecisionBy()).isEqualTo(requesterUserId);
        } finally {
            taskGrantService.revokeExecution(executionId);
            invocationRepository.findByRequestId(requestId).ifPresent(invocationRepository::delete);
        }
    }

    @Test
    void approveSessionDecisionPersistsGrantAndUpdatesInvocationInMysqlTransaction() {
        String suffix = UUID.randomUUID().toString();
        String chatId = "approval-decision-test-" + suffix;
        String toolKey = "local::approval_decision_test_" + suffix;
        String requestId = UUID.randomUUID().toString();
        Integer requesterUserId = -10003;
        McpChatToolGrantId grantId = new McpChatToolGrantId(chatId, requesterUserId, toolKey);
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId(requestId)
                .setToolKey(toolKey)
                .setToolName("approval_decision_test")
                .setRiskLevel(McpToolRiskLevel.HIGH)
                .setChannel(McpInvocationChannel.CHAT_AGENT)
                .setPolicySnapshot(McpApprovalPolicy.ASK)
                .setStatus(McpInvocationStatus.PENDING)
                .setRequesterUserId(requesterUserId)
                .setChatId(chatId)
                .setTurnId(UUID.randomUUID().toString())
                .setExpireTime(new Date(System.currentTimeMillis() + 60_000));
        User requester = new User();
        requester.setId(requesterUserId);

        try {
            invocationRepository.saveAndFlush(invocation);

            McpApprovalVo result = approvalService.decide(requestId, "approved_session", null, requester);

            assertThat(result.getStatus()).isEqualTo(McpInvocationStatus.APPROVED);
            assertThat(result.getApprovalScope()).isEqualTo(McpApprovalScope.SESSION);
            assertThat(grantRepository.existsById(grantId)).isTrue();
            McpToolInvocation persisted = invocationRepository.findByRequestId(requestId).orElseThrow();
            assertThat(persisted.getApprovalScope()).isEqualTo(McpApprovalScope.SESSION);
            assertThat(persisted.getDecisionBy()).isEqualTo(requesterUserId);
        } finally {
            grantService.revokeChat(chatId, requesterUserId);
            invocationRepository.findByRequestId(requestId).ifPresent(invocationRepository::delete);
        }
    }

    @Test
    void approvedSessionWakesToolOnlyAfterApprovalTransactionCommits() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String chatId = "approval-concurrency-test-" + suffix;
        String toolName = "approval_concurrency_test_" + suffix;
        String toolKey = McpToolDescriptor.localKey(toolName);
        Integer requesterUserId = -10004;
        List<McpApprovalEvent> events = new CopyOnWriteArrayList<>();
        McpInvocationContext context = new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                requesterUserId,
                chatId,
                UUID.randomUUID().toString(),
                "analysis",
                null,
                null,
                events::add
        );
        McpToolDescriptor descriptor = new McpToolDescriptor(
                toolKey,
                McpToolSourceType.LOCAL,
                null,
                "local",
                "ZenVis",
                toolName,
                toolName,
                "Approval concurrency test",
                "Verifies approval commit ordering",
                false,
                true,
                McpToolRiskLevel.HIGH,
                McpApprovalPolicy.ASK
        );
        User requester = new User();
        requester.setId(requesterUserId);
        CompletableFuture<String> execution = CompletableFuture.supplyAsync(() -> approvalService.execute(
                descriptor, "{\"id\":1}", context, () -> "{\"ok\":true}"));
        String requestId = null;

        try {
            requestId = awaitApprovalRequestId(events);

            approvalService.decide(requestId, "approved_session", null, requester);

            assertThat(execution.get(5, TimeUnit.SECONDS)).isEqualTo("{\"ok\":true}");
            McpToolInvocation persisted = invocationRepository.findByRequestId(requestId).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(McpInvocationStatus.SUCCEEDED);
            assertThat(persisted.getApprovalScope()).isEqualTo(McpApprovalScope.SESSION);
        } finally {
            grantService.revokeChat(chatId, requesterUserId);
            if (requestId != null) {
                invocationRepository.findByRequestId(requestId).ifPresent(invocationRepository::delete);
            }
            execution.cancel(true);
        }
    }

    @Test
    void invocationAuditSupportsServerSideFilteringPaginationAndUserIsolation() {
        String suffix = UUID.randomUUID().toString();
        Integer requesterUserId = -10005;
        List<McpToolInvocation> invocations = invocationRepository.saveAllAndFlush(List.of(
                auditInvocation("audit_alpha_" + suffix, McpInvocationChannel.CHAT_AGENT,
                        McpInvocationStatus.SUCCEEDED, McpApprovalPolicy.ASK, McpApprovalScope.SESSION,
                        requesterUserId, -101),
                auditInvocation("audit_beta_" + suffix, McpInvocationChannel.MANUAL,
                        McpInvocationStatus.FAILED, McpApprovalPolicy.ALLOW, null,
                        requesterUserId, null),
                auditInvocation("audit_other_" + suffix, McpInvocationChannel.CHAT_AGENT,
                        McpInvocationStatus.SUCCEEDED, McpApprovalPolicy.ASK, McpApprovalScope.SESSION,
                        -10006, -102)
        ));
        User admin = new User();
        admin.setId(-1);
        admin.setIsSuperAdmin(true);
        User requester = new User();
        requester.setId(requesterUserId);

        try {
            McpInvocationSearchDto filtered = new McpInvocationSearchDto();
            filtered.setKeyword("alpha_" + suffix);
            filtered.setChannel(McpInvocationChannel.CHAT_AGENT);
            filtered.setStatus(McpInvocationStatus.SUCCEEDED);
            filtered.setPolicy(McpApprovalPolicy.ASK);
            filtered.setApprovalScope(McpApprovalScope.SESSION);
            filtered.setRequesterUserId(requesterUserId);
            filtered.setDecisionBy(-101);
            filtered.setPerPage(10);

            PageRowsVo<McpApprovalVo> filteredResult = approvalService.listInvocations(filtered, admin);
            assertThat(filteredResult.getTotal()).isEqualTo(1);
            assertThat(filteredResult.getRows()).singleElement()
                    .extracting(McpApprovalVo::getToolKey)
                    .isEqualTo("local::audit_alpha_" + suffix);

            McpInvocationSearchDto paged = new McpInvocationSearchDto();
            paged.setKeyword(suffix);
            paged.setPage(1);
            paged.setPerPage(1);
            PageRowsVo<McpApprovalVo> pagedResult = approvalService.listInvocations(paged, requester);
            assertThat(pagedResult.getTotal()).isEqualTo(2);
            assertThat(pagedResult.getRows()).hasSize(1);
        } finally {
            invocationRepository.deleteAll(invocations);
        }
    }

    @Test
    void approvalQueueContainsOnlyPendingInvocations() {
        String suffix = UUID.randomUUID().toString();
        Integer requesterUserId = -10007;
        McpToolInvocation pending = auditInvocation(
                "queue_pending_" + suffix,
                McpInvocationChannel.CHAT_AGENT,
                McpInvocationStatus.PENDING,
                McpApprovalPolicy.ASK,
                null,
                requesterUserId,
                null
        ).setFinishTime(null).setExpireTime(new Date(System.currentTimeMillis() + 60_000));
        McpToolInvocation succeeded = auditInvocation(
                "queue_succeeded_" + suffix,
                McpInvocationChannel.CHAT_AGENT,
                McpInvocationStatus.SUCCEEDED,
                McpApprovalPolicy.ASK,
                McpApprovalScope.ONCE,
                requesterUserId,
                requesterUserId
        );
        List<McpToolInvocation> invocations = invocationRepository.saveAllAndFlush(List.of(pending, succeeded));
        User requester = new User();
        requester.setId(requesterUserId);

        try {
            PageRowsVo<McpApprovalVo> queue = approvalService.listPendingApprovals(1, 20, requester);

            assertThat(queue.getTotal()).isEqualTo(1);
            assertThat(queue.getRows()).singleElement()
                    .extracting(McpApprovalVo::getRequestId)
                    .isEqualTo(pending.getRequestId());
        } finally {
            invocationRepository.deleteAll(invocations);
        }
    }

    private McpToolInvocation auditInvocation(String toolName,
                                              McpInvocationChannel channel,
                                              McpInvocationStatus status,
                                              McpApprovalPolicy policy,
                                              McpApprovalScope approvalScope,
                                              Integer requesterUserId,
                                              Integer decisionBy) {
        return new McpToolInvocation()
                .setRequestId(UUID.randomUUID().toString())
                .setToolKey(McpToolDescriptor.localKey(toolName))
                .setToolName(toolName)
                .setRiskLevel(McpToolRiskLevel.HIGH)
                .setChannel(channel)
                .setPolicySnapshot(policy)
                .setApprovalScope(approvalScope)
                .setStatus(status)
                .setRequesterUserId(requesterUserId)
                .setDecisionBy(decisionBy)
                .setArgumentsSummary("{\"request\":{\"id\":1}}")
                .setResultSummary("{\"ok\":true}")
                .setFinishTime(new Date());
    }

    private String awaitApprovalRequestId(List<McpApprovalEvent> events) throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            String requestId = events.stream()
                    .filter(event -> "approval_required".equals(event.event()))
                    .map(event -> event.data().getRequestId())
                    .findFirst()
                    .orElse(null);
            if (requestId != null) {
                return requestId;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("approval request was not created");
    }
}
