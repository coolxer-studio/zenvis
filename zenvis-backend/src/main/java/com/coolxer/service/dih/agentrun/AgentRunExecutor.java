package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.entity.AgentRunInput;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.AgentRunInputRepository;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.dao.mysql.repository.McpToolInvocationRepository;
import com.coolxer.dao.mysql.repository.UserRepository;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ContextCompactionEventData;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.RagSearchEventData;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ReportActionDto;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.model.dih.vo.SkillRuntimeLimitsVo;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.ContextCompactionObserver;
import com.coolxer.service.dih.DihChatApplicationService;
import com.coolxer.service.dih.DihChatApplicationService.McpToolLogStream;
import com.coolxer.service.dih.DihChatExecutionPolicy;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.attachment.AttachmentToolProviderFactory;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpApprovalEvent;
import com.coolxer.service.dih.mcp.McpApprovalPendingException;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolResultUnknownException;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单次 Run worker。每次只做一个模型决策步骤，然后保存检查点并重新入队。
 */
@Slf4j
@Service
public class AgentRunExecutor {

    private static final int MAX_MODEL_FAILURES = 3;
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile(
            "HTTP\\s+(\\d{3})|\\b(4\\d{2}|5\\d{2})\\s+[A-Za-z]");

    private static final String RUN_INSTRUCTIONS = """

            【持久化智能体运行协议】
            你正在一个会跨越多步、可恢复的后台任务中工作。先理解目标、当前计划和已完成步骤，再执行下一步。
            可用控制工具：
            - run_update_plan：创建或调整完整计划。
            - run_update_step：步骤开始、完成、跳过或阻塞时更新状态；完成步骤必须写摘要。
            - run_request_user：只有缺少必须由用户提供的业务信息时才暂停提问；不得用它代替 MCP 审批、工具重试或等待“继续”。
            - run_finish：所有步骤完成或明确跳过后才提交最终答复。
            不要声称完成了未执行的工作。工具失败、输出截断或证据不足时，保留已完成成果并说明缺口。
            本步骤优先推进一个清晰目标；不要为了结束而跳过未完成步骤。
            """;

    private static final String REPORT_INSTRUCTIONS = """
            当前会话是报表任务。长报表必须使用分章节写入：
            1. 先调用 report_prepare_document 创建/打开草稿。
            2. 每个章节调用 report_upsert_section 或 report_replace_section；单章不超过 2400 字符。
            3. 不要在一次模型输出中重写所有章节；只写当前章节，其他章节保持原样。
            4. 所有章节完成后调用 report_finalize，然后 run_update_step 和 run_finish。
            工具返回“章节超过上限”时只缩短当前章节后重试，不得删除其他章节。
            """;

    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final AgentRunInputRepository inputRepository;
    private final McpToolInvocationRepository invocationRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final SkillService skillService;
    private final AgentMcpToolService agentMcpToolService;
    private final DihChatApplicationService chatApplicationService;
    private final ReportDraftService reportDraftService;
    private final ChatAttachmentService chatAttachmentService;
    private final AgentRunInteractionService interactionService;

    @Autowired(required = false)
    private AttachmentToolProviderFactory attachmentToolProviderFactory;
    private final AgentRunContextAssembler contextAssembler;
    private final ScheduledExecutorService heartbeatExecutor;

    @Autowired(required = false)
    private AgentRunMetrics metrics;

    private final long leaseSeconds;

