package com.coolxer.service.dih.mcp;

import com.coolxer.commons.enums.McpApprovalPolicy;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.McpInvocationSearchDto;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.model.dih.vo.McpToolCallResultVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class McpApprovalService {

    private static final int MAX_ERROR_CHARS = 2000;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "secret", "authorization", "apikey", "api_key",
            "accesstoken", "access_token", "refreshtoken", "refresh_token", "privatekey", "private_key",
            "credential", "credentials"
    );
    private static final List<McpInvocationStatus> ACTIVE_STATUSES = List.of(
            McpInvocationStatus.PENDING, McpInvocationStatus.APPROVED, McpInvocationStatus.RUNNING
    );

    private final McpToolInvocationRepository invocationRepository;
    private final McpToolPolicyService policyService;
    private final McpChatToolGrantService chatToolGrantService;
    private final McpTaskToolGrantService taskToolGrantService;
    private final ObjectMapper objectMapper;
    private final long timeoutMillis;
    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    public McpApprovalService(McpToolInvocationRepository invocationRepository,
                              McpToolPolicyService policyService,
                              McpChatToolGrantService chatToolGrantService,
                              McpTaskToolGrantService taskToolGrantService,
                              ObjectMapper objectMapper,
                              @Value("${app.ai.mcp.approval.timeout-seconds:300}") long timeoutSeconds) {
        this.invocationRepository = invocationRepository;
        this.policyService = policyService;
        this.chatToolGrantService = chatToolGrantService;
        this.taskToolGrantService = taskToolGrantService;
        this.objectMapper = objectMapper;
        this.timeoutMillis = Duration.ofSeconds(Math.max(timeoutSeconds, 1)).toMillis();
    }

    @PostConstruct
    public void expireInterruptedInvocations() {
        Date now = new Date();
        for (McpToolInvocation invocation : invocationRepository.findByStatusIn(ACTIVE_STATUSES)) {
            invocation.setStatus(McpInvocationStatus.EXPIRED)
                    .setErrorSummary("服务重启，原工具调用已失效")
                    .setFinishTime(now);
            invocationRepository.save(invocation);
        }
    }

    public String execute(McpToolDescriptor descriptor,
                          String toolInput,
                          McpInvocationContext context,
                          Callable<String> delegate) {
        McpInvocationContext executionContext = context == null
                ? McpInvocationContext.background(null) : context;
        McpApprovalPolicy policy = policyService.effectivePolicy(descriptor.toolKey(), descriptor.defaultPolicy());
        boolean sessionGranted = policy == McpApprovalPolicy.ASK
                && chatToolGrantService.isGranted(executionContext, descriptor.toolKey());
        boolean taskGranted = policy == McpApprovalPolicy.ASK
                && taskToolGrantService.isGranted(executionContext, descriptor.toolKey());
        boolean taskAutoApproved = policy == McpApprovalPolicy.ASK
                && executionContext.analysisTaskId() != null
                && executionContext.taskApprovalMode() == AnalysisTaskApprovalMode.AUTO;
        boolean approvalRequired = policy == McpApprovalPolicy.ASK
                && !sessionGranted && !taskGranted && !taskAutoApproved;
        McpApprovalScope automaticScope = sessionGranted ? McpApprovalScope.SESSION
                : taskGranted ? McpApprovalScope.TASK_RUN
                : taskAutoApproved ? McpApprovalScope.TASK_AUTO : null;
        McpToolInvocation invocation = createInvocation(descriptor, toolInput, executionContext, policy,
                approvalRequired ? McpInvocationStatus.PENDING
                        : policy == McpApprovalPolicy.DENY ? McpInvocationStatus.DENIED : McpInvocationStatus.RUNNING,
                automaticScope);

        if (taskAutoApproved) {
            invocation.setDecisionComment("AI分析任务配置为无需人工审批，已自动批准 ASK 工具")
                    .setDecisionTime(new Date());
            invocationRepository.save(invocation);
        }

        if (policy == McpApprovalPolicy.DENY) {
            invocation.setFinishTime(new Date()).setErrorSummary("该工具已被审批策略禁止");
            invocationRepository.save(invocation);
            return deniedResult(invocation, executionContext, "该工具已被审批策略禁止");
        }

        if (executionContext.isTaskCancelled()) {
            invocation.setStatus(McpInvocationStatus.CANCELLED)
                    .setFinishTime(new Date())
                    .setErrorSummary("AI分析任务已取消");
            invocationRepository.save(invocation);
            return deniedResult(invocation, executionContext, "AI分析任务已取消");
        }

        if (approvalRequired) {
            PendingApproval pending = new PendingApproval(new CompletableFuture<>(), executionContext);
            pendingApprovals.put(invocation.getRequestId(), pending);
            executionContext.emit(McpApprovalEvent.required(new McpApprovalVo(invocation)));
            McpInvocationStatus decision = awaitDecision(invocation, pending);
            pendingApprovals.remove(invocation.getRequestId());
            if (decision != McpInvocationStatus.APPROVED) {
                McpToolInvocation resolved = getInvocation(invocation.getRequestId());
                String reason = decision == McpInvocationStatus.EXPIRED ? "MCP工具审批已超时"
                        : decision == McpInvocationStatus.CANCELLED ? "MCP工具调用已取消" : "用户拒绝了MCP工具调用";
                executionContext.emit(McpApprovalEvent.updated(new McpApprovalVo(resolved)));
                return deniedResult(resolved, executionContext, reason);
            }
            invocation = getInvocation(invocation.getRequestId());
            invocation.setStatus(McpInvocationStatus.RUNNING);
            invocationRepository.save(invocation);
            executionContext.emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
        }

        if (executionContext.isTaskCancelled()) {
            invocation = getInvocation(invocation.getRequestId());
            invocation.setStatus(McpInvocationStatus.CANCELLED)
                    .setErrorSummary("AI分析任务已取消")
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            return deniedResult(invocation, executionContext, "AI分析任务已取消");
        }

        long startedAt = System.nanoTime();
        try {
            String result = delegate.call();
            invocation = getInvocation(invocation.getRequestId());
            if (invocation.getStatus() == McpInvocationStatus.CANCELLED || executionContext.isTaskCancelled()) {
                if (invocation.getStatus() != McpInvocationStatus.CANCELLED) {
                    invocation.setStatus(McpInvocationStatus.CANCELLED)
                            .setErrorSummary("AI分析任务已取消")
                            .setFinishTime(new Date());
                    invocationRepository.save(invocation);
                }
                return deniedResult(invocation, executionContext, "AI分析任务已取消");
            }
            McpAuditPayloadUtils.StoredPayload auditResult = McpAuditPayloadUtils.prepareResult(result);
            invocation.setStatus(McpInvocationStatus.SUCCEEDED)
                    .setResult(auditResult.value())
                    .setResultLength(auditResult.originalLength())
                    .setDurationMillis(elapsedMillis(startedAt))
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            if (approvalRequired) {
                executionContext.emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
            }
            return result;
        } catch (Exception e) {
            invocation = getInvocation(invocation.getRequestId());
            if (invocation.getStatus() == McpInvocationStatus.CANCELLED || executionContext.isTaskCancelled()) {
                if (invocation.getStatus() != McpInvocationStatus.CANCELLED) {
                    invocation.setStatus(McpInvocationStatus.CANCELLED)
                            .setErrorSummary("AI分析任务已取消")
                            .setFinishTime(new Date());
                    invocationRepository.save(invocation);
                }
                return deniedResult(invocation, executionContext, "AI分析任务已取消");
            }
            invocation.setStatus(McpInvocationStatus.FAILED)
                    .setErrorSummary(summarizeJson(e.getMessage(), MAX_ERROR_CHARS))
                    .setDurationMillis(elapsedMillis(startedAt))
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            if (approvalRequired) {
                executionContext.emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e);
        }
    }

    public ManualGate prepareManual(McpToolDescriptor descriptor,
                                    String toolInput,
                                    User requester,
                                    String approvalRequestId) {
        Integer requesterId = requester == null ? null : requester.getId();
        if (StringUtils.isNotBlank(approvalRequestId)) {
            McpToolInvocation invocation = getInvocation(approvalRequestId);
            assertCanAccess(invocation, requester);
            if (!descriptor.toolKey().equals(invocation.getToolKey())
                    || !digest(toolInput).equals(invocation.getArgumentsDigest())) {
                throw new ApiException(400, "审批请求与工具或参数不匹配");
            }
            if (invocation.getStatus() != McpInvocationStatus.APPROVED) {
                throw new ApiException(409, "审批请求尚未批准或已失效");
            }
            invocation.setStatus(McpInvocationStatus.RUNNING);
            invocationRepository.save(invocation);
            return new ManualGate(invocation, true);
        }

        McpApprovalPolicy policy = policyService.effectivePolicy(descriptor.toolKey(), descriptor.defaultPolicy());
        McpInvocationContext context = new McpInvocationContext(
                McpInvocationChannel.MANUAL, requesterId, null, UUID.randomUUID().toString(),
                null, null, null, null
        );
        McpInvocationStatus status = policy == McpApprovalPolicy.ALLOW ? McpInvocationStatus.RUNNING
                : policy == McpApprovalPolicy.DENY ? McpInvocationStatus.DENIED : McpInvocationStatus.PENDING;
        McpToolInvocation invocation = createInvocation(descriptor, toolInput, context, policy, status, null);
        if (status == McpInvocationStatus.DENIED) {
            invocation.setErrorSummary("该工具已被审批策略禁止").setFinishTime(new Date());
            invocationRepository.save(invocation);
        }
        return new ManualGate(invocation, status == McpInvocationStatus.RUNNING);
    }

    public McpToolCallResultVo completeManual(String requestId, Callable<Object> delegate) {
        McpToolInvocation invocation = getInvocation(requestId);
        if (invocation.getStatus() != McpInvocationStatus.RUNNING) {
            return new McpToolCallResultVo(requestId, invocation.getStatus(), null, invocation.getErrorSummary());
        }
        long startedAt = System.nanoTime();
        try {
            Object result = delegate.call();
            String serializedResult = serializeResult(result);
            McpAuditPayloadUtils.StoredPayload auditResult =
                    McpAuditPayloadUtils.prepareResult(serializedResult);
            invocation.setStatus(McpInvocationStatus.SUCCEEDED)
                    .setResult(auditResult.value())
                    .setResultLength(auditResult.originalLength())
                    .setDurationMillis(elapsedMillis(startedAt))
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            return new McpToolCallResultVo(requestId, invocation.getStatus(), result, null);
        } catch (Exception e) {
            invocation.setStatus(McpInvocationStatus.FAILED)
                    .setErrorSummary(summarizeJson(e.getMessage(), MAX_ERROR_CHARS))
                    .setDurationMillis(elapsedMillis(startedAt))
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            return new McpToolCallResultVo(requestId, invocation.getStatus(), null, invocation.getErrorSummary());
        }
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public McpApprovalVo decide(String requestId, String decision, String comment, User currentUser) {
        McpToolInvocation invocation = getInvocation(requestId);
        assertCanAccess(invocation, currentUser);
        if (invocation.getStatus() != McpInvocationStatus.PENDING) {
            throw new ApiException(409, "审批请求已处理或已失效");
        }
        String normalizedDecision = StringUtils.trimToEmpty(decision).toLowerCase(Locale.ROOT);
        boolean approved = "approved".equals(normalizedDecision);
        boolean approvedSession = "approved_session".equals(normalizedDecision);
        boolean approvedTask = "approved_task".equals(normalizedDecision);
        boolean rejected = "rejected".equals(normalizedDecision);
        if (!approved && !approvedSession && !approvedTask && !rejected) {
            throw new ApiException(400, "决策值只支持 approved、approved_session、approved_task 或 rejected");
        }
        if (approvedSession) {
            assertSessionApprovalSupported(invocation);
            return approveForSession(invocation, comment, currentUser);
        }
        if (approvedTask) {
            assertTaskApprovalSupported(invocation);
            return approveForTask(invocation, comment, currentUser);
        }
        invocation.setStatus(approved ? McpInvocationStatus.APPROVED : McpInvocationStatus.REJECTED)
                .setApprovalScope(approved ? McpApprovalScope.ONCE : null);
        applyDecisionAudit(invocation, comment, currentUser);
        if (rejected) {
            invocation.setFinishTime(new Date()).setErrorSummary("用户拒绝了MCP工具调用");
        }
        invocationRepository.saveAndFlush(invocation);
        signalDecisionAfterCommit(invocation);
        return new McpApprovalVo(invocation);
    }

    private McpApprovalVo approveForSession(McpToolInvocation requestedInvocation,
                                            String comment,
                                            User currentUser) {
        Integer decisionBy = currentUser == null ? null : currentUser.getId();
        chatToolGrantService.grant(requestedInvocation, decisionBy);
        List<McpToolInvocation> pendingInvocations = invocationRepository
                .findByChatIdAndRequesterUserIdAndToolKeyAndStatus(
                        requestedInvocation.getChatId(),
                        requestedInvocation.getRequesterUserId(),
                        requestedInvocation.getToolKey(),
                        McpInvocationStatus.PENDING
                );
        if (pendingInvocations.stream().noneMatch(item ->
                requestedInvocation.getRequestId().equals(item.getRequestId()))) {
            pendingInvocations = new java.util.ArrayList<>(pendingInvocations);
            pendingInvocations.add(requestedInvocation);
        }
        for (McpToolInvocation pendingInvocation : pendingInvocations) {
            pendingInvocation.setStatus(McpInvocationStatus.APPROVED)
                    .setApprovalScope(McpApprovalScope.SESSION);
            applyDecisionAudit(pendingInvocation, comment, currentUser);
        }
        invocationRepository.saveAllAndFlush(pendingInvocations);
        signalDecisionsAfterCommit(pendingInvocations);
        return pendingInvocations.stream()
                .filter(item -> requestedInvocation.getRequestId().equals(item.getRequestId()))
                .findFirst()
                .map(McpApprovalVo::new)
                .orElseGet(() -> new McpApprovalVo(requestedInvocation));
    }

    private McpApprovalVo approveForTask(McpToolInvocation requestedInvocation,
                                         String comment,
                                         User currentUser) {
        Integer decisionBy = currentUser == null ? null : currentUser.getId();
        taskToolGrantService.grant(requestedInvocation, decisionBy);
        List<McpToolInvocation> pendingInvocations = invocationRepository
                .findByTaskExecutionIdAndToolKeyAndStatus(
                        requestedInvocation.getTaskExecutionId(),
                        requestedInvocation.getToolKey(),
                        McpInvocationStatus.PENDING
                );
        if (pendingInvocations.stream().noneMatch(item ->
                requestedInvocation.getRequestId().equals(item.getRequestId()))) {
            pendingInvocations = new ArrayList<>(pendingInvocations);
            pendingInvocations.add(requestedInvocation);
        }
        for (McpToolInvocation pendingInvocation : pendingInvocations) {
            pendingInvocation.setStatus(McpInvocationStatus.APPROVED)
                    .setApprovalScope(McpApprovalScope.TASK_RUN);
            applyDecisionAudit(pendingInvocation, comment, currentUser);
        }
        invocationRepository.saveAllAndFlush(pendingInvocations);
        signalDecisionsAfterCommit(pendingInvocations);
        return pendingInvocations.stream()
                .filter(item -> requestedInvocation.getRequestId().equals(item.getRequestId()))
                .findFirst()
                .map(McpApprovalVo::new)
                .orElseGet(() -> new McpApprovalVo(requestedInvocation));
    }

    private void assertSessionApprovalSupported(McpToolInvocation invocation) {
        if (invocation.getChannel() != McpInvocationChannel.CHAT_AGENT
                || invocation.getRequesterUserId() == null
                || StringUtils.isBlank(invocation.getChatId())) {
            throw new ApiException(400, "仅DIH聊天中的MCP调用支持本会话始终允许");
        }
    }

    private void assertTaskApprovalSupported(McpToolInvocation invocation) {
        if (invocation.getChannel() != McpInvocationChannel.BACKGROUND_AGENT
                || invocation.getAnalysisTaskId() == null
                || StringUtils.isBlank(invocation.getTaskExecutionId())) {
            throw new ApiException(400, "仅AI分析任务中的MCP调用支持本任务一直允许");
        }
    }

    private void applyDecisionAudit(McpToolInvocation invocation, String comment, User currentUser) {
        invocation.setDecisionBy(currentUser == null ? null : currentUser.getId())
                .setDecisionComment(StringUtils.abbreviate(StringUtils.trimToNull(comment), 1000))
                .setDecisionTime(new Date());
    }

    private void signalDecision(McpToolInvocation invocation) {
        PendingApproval pending = pendingApprovals.get(invocation.getRequestId());
        if (pending != null) {
            try {
                pending.context().emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
            } finally {
                pending.future().complete(invocation.getStatus());
            }
        }
    }

    private void signalDecisionAfterCommit(McpToolInvocation invocation) {
        signalDecisionsAfterCommit(List.of(invocation));
    }

    private void signalDecisionsAfterCommit(List<McpToolInvocation> invocations) {
        List<McpToolInvocation> decisions = List.copyOf(invocations);
        Runnable signal = () -> decisions.forEach(this::signalDecision);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.run();
                }
            });
            return;
        }
        signal.run();
    }

    public PageRowsVo<McpApprovalVo> list(int page, int perPage, User currentUser) {
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(perPage, 1));
        Page<McpToolInvocation> result = isSuperAdmin(currentUser)
                ? invocationRepository.findAllByOrderByCreateTimeDesc(pageable)
                : invocationRepository.findByRequesterUserIdOrderByCreateTimeDesc(
                        currentUser == null ? -1 : currentUser.getId(), pageable);
        return new PageRowsVo<>(result.getContent().stream().map(McpApprovalVo::new).toList(), result.getTotalElements());
    }

    public PageRowsVo<McpApprovalVo> listPendingApprovals(int page, int perPage, User currentUser) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(perPage, 1), 100)
        );
        Page<McpToolInvocation> result = isSuperAdmin(currentUser)
                ? invocationRepository.findByStatusOrderByCreateTimeDesc(McpInvocationStatus.PENDING, pageable)
                : invocationRepository.findByRequesterUserIdAndStatusOrderByCreateTimeDesc(
                        currentUser == null ? -1 : currentUser.getId(), McpInvocationStatus.PENDING, pageable);
        return new PageRowsVo<>(result.getContent().stream().map(McpApprovalVo::new).toList(), result.getTotalElements());
    }

    public PageRowsVo<McpApprovalVo> listTaskPendingApprovals(Integer taskId,
                                                              int page,
                                                              int perPage,
                                                              User currentUser) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(perPage, 1), 100)
        );
        Page<McpToolInvocation> result = invocationRepository
                .findByAnalysisTaskIdAndStatusOrderByCreateTimeDesc(
                        taskId, McpInvocationStatus.PENDING, pageable);
        if (!isSuperAdmin(currentUser)) {
            for (McpToolInvocation invocation : result.getContent()) {
                assertCanAccess(invocation, currentUser);
            }
        }
        return new PageRowsVo<>(result.getContent().stream().map(McpApprovalVo::new).toList(), result.getTotalElements());
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public McpApprovalVo decideTask(Integer taskId,
                                    String requestId,
                                    String decision,
                                    String comment,
                                    User currentUser) {
        McpToolInvocation invocation = getInvocation(requestId);
        if (!java.util.Objects.equals(taskId, invocation.getAnalysisTaskId())) {
            throw new ApiException(400, "审批请求与AI分析任务不匹配");
        }
        return decide(requestId, decision, comment, currentUser);
    }

    public PageRowsVo<McpApprovalVo> listInvocations(McpInvocationSearchDto searchDto, User currentUser) {
        McpInvocationSearchDto criteria = searchDto == null ? new McpInvocationSearchDto() : searchDto;
        PageRequest pageable = PageRequest.of(
                Math.max(criteria.getPage(), 1) - 1,
                Math.min(Math.max(criteria.getPerPage(), 1), 100),
                Sort.by(Sort.Direction.DESC, "createTime")
        );
        String keyword = StringUtils.trimToEmpty(criteria.getKeyword()).toLowerCase(Locale.ROOT);
        Specification<McpToolInvocation> specification = (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(criteria.getRequestId())) {
                predicates.add(builder.equal(root.get("requestId"), criteria.getRequestId().trim()));
            }
            if (!isSuperAdmin(currentUser)) {
                predicates.add(builder.equal(root.get("requesterUserId"),
                        currentUser == null ? -1 : currentUser.getId()));
            } else if (criteria.getRequesterUserId() != null) {
                predicates.add(builder.equal(root.get("requesterUserId"), criteria.getRequesterUserId()));
            }
            if (criteria.getDecisionBy() != null) {
                predicates.add(builder.equal(root.get("decisionBy"), criteria.getDecisionBy()));
            }
            if (criteria.getChannel() != null) {
                predicates.add(builder.equal(root.get("channel"), criteria.getChannel()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.getPolicy() != null) {
                predicates.add(builder.equal(root.get("policySnapshot"), criteria.getPolicy()));
            }
            if (criteria.getApprovalScope() != null) {
                predicates.add(builder.equal(root.get("approvalScope"), criteria.getApprovalScope()));
            }
            if (criteria.getAnalysisTaskId() != null) {
                predicates.add(builder.equal(root.get("analysisTaskId"), criteria.getAnalysisTaskId()));
            }
            if (StringUtils.isNotBlank(criteria.getExecutionId())) {
                predicates.add(builder.equal(root.get("taskExecutionId"), criteria.getExecutionId().trim()));
            }
            if (StringUtils.isNotEmpty(keyword)) {
                String pattern = "%" + keyword + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("requestId")), pattern),
                        builder.like(builder.lower(root.get("toolKey")), pattern),
                        builder.like(builder.lower(root.get("toolName")), pattern),
                        builder.like(builder.lower(root.get("serverName")), pattern),
                        builder.like(builder.lower(root.get("chatId")), pattern),
                        builder.like(builder.lower(root.get("agentType")), pattern)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<McpToolInvocation> result = invocationRepository.findAll(specification, pageable);
        return new PageRowsVo<>(result.getContent().stream().map(McpApprovalVo::new).toList(), result.getTotalElements());
    }

    public McpApprovalVo view(String requestId, User currentUser) {
        McpToolInvocation invocation = getInvocation(requestId);
        assertCanAccess(invocation, currentUser);
        return new McpApprovalVo(invocation);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void cancelTurn(String turnId, Integer requesterUserId) {
        if (StringUtils.isBlank(turnId)) {
            return;
        }
        for (McpToolInvocation invocation : invocationRepository.findByTurnIdAndStatusIn(
                turnId, List.of(McpInvocationStatus.PENDING, McpInvocationStatus.APPROVED))) {
            if (requesterUserId != null && invocation.getRequesterUserId() != null
                    && !requesterUserId.equals(invocation.getRequesterUserId())) {
                continue;
            }
            invocation.setStatus(McpInvocationStatus.CANCELLED)
                    .setErrorSummary("聊天流已终止")
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            PendingApproval pending = pendingApprovals.get(invocation.getRequestId());
            if (pending != null) {
                pending.future().complete(McpInvocationStatus.CANCELLED);
                pending.context().emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
            }
        }
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void cancelTaskExecution(String executionId, String reason) {
        if (StringUtils.isBlank(executionId)) {
            return;
        }
        String cancellationReason = StringUtils.defaultIfBlank(reason, "AI分析任务已取消");
        List<McpToolInvocation> invocations = invocationRepository.findByTaskExecutionIdAndStatusIn(
                executionId,
                List.of(McpInvocationStatus.PENDING, McpInvocationStatus.APPROVED, McpInvocationStatus.RUNNING)
        );
        for (McpToolInvocation invocation : invocations) {
            invocation.setStatus(McpInvocationStatus.CANCELLED)
                    .setErrorSummary(cancellationReason)
                    .setFinishTime(new Date());
            invocationRepository.save(invocation);
            PendingApproval pending = pendingApprovals.get(invocation.getRequestId());
            if (pending != null) {
                pending.future().complete(McpInvocationStatus.CANCELLED);
                pending.context().emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
            }
        }
        taskToolGrantService.revokeExecution(executionId);
    }

    public long countPendingTaskApprovals(String executionId) {
        if (StringUtils.isBlank(executionId)) {
            return 0;
        }
        return invocationRepository.countByTaskExecutionIdAndStatus(executionId, McpInvocationStatus.PENDING);
    }

    @Scheduled(fixedDelay = 30000L)
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void expireOverdue() {
        Date now = new Date();
        for (McpToolInvocation invocation : invocationRepository.findByStatusIn(List.of(McpInvocationStatus.PENDING))) {
            if (invocation.getExpireTime() == null || invocation.getExpireTime().after(now)) {
                continue;
            }
            try {
                invocation.setStatus(McpInvocationStatus.EXPIRED)
                        .setErrorSummary("MCP工具审批已超时")
                        .setFinishTime(now);
                invocationRepository.save(invocation);
                PendingApproval pending = pendingApprovals.get(invocation.getRequestId());
                if (pending != null) {
                    pending.future().complete(McpInvocationStatus.EXPIRED);
                    pending.context().emit(McpApprovalEvent.updated(new McpApprovalVo(invocation)));
                }
            } catch (OptimisticLockingFailureException ignored) {
                log.debug("MCP审批状态已被其他请求更新: requestId={}", invocation.getRequestId());
            }
        }
    }

    private McpInvocationStatus awaitDecision(McpToolInvocation invocation, PendingApproval pending) {
        Long deadline = invocation.getExpireTime() == null ? null : invocation.getExpireTime().getTime();
        while (deadline == null || System.currentTimeMillis() < deadline) {
            McpInvocationStatus databaseStatus = getInvocation(invocation.getRequestId()).getStatus();
            if (databaseStatus != McpInvocationStatus.PENDING) {
                return databaseStatus;
            }
            try {
                McpInvocationStatus signaled = pending.future().get(1, TimeUnit.SECONDS);
                if (signaled != null) {
                    return signaled;
                }
            } catch (java.util.concurrent.TimeoutException ignored) {
                // Polling also allows approval requests handled by another application instance.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markTerminal(invocation.getRequestId(), McpInvocationStatus.CANCELLED, "MCP工具调用线程已中断");
                return McpInvocationStatus.CANCELLED;
            } catch (Exception e) {
                log.debug("等待MCP审批状态失败: requestId={}", invocation.getRequestId(), e);
            }
        }
        markTerminal(invocation.getRequestId(), McpInvocationStatus.EXPIRED, "MCP工具审批已超时");
        return McpInvocationStatus.EXPIRED;
    }

    private void markTerminal(String requestId, McpInvocationStatus status, String error) {
        McpToolInvocation invocation = getInvocation(requestId);
        if (invocation.getStatus() != McpInvocationStatus.PENDING) {
            return;
        }
        invocation.setStatus(status).setErrorSummary(error).setFinishTime(new Date());
        invocationRepository.save(invocation);
    }

    private McpToolInvocation createInvocation(McpToolDescriptor descriptor,
                                               String toolInput,
                                               McpInvocationContext context,
                                               McpApprovalPolicy policy,
                                               McpInvocationStatus status,
                                               McpApprovalScope approvalScope) {
        Date now = new Date();
        McpToolInvocation invocation = new McpToolInvocation()
                .setRequestId(UUID.randomUUID().toString())
                .setToolKey(descriptor.toolKey())
                .setToolName(StringUtils.defaultIfBlank(descriptor.aiToolName(), descriptor.toolName()))
                .setServerName(descriptor.serverName())
                .setDescription(descriptor.description())
                .setRiskLevel(descriptor.riskLevel() == null
                        ? com.coolxer.commons.enums.McpToolRiskLevel.UNKNOWN : descriptor.riskLevel())
                .setChannel(context.channel() == null ? McpInvocationChannel.BACKGROUND_AGENT : context.channel())
                .setPolicySnapshot(policy)
                .setApprovalScope(approvalScope)
                .setStatus(status)
                .setRequesterUserId(context.requesterUserId())
                .setChatId(context.chatId())
                .setTurnId(context.turnId())
                .setAgentType(context.agentType())
                .setAnalysisTaskId(context.analysisTaskId())
                .setTaskExecutionId(context.executionId())
                .setMcpSessionId(context.mcpSessionId())
                .setMcpClientInfo(context.mcpClientInfo())
                .setArguments(McpAuditPayloadUtils.fitLongText(toolInput))
                .setArgumentsDigest(digest(toolInput));
        if (status == McpInvocationStatus.PENDING && !isManualAnalysisTask(context)) {
            invocation.setExpireTime(new Date(now.getTime() + timeoutMillis));
        }
        return invocationRepository.save(invocation);
    }

    private boolean isManualAnalysisTask(McpInvocationContext context) {
        return context != null
                && context.analysisTaskId() != null
                && context.taskApprovalMode() == AnalysisTaskApprovalMode.MANUAL;
    }

    private String deniedResult(McpToolInvocation invocation, McpInvocationContext context, String reason) {
        if (context.channel() == McpInvocationChannel.MCP_SERVER) {
            throw new McpToolDeniedException(reason + "，requestId=" + invocation.getRequestId());
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "status", invocation.getStatus().name().toLowerCase(Locale.ROOT),
                    "requestId", invocation.getRequestId(),
                    "message", reason
            ));
        } catch (Exception e) {
            return reason;
        }
    }

    private void assertCanAccess(McpToolInvocation invocation, User currentUser) {
        if (isSuperAdmin(currentUser)) {
            return;
        }
        if (currentUser == null || invocation.getRequesterUserId() == null
                || !currentUser.getId().equals(invocation.getRequesterUserId())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }

    private boolean isSuperAdmin(User user) {
        return user != null && Boolean.TRUE.equals(user.getIsSuperAdmin());
    }

    private McpToolInvocation getInvocation(String requestId) {
        return invocationRepository.findByRequestId(requestId)
                .orElseThrow(() -> new ApiException(404, "MCP审批请求不存在"));
    }

    private String summarizeJson(String value, int maxChars) {
        if (StringUtils.isBlank(value)) {
            return "{}";
        }
        try {
            JsonNode node = canonicalize(objectMapper.readTree(value));
            return summarize(objectMapper.writeValueAsString(redact(node)), maxChars);
        } catch (Exception e) {
            return summarize(redactText(value), maxChars);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, canonicalize(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        return node;
    }

    private JsonNode redact(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            copy.fields().forEachRemaining(entry -> {
                String normalizedKey = entry.getKey().toLowerCase(Locale.ROOT).replace("-", "_");
                if (SENSITIVE_KEYS.contains(normalizedKey)) {
                    copy.put(entry.getKey(), "***");
                } else {
                    copy.set(entry.getKey(), redact(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(redact(item)));
            return array;
        }
        return node;
    }

    private String digest(String value) {
        try {
            String canonical = StringUtils.defaultString(value);
            try {
                canonical = objectMapper.writeValueAsString(canonicalize(objectMapper.readTree(canonical)));
            } catch (Exception ignored) {
                // Non-JSON input is hashed as-is.
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Base64.getEncoder().encodeToString(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String serializeResult(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String summarize(String value, int maxChars) {
        String normalized = StringUtils.defaultString(value).replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private static String redactText(String value) {
        String redacted = StringUtils.defaultString(value)
                .replaceAll("(?i)(password|passwd|pwd|token|secret|authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|private[_-]?key)(\\s*[=:]\\s*)([^,\\s;}&]+)", "$1$2***");
        return redacted.replaceAll("(?i)(bearer\\s+)[a-z0-9._~+\\-/=]+", "$1***");
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record PendingApproval(CompletableFuture<McpInvocationStatus> future, McpInvocationContext context) {
    }

    public record ManualGate(McpToolInvocation invocation, boolean executable) {
    }
}