    public AgentRunExecutor(AgentRunService runService,
                            AgentRunRepository runRepository,
                            AgentRunInputRepository inputRepository,
                            McpToolInvocationRepository invocationRepository,
                            ChatSessionRepository chatSessionRepository,
                            UserRepository userRepository,
                            SkillService skillService,
                            AgentMcpToolService agentMcpToolService,
                            DihChatApplicationService chatApplicationService,
                            ReportDraftService reportDraftService,
                            ChatAttachmentService chatAttachmentService,
                            AgentRunInteractionService interactionService,
                            AgentRunContextAssembler contextAssembler,
                            @org.springframework.beans.factory.annotation.Value(
                                    "${app.ai.agent-run.lease-seconds:60}") long leaseSeconds) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.inputRepository = inputRepository;
        this.invocationRepository = invocationRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.userRepository = userRepository;
        this.skillService = skillService;
        this.agentMcpToolService = agentMcpToolService;
        this.chatApplicationService = chatApplicationService;
        this.reportDraftService = reportDraftService;
        this.chatAttachmentService = chatAttachmentService;
        this.interactionService = interactionService;
        this.contextAssembler = contextAssembler;
        this.leaseSeconds = leaseSeconds;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-run-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void execute(String runId, String leaseId) {
        long startedAt = System.nanoTime();
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> heartbeat(runId, leaseId),
                Math.max(leaseSeconds / 4, 5),
                Math.max(leaseSeconds / 4, 5),
                TimeUnit.SECONDS);
        try {
            executeInternal(runId, leaseId);
        } catch (Exception e) {
            log.error("Agent Run 执行失败: runId={}", runId, e);
            runService.markFailed(runId, errorMessage(e), null);
        } finally {
            heartbeat.cancel(false);
            if (metrics != null) {
                metrics.stepDuration(Duration.ofNanos(System.nanoTime() - startedAt));
            }
        }
    }

    private void executeInternal(String runId, String leaseId) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalStateException("Agent Run 不存在: " + runId));
        if (run.getStatus() == AgentRunStatus.CANCELING) {
            runService.markCancelled(runId, run.getCurrentStepId());
            return;
        }
        if (run.getStatus().isWaiting() || run.getStatus().isTerminal()) {
            return;
        }
        if (run.getDecisionSteps() >= run.getMaxSteps()) {
            runService.markPaused(runId, "已达到最大决策步数", run.getCurrentStepId());
            return;
        }
        if (run.getEffectiveMillis() >= run.getMaxDurationSeconds() * 1000L) {
            runService.markPaused(runId, "已达到最大有效执行时长", run.getCurrentStepId());
            return;
        }
        User user = userRepository.findById(run.getCreateBy());
        if (user == null) {
            runService.markFailed(runId, "Run 所属用户不存在", run.getCurrentStepId());
            return;
        }
        ChatSession session = chatSessionRepository.findById(run.getChatSessionId())
                .orElseThrow(() -> new IllegalStateException("Run 会话不存在"));
        DihChatExecutionPolicy policy = DihChatExecutionPolicy
                .resolve(run.getChatType(), skillService)
                .orElseThrow(() -> new IllegalStateException("Run 会话类型不可用"));
        String stepId = StringUtils.defaultIfBlank(run.getCurrentStepId(), "step-" + (run.getDecisionSteps() + 1));
        AgentRunExecutionState state = new AgentRunExecutionState(runId, stepId);
        AtomicInteger toolCalls = new AtomicInteger();
        StringBuilder assistantText = new StringBuilder();
        AtomicBoolean truncated = new AtomicBoolean(false);

        McpToolLogStream toolEvidence = McpToolLogStream.createEvidenceOnly(
                "run:" + runId + ":" + stepId);
        ContextCompactionObserver compactionObserver = compactionObserver(run, stepId);
        AgentRunPrompt preparedPrompt = buildPrompt(run, stepId, user, compactionObserver);
        McpToolContext toolContext = buildToolContext(
                run, policy, user, state, stepId, toolCalls, toolEvidence,
                preparedPrompt.attachments());
        ChatDto chatDto = buildChatDto(run, preparedPrompt.text(), preparedPrompt.attachments());
        long startedAt = System.nanoTime();
        try {
            resumeApprovedTools(run, session, stepId, toolContext, state);
            String dispatchPrompt = chatApplicationService.bootstrapVisualizationWorkflow(
                    policy, toolContext, preparedPrompt.text(), session);
            AtomicBoolean cancellationRequested = new AtomicBoolean(false);
            Flux<String> modelStream = chatApplicationService.dispatchChat(
                            run.getChatType(),
                            run.getChatId(),
                            run.getModel(),
                            dispatchPrompt,
                            chatDto,
                            user,
                            toolContext,
                            policy,
                            false,
                            new java.util.concurrent.atomic.AtomicReference<>(MessageType.TEXT),
                            compactionObserver);
            List<String> values = stopWhenCancellationRequested(modelStream, runId, cancellationRequested)
                    .collectList()
                    .block(Duration.ofMinutes(35));
            if (values == null) {
                throw new IllegalStateException("模型调用未返回结果");
            }
            if (cancellationRequested.get()) {
                runService.markCancelled(runId, stepId);
                return;
            }
            for (String value : values == null ? List.<String>of() : values) {
                handleStreamValue(runId, stepId, value, assistantText, truncated);
            }
            contextAssembler.applyInputs(preparedPrompt.pendingInputIds());
        } catch (Exception e) {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            AgentRun failed = runRepository.findByRunId(runId).orElse(run);
            if (failed.getStatus() == AgentRunStatus.CANCELING) {
                runService.markCancelled(runId, stepId);
                return;
            }
            if (state.isApprovalPending()
                    || failed.getStatus() == AgentRunStatus.WAITING_APPROVAL
                    || hasCause(e, McpApprovalPendingException.class)) {
                runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), true, null, stepId);
                runService.saveCheckpoint(runId, StringUtils.left(assistantText.toString(), 24_000));
                return;
            }
            if (state.isBudgetExhausted() || hasCause(e, AgentRunBudgetExceededException.class)) {
                runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), false, null, stepId);
                runService.markPaused(runId, "工具调用达到 Run 总预算", stepId);
                return;
            }
            if (hasCause(e, McpToolResultUnknownException.class)) {
                runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), false, null, stepId);
                runService.markPaused(runId, "UNKNOWN_TOOL_RESULT: 外部工具结果未知，已停止自动重放", stepId);
                return;
            }
            int failureCount = runService.checkpointFailure(
                    runId, elapsedMillis, toolCalls.get(), errorMessage(e), stepId);
            boolean retryable = isRetryableModelFailure(e);
            Integer httpStatus = httpStatus(e);
            Map<String, Object> errorPayload = new LinkedHashMap<>();
            errorPayload.put("message", errorMessage(e));
            errorPayload.put("retryable", retryable);
            errorPayload.put("failureCount", failureCount);
            if (httpStatus != null) {
                errorPayload.put("httpStatus", httpStatus);
            }
            runService.recordEvent(runId, "run.error", stepId,
                    errorPayload);
            if (!retryable) {
                String statusText = httpStatus == null ? "" : "（HTTP " + httpStatus + "）";
                runService.markPaused(runId,
                        "模型服务配置错误" + statusText + "，请检查模型地址、密钥或模型名称后继续",
                        stepId);
                return;
            }
            if (failureCount >= MAX_MODEL_FAILURES) {
                runService.markPaused(runId,
                        "模型调用连续失败 " + MAX_MODEL_FAILURES + " 次：" + errorMessage(e),
                        stepId);
                return;
            }
            runService.requeue(runId, stepId,
                    "模型调用失败，准备第 " + (failureCount + 1) + "/"
                            + MAX_MODEL_FAILURES + " 次重试");
            return;
        }

        AgentRun latest = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalStateException("Agent Run 不存在: " + runId));
        if (latest.getStatus() == AgentRunStatus.CANCELING) {
            runService.markCancelled(runId, stepId);
            return;
        }
        if (latest.getStatus() == AgentRunStatus.PAUSED
                || latest.getStatus() == AgentRunStatus.WAITING_USER
                || latest.getStatus() == AgentRunStatus.WAITING_APPROVAL) {
            return;
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (truncated.get()) {
            runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), false, null, stepId);
            int attempt = runService.recordTruncation(runId, stepId,
                    StringUtils.left(assistantText.toString(), 24_000));
            if (attempt >= 2) {
                runService.markPaused(runId, "OUTPUT_TRUNCATED: 连续输出截断，请缩短当前步骤后继续", stepId);
            } else {
                runService.requeue(runId, stepId, "OUTPUT_TRUNCATED: 当前步骤未完成，重试");
            }
            return;
        }
        String assistantAnswer = assistantText.toString();
        if (chatApplicationService.requiresUserDecision(assistantAnswer)) {
            Message responseMessage = chatApplicationService.saveAgentRunResponse(
                    session,
                    user,
                    assistantAnswer,
                    MessageType.TEXT,
                    null,
                    runId,
                    toolEvidence);
            runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), true, null, stepId);
            runService.saveCheckpoint(runId, StringUtils.left(assistantAnswer, 24_000));
            String decisionQuestion = chatApplicationService.resolveUserDecisionQuestion(
                    assistantAnswer);
            String question = state.isWaitingUser()
                    ? state.getUserQuestion()
                    : StringUtils.defaultIfBlank(decisionQuestion,
                    "请处理当前智能体工作流卡片后继续");
            var cardInteraction = interactionService.cardInteraction(responseMessage, question);
            if (cardInteraction != null) {
                runService.markWaitingForCard(runId, cardInteraction, stepId);
            } else {
                runService.markWaitingUser(runId, question, stepId);
            }
            return;
        }
        boolean progressed = state.hasProgress();
        runService.checkpointStep(runId, elapsedMillis, toolCalls.get(), progressed,
                state.isWaitingUser() ? "user" : null, stepId);
        runService.saveCheckpoint(runId, StringUtils.left(assistantText.toString(), 24_000));
        if (progressed) {
            runService.clearTruncation(runId, stepId);
        } else if (!state.isFinished()) {
            runService.saveCheckpoint(runId,
                    "【继续未完成步骤】上一步只产生了说明性文本，没有推进计划。"
                            + "请根据当前计划继续执行下一步；不要等待用户再次发送“继续”。"
                            + (StringUtils.isBlank(assistantText) ? "" : "\n\n上一步输出：\n"
                            + StringUtils.left(assistantText.toString(), 8_000)));
        }

        latest = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalStateException("Agent Run 不存在: " + runId));
        AgentRunPlan plan = parsePlan(latest.getPlanJson());
        if (state.isWaitingUser()) {
            runService.markWaitingUser(runId,
                    StringUtils.defaultIfBlank(state.getUserQuestion(), "请补充必要信息后继续"),
                    stepId);
            return;
        }
        String answer = StringUtils.defaultIfBlank(
                assistantAnswer, state.getFinalSummary());
        boolean reportReady = !"agent_report".equals(policy.agentType())
                || reportDraftService.hasFinalizedDraft(runId);
        if (planComplete(plan) && reportReady
                && (state.isFinished() || StringUtils.isNotBlank(answer))) {
            saveFinalMessage(session, user, runId, answer, toolEvidence);
            runService.markSucceeded(runId, answer, stepId);
            return;
        }
        if (state.isBudgetExhausted()) {
            runService.markPaused(runId, "工具调用达到 Run 总预算", stepId);
            return;
        }
        if (latest.getNoProgressCount() >= 3) {
            runService.markPaused(runId, "连续 3 次未推进计划", stepId);
            return;
        }
        if (latest.getDecisionSteps() >= latest.getMaxSteps()) {
            runService.markPaused(runId, "已达到最大决策步数", stepId);
            return;
        }
        if (latest.getEffectiveMillis() >= latest.getMaxDurationSeconds() * 1000L) {
            runService.markPaused(runId, "已达到最大有效执行时长", stepId);
            return;
        }
        runService.requeue(runId, stepId, state.isFinished()
                ? "计划仍有未完成步骤，继续执行"
                : (progressed ? null : "当前步骤未推进计划，继续未完成步骤"));
    }

    private Flux<String> stopWhenCancellationRequested(Flux<String> modelStream,
                                                       String runId,
                                                       AtomicBoolean cancellationRequested) {
        Flux<Boolean> cancellationSignal = Flux.interval(Duration.ofSeconds(2))
                .flatMap(tick -> Mono.fromCallable(() -> runRepository.findByRunId(runId)
                                .map(current -> current.getStatus() == AgentRunStatus.CANCELING)
                                .orElse(false))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorReturn(false))
                .filter(Boolean::booleanValue)
                .take(1)
                .doOnNext(ignored -> cancellationRequested.set(true));
        return modelStream.takeUntilOther(cancellationSignal);
    }

    private McpToolContext buildToolContext(AgentRun run,
                                            DihChatExecutionPolicy policy,
                                            User user,
                                            AgentRunExecutionState state,
                                            String stepId,
                                            AtomicInteger toolCalls,
                                            McpToolLogStream toolEvidence,
                                            List<ChatAttachment> attachments) {
        McpToolContext resolved = policy.toolsAllowed()
                ? agentMcpToolService.resolve(policy.agentType(), policy.skillIds())
                : McpToolContext.empty();
        AttachmentToolProviderFactory.AttachmentToolBundle attachmentTools =
                attachmentToolProviderFactory == null
                        || (run.getChatId() == null || run.getChatId().isBlank())
                        && (attachments == null || attachments.isEmpty())
                        ? AttachmentToolProviderFactory.AttachmentToolBundle.empty()
                        : attachmentToolProviderFactory.create(
                        user, run.getChatId(), run.getRunId(), attachments);
        AgentRunInternalToolProvider internal = new AgentRunInternalToolProvider(
                runService, reportDraftService, state, user, policy.agentType());
        ToolCallbackProvider merged = new CompositeToolCallbackProvider(
                resolved.hasTools() ? resolved.toolCallbackProvider() : null,
                attachmentTools.hasTools() ? attachmentTools.provider() : null,
                internal);
        String systemPrompt = StringUtils.defaultString(resolved.systemPrompt())
                + (attachmentTools.hasTools() ? "\n" + attachmentTools.systemPrompt() : "")
                + RUN_INSTRUCTIONS
                + ("agent_report".equals(policy.agentType()) ? REPORT_INSTRUCTIONS : "");
        McpInvocationContext invocation = new McpInvocationContext(
                McpInvocationChannel.CHAT_AGENT,
                user.getId(),
                run.getChatId(),
                "run:" + run.getRunId() + ":" + stepId,
                policy.agentType(),
                null,
                "agent-run:" + run.getRunId(),
                event -> handleApprovalEvent(
                        run.getRunId(), stepId, state, event, toolEvidence));
        ToolCallbackProvider logging = new McpToolCallLoggingProvider(merged, call -> {
            toolEvidence.emit(call);
            if ("started".equals(call.status())) {
                int current = toolCalls.incrementAndGet();
                if (run.getToolCalls() + current > run.getMaxToolCalls()) {
                    state.markBudgetExhausted();
                    throw new AgentRunBudgetExceededException(
                            "Run 工具调用总预算已用尽: " + run.getMaxToolCalls());
                }
            }
            if ("succeeded".equals(call.status())) {
                state.markToolSucceeded();
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", call.status());
            payload.put("callId", call.callId());
            payload.put("toolName", call.toolName());
            payload.put("arguments", call.arguments());
            payload.put("result", call.result());
            payload.put("error", call.error());
            payload.put("durationMillis", call.durationMillis());
            runService.recordEvent(run.getRunId(),
                    "tool." + call.status(), stepId, payload);
        });
        ToolRuntimeContext runtime = resolved.toolRuntimeContext() == null
                ? (attachmentTools.runtime() == null
                ? new ToolRuntimeContext(resolved.skillRuntime() == null
                ? new SkillRuntimeLimitsVo(15, 2, 8_000, 48_000, null)
                : resolved.skillRuntime().getLimits())
                : attachmentTools.runtime())
                : resolved.toolRuntimeContext();
        return new McpToolContext(logging, systemPrompt, invocation,
                resolved.skillRuntime(), runtime);
    }

    private void handleApprovalEvent(String runId,
                                     String stepId,
                                     AgentRunExecutionState state,
                                     McpApprovalEvent event,
                                     McpToolLogStream toolEvidence) {
        McpApprovalVo approval = event == null ? null : event.data();
        String requestId = approval == null ? "" : approval.getRequestId();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (approval != null) {
            payload.put("requestId", approval.getRequestId());
            payload.put("toolName", approval.getToolName());
            payload.put("status", approval.getStatus());
            payload.put("description", approval.getDescription());
            payload.put("riskLevel", approval.getRiskLevel());
            payload.put("agentRunId", approval.getAgentRunId());
            payload.put("runApprovalAllowed", approval.getRunApprovalAllowed());
        }
        if (toolEvidence != null) {
            toolEvidence.recordApproval(event);
        }
        if (event != null && "approval_required".equals(event.event())) {
            state.markApprovalPending();
            runService.markApprovalRequired(runId, requestId, stepId);
            runService.recordEvent(runId, "tool.approval_required", stepId, payload);
        } else if (event != null) {
            runService.markApprovalUpdated(runId, requestId,
                    approval == null ? "" : String.valueOf(approval.getStatus()), stepId);
            runService.recordEvent(runId, "tool.approval_updated", stepId, payload);
        }
    }

    private void handleStreamValue(String runId,
                                   String stepId,
                                   String value,
                                   StringBuilder assistantText,
                                   AtomicBoolean truncated) {
        if (value == null) {
            return;
        }
        if (value.startsWith(AIChatService.OUTPUT_TRUNCATED_MARKER_PREFIX)) {
            truncated.set(true);
            return;
        }
        if (value.startsWith(RagSearchEventData.STREAM_MARKER_PREFIX)) {
            String json = value.substring(RagSearchEventData.STREAM_MARKER_PREFIX.length());
            runService.recordEvent(runId, "rag.search", stepId, parsePayload(json));
            return;
        }
        assistantText.append(value);
        runService.recordEvent(runId, "assistant.delta", stepId, Map.of("content", value));
    }

    private AgentRunPrompt buildPrompt(AgentRun run,
                                       String stepId,
                                       User user,
                                       ContextCompactionObserver compactionObserver) {
        ChatSession session = chatSessionRepository.findById(run.getChatSessionId()).orElse(null);
        AgentRunContextAssembler.PromptContext context = contextAssembler.assemble(
                run, session, user, stepId, compactionObserver);
        String text = context.text();
        if ("agent_report".equals(run.getAgentType()) && planComplete(parsePlan(run.getPlanJson()))
                && !reportDraftService.hasFinalizedDraft(run.getRunId())) {
            text += "\n计划已完成但报表草稿尚未 finalize，请先调用 report_finalize。";
        }
        return new AgentRunPrompt(text, context.attachments(), context.pendingInputIds());
    }

    private ContextCompactionObserver compactionObserver(AgentRun run, String stepId) {
        return event -> recordCompactionEvent(run.getRunId(), stepId, event);
    }

    private void recordCompactionEvent(String runId,
                                       String stepId,
                                       ContextCompactionEventData event) {
        if (event == null) {
            return;
        }
        try {
            runService.recordEvent(runId, "context.compacted", stepId, event);
        } catch (RuntimeException e) {
            log.warn("记录 Run 上下文压缩事件失败: runId={}, stepId={}, reason={}",
                    runId, stepId, event.getReason(), e);
        }
    }

    private ChatDto buildChatDto(AgentRun run, String prompt, List<ChatAttachment> attachments) {
        ChatDto chatDto = new ChatDto();
        chatDto.setChatId(run.getChatId());
        chatDto.setType(run.getChatType());
        chatDto.setModel(run.getModel());
        chatDto.setMessage(prompt);
        chatDto.setAttachments(attachments);
        try {
            Map<String, Object> request = JacksonUtil.toMap(
                    run.getRequestJson(), new TypeReference<Map<String, Object>>() {});
            Object reportAction = request.get("reportAction");
            if (reportAction instanceof Map<?, ?> map) {
                chatDto.setReportAction(JacksonUtil.toObject(
                        JacksonUtil.toJson(map), ReportActionDto.class));
            }
        } catch (RuntimeException ignored) {
            // 旧 Run 可能没有 request_json，保持普通 Agent 行为。
        }
        return chatDto;
    }

    private void resumeApprovedTools(AgentRun run,
                                     ChatSession session,
                                     String stepId,
                                     McpToolContext toolContext,
                                     AgentRunExecutionState state) {
        String currentTurnId = "run:" + run.getRunId() + ":" + stepId;
        boolean dataPush = session != null
                && DataPushServiceAgent.AGENT_TYPE.equals(session.getType());
        List<McpToolInvocation> approved = invocationRepository
                .findByAgentRunIdAndStatusIn(runId(run),
                        List.of(com.coolxer.commons.enums.McpInvocationStatus.APPROVED));
        if (approved.isEmpty() || toolContext.toolCallbackProvider() == null) {
            return;
        }
        for (McpToolInvocation invocation : approved) {
            if (!dataPush && !currentTurnId.equals(invocation.getTurnId())) {
                continue;
            }
            ToolCallback callback = findToolCallback(
                    toolContext.toolCallbackProvider(), invocation.getToolName());
            if (callback == null) {
                runService.recordEvent(run.getRunId(), "tool.approval_unavailable", stepId,
                        Map.of("requestId", invocation.getRequestId(),
                                "toolName", invocation.getToolName()));
                continue;
            }
            String result = callback.call(invocation.getArguments(),
                    restoredToolContext(toolContext, invocation));
            state.markToolSucceeded();
            completeApprovedInvocationIfNeeded(invocation, result);
            runService.recordEvent(run.getRunId(), "tool.approval_executed", stepId,
                    Map.of("requestId", invocation.getRequestId(),
                            "toolName", invocation.getToolName(),
                            "result", StringUtils.left(StringUtils.defaultString(result), 8_000)));
            runService.saveCheckpoint(run.getRunId(), StringUtils.left(
                    "【已执行获批工具】" + invocation.getToolName() + "\n"
                            + StringUtils.defaultString(result), 24_000));
        }
    }

    private ToolContext restoredToolContext(McpToolContext toolContext,
                                            McpToolInvocation invocation) {
        Map<String, Object> contextMap = new LinkedHashMap<>();
        McpInvocationContext current = toolContext.invocationContext();
        if (current != null) {
            McpInvocationContext restored = new McpInvocationContext(
                    current.channel(),
                    current.requesterUserId(),
                    current.chatId(),
                    invocation.getTurnId(),
                    current.agentType(),
                    current.mcpSessionId(),
                    current.mcpClientInfo(),
                    current.eventConsumer(),
                    current.analysisTaskId(),
                    current.executionId(),
                    current.taskApprovalMode(),
                    current.cancelled());
            contextMap.put(McpInvocationContext.TOOL_CONTEXT_KEY, restored);
        }
        if (toolContext.toolRuntimeContext() != null) {
            contextMap.put(ToolRuntimeContext.TOOL_CONTEXT_KEY,
                    toolContext.toolRuntimeContext());
        }
        return new ToolContext(contextMap);
    }

    private void completeApprovedInvocationIfNeeded(McpToolInvocation invocation,
                                                    String result) {
        McpToolInvocation latest = invocationRepository
                .findByRequestId(invocation.getRequestId())
                .orElse(invocation);
        if (latest.getStatus() != com.coolxer.commons.enums.McpInvocationStatus.APPROVED) {
            return;
        }
        latest.setStatus(com.coolxer.commons.enums.McpInvocationStatus.SUCCEEDED)
                .setResult(StringUtils.left(StringUtils.defaultString(result), 64_000))
                .setResultLength((long) StringUtils.defaultString(result)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .setFinishTime(new Date());
        invocationRepository.save(latest);
    }

    private ToolCallback findToolCallback(
            ToolCallbackProvider provider, String toolName) {
        if (provider == null || StringUtils.isBlank(toolName)) {
            return null;
        }
        for (ToolCallback callback : provider.getToolCallbacks()) {
            if (callback != null && callback.getToolDefinition() != null
                    && StringUtils.equals(toolName, callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    private String runId(AgentRun run) {
        return run.getRunId();
    }

    private List<ChatAttachment> loadAttachments(List<AgentRunInput> inputs) {
        List<ChatAttachment> attachments = new ArrayList<>();
        for (AgentRunInput input : inputs) {
            if (StringUtils.isBlank(input.getAttachments())) {
                continue;
            }
            try {
                List<ChatAttachment> parsed = JacksonUtil.toList(
                        input.getAttachments(), new TypeReference<List<ChatAttachment>>() {});
                if (parsed != null) {
                    attachments.addAll(parsed);
                }
            } catch (RuntimeException e) {
                log.warn("解析 Agent Run 附件失败: runId={}, inputId={}",
                        input.getRunId(), input.getId());
            }
        }
        return attachments;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRetryableModelFailure(Throwable error) {
        Integer status = httpStatus(error);
        if (status == null) {
            return true;
        }
        return status == 408 || status == 429 || status >= 500;
    }

    private Integer httpStatus(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                return responseException.getStatusCode().value();
            }
            Matcher matcher = HTTP_STATUS_PATTERN.matcher(
                    StringUtils.defaultString(current.getMessage()));
            if (matcher.find()) {
                String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private AgentRunPlan parsePlan(String json) {
        try {
            AgentRunPlan plan = JacksonUtil.toObject(json, AgentRunPlan.class);
            return plan == null ? AgentRunPlan.builder().steps(List.of()).build() : plan;
        } catch (RuntimeException e) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
    }

    private boolean planComplete(AgentRunPlan plan) {
        return plan.getSteps() != null && !plan.getSteps().isEmpty()
                && plan.getSteps().stream().allMatch(step ->
                "DONE".equalsIgnoreCase(step.getStatus())
                        || "SKIPPED".equalsIgnoreCase(step.getStatus()));
    }

    private Object parsePayload(String json) {
        try {
            return JacksonUtil.toMap(json, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            return Map.of("raw", json);
        }
    }

    private void saveFinalMessage(ChatSession session,
                                  User user,
                                  String runId,
                                  String answer,
                                  McpToolLogStream toolEvidence) {
        if (StringUtils.isBlank(answer)) {
            return;
        }
        chatApplicationService.saveAgentRunResponse(
                session, user, answer, MessageType.TEXT, null, runId, toolEvidence);
    }

    private void heartbeat(String runId, String leaseId) {
        try {
            Date now = new Date();
            runRepository.heartbeat(
                    runRepository.findByRunId(runId).map(AgentRun::getId).orElse(-1),
                    leaseId,
                    now,
                    new Date(now.getTime() + leaseSeconds * 1000L));
        } catch (RuntimeException e) {
            log.warn("Agent Run 心跳失败: runId={}, error={}", runId, e.getMessage());
        }
    }

    private String errorMessage(Throwable error) {
        return StringUtils.left(StringUtils.defaultIfBlank(
                error.getMessage(), error.getClass().getSimpleName()), 4_000);
    }

    private record AgentRunPrompt(String text,
                                  List<ChatAttachment> attachments,
                                  List<Integer> pendingInputIds) {
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }
}
