package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.commons.enums.McpInvocationStatus;
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
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.model.dih.agentrun.AgentRunInteraction;
import com.coolxer.model.dih.dto.AgentRunRequestDto;
import com.coolxer.model.dih.dto.AgentRunSteerDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.vo.AgentRunEventVo;
import com.coolxer.model.dih.vo.AgentRunVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihChatExecutionPolicy;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.demo.BuiltinDemoRequestDetector;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 持久化 Agent Run 的命令面和事件读取面。
 */
@Slf4j
@Service
public class AgentRunService {

    public static final List<AgentRunStatus> ACTIVE_STATUSES = List.of(
            AgentRunStatus.QUEUED,
            AgentRunStatus.RUNNING,
            AgentRunStatus.WAITING_APPROVAL,
            AgentRunStatus.WAITING_USER,
            AgentRunStatus.PAUSED,
            AgentRunStatus.CANCELING
    );

    private final AgentRunRepository runRepository;
    private final AgentRunEventRepository eventRepository;
    private final AgentRunInputRepository inputRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionService chatSessionService;
    private final SkillService skillService;
    private final BuiltinDemoRequestDetector builtinDemoRequestDetector;
    private final McpToolInvocationRepository invocationRepository;
    private final DataSource mysqlDataSource;
    private final TransactionTemplate mysqlTransactionTemplate;

    @Value("${app.ai.agent-run.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.agent-run.enabled-types:*}")
    private String enabledTypes;

    @Value("${app.ai.agent-run.disabled-types:}")
    private String disabledTypes;

    @Value("${app.ai.agent-run.max-steps:30}")
    private int defaultMaxSteps;

    @Value("${app.ai.agent-run.max-duration-seconds:1800}")
    private int defaultMaxDurationSeconds;

    @Value("${app.ai.agent-run.max-tool-calls:80}")
    private int defaultMaxToolCalls;

    @Value("${app.ai.agent-run.lease-seconds:60}")
    private long leaseSeconds;

    @Autowired(required = false)
    private AgentRunMetrics metrics;

    @Autowired(required = false)
    private ChatAttachmentService chatAttachmentService;

    public AgentRunService(AgentRunRepository runRepository,
                           AgentRunEventRepository eventRepository,
                           AgentRunInputRepository inputRepository,
                           ChatSessionRepository chatSessionRepository,
                           ChatSessionService chatSessionService,
                           SkillService skillService,
                           BuiltinDemoRequestDetector builtinDemoRequestDetector,
                           McpToolInvocationRepository invocationRepository,
                           @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
                           @Qualifier("mysqlTransactionManager")
                           PlatformTransactionManager mysqlTransactionManager) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.inputRepository = inputRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionService = chatSessionService;
        this.skillService = skillService;
        this.builtinDemoRequestDetector = builtinDemoRequestDetector;
        this.invocationRepository = invocationRepository;
        this.mysqlDataSource = mysqlDataSource;
        this.mysqlTransactionTemplate = new TransactionTemplate(mysqlTransactionManager);
        this.mysqlTransactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnabledFor(String agentType) {
        if (!enabled) {
            return false;
        }
        if (StringUtils.isBlank(agentType)) {
            return false;
        }
        if (matchesType(disabledTypes, agentType)) {
            return false;
        }
        return StringUtils.equals(StringUtils.trim(enabledTypes), "*")
                || matchesType(enabledTypes, agentType);
    }

    private boolean matchesType(String configuredTypes, String agentType) {
        return java.util.Arrays.stream(StringUtils.split(configuredTypes, ','))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .anyMatch(agentType::equals);
    }

    public AgentRunVo create(AgentRunRequestDto request, User currentUser) {
        requireEnabled();
        if (currentUser == null) {
            throw new ApiException(403, "登录状态已失效");
        }
        if (request == null || StringUtils.isBlank(request.getChatId())) {
            throw new ApiException(400, "会话ID不能为空");
        }
        String message = StringUtils.trimToEmpty(request.getMessage());
        if (StringUtils.isBlank(message)
                && (request.getAttachments() == null || request.getAttachments().isEmpty())) {
            throw new ApiException(400, "消息内容或附件不能为空");
        }
        if (StringUtils.isBlank(message)) {
            message = "请分析上传的附件内容。";
        }
        if (chatAttachmentService != null) {
            chatAttachmentService.ensureSendable(request.getAttachments(), currentUser);
            chatAttachmentService.bindToChat(
                    request.getAttachments(), currentUser, request.getChatId());
        }
        DihChatExecutionPolicy policy = DihChatExecutionPolicy
                .resolve(StringUtils.trimToEmpty(request.getType()), skillService)
                .orElseThrow(() -> new ApiException(400, "会话类型不支持"));
        if (!policy.isAgent()) {
            throw new ApiException(400, "普通问答请使用轻量聊天接口");
        }
        if (!isEnabledFor(policy.agentType())) {
            throw new ApiException(503, "当前智能体类型尚未启用持久化任务");
        }
        String createLockKey = runCreateLockKey(request.getChatId(), currentUser.getId());
        String normalizedMessage = message;
        return withCreateLock(createLockKey, () -> mysqlTransactionTemplate.execute(status ->
                createLocked(request, normalizedMessage, policy, currentUser)));
    }

    private AgentRunVo createLocked(AgentRunRequestDto request,
                                    String message,
                                    DihChatExecutionPolicy policy,
                                    User currentUser) {
        ChatSession session = chatSessionRepository
                .findBySessionIdAndCreateByForUpdate(request.getChatId(), currentUser.getId())
                .orElse(null);
        if (builtinDemoRequestDetector.isBuiltinDemoRequest(
                policy.agentType(), request.getChatId(), message, currentUser, session)) {
            throw new ApiException(503, "内置演示使用聊天示例链路，不创建持久化任务");
        }
        AgentRun active = runRepository
                .findFirstByChatIdAndCreateByAndStatusInOrderByCreateTimeDesc(
                        request.getChatId(), currentUser.getId(), ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            AgentRunVo vo = steer(active.getRunId(), request, currentUser);
            vo.setSteered(true);
            return vo;
        }
        String runId = UUID.randomUUID().toString();
        if (session == null) {
            ChatSessionDto defaults = new ChatSessionDto();
            defaults.setSessionId(request.getChatId());
            defaults.setType(policy.chatType());
            Message userMessage = userMessage(message, request.getAttachments(), runId);
            session = chatSessionService.appendMessage(
                    request.getChatId(), defaults, userMessage, currentUser);
        } else {
            chatSessionService.appendMessage(
                    session, userMessage(message, request.getAttachments(), runId), currentUser);
        }

        AgentRunPlan plan = initialPlan(message);
        AgentRun run = new AgentRun()
                .setRunId(runId)
                .setChatId(request.getChatId())
                .setChatSessionId(session.getId())
                .setChatType(policy.chatType())
                .setAgentType(policy.agentType())
                .setSkillIds(JacksonUtil.toJson(policy.skillIds()))
                .setModel(request.getModel())
                .setStatus(AgentRunStatus.QUEUED)
                .setGoal(message)
                .setPlanJson(JacksonUtil.toJson(plan))
                .setPlanRevision(1)
                .setCurrentStepId(plan.getSteps().isEmpty() ? null : plan.getSteps().get(0).getId())
                .setMaxSteps(defaultMaxSteps)
                .setMaxDurationSeconds(defaultMaxDurationSeconds)
                .setMaxToolCalls(defaultMaxToolCalls)
                .setRequestJson(JacksonUtil.toJson(request));
        run.setCreateBy(currentUser.getId());
        run.setUpdateBy(currentUser.getId());
        run = runRepository.save(run);

        AgentRunInput input = new AgentRunInput()
                .setRunId(runId)
                .setClientMessageId(clientMessageId(request.getClientMessageId()))
                .setMessageId(runId)
                .setContent(message)
                .setAttachments(JacksonUtil.toJson(request.getAttachments() == null
                        ? List.of() : request.getAttachments()))
                .setStatus(AgentRunInputStatus.APPLIED)
                .setAppliedAt(new Date());
        input.setCreateBy(currentUser.getId());
        input.setUpdateBy(currentUser.getId());
        inputRepository.save(input);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run", AgentRunVo.from(run));
        appendEvent(run, "run.created", run.getCurrentStepId(), payload);
        appendEvent(run, "run.snapshot", run.getCurrentStepId(), Map.of("run", AgentRunVo.from(run)));
        runRepository.save(run);
        return AgentRunVo.from(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunVo steer(String runId, AgentRunRequestDto request, User currentUser) {
        AgentRun run = requireOwnedRunForUpdate(runId, currentUser);
        if (run.getStatus().isTerminal()) {
            throw new ApiException(409, "任务已结束，请创建新的任务");
        }
        AgentRunSteerDto steer = new AgentRunSteerDto();
        steer.setMessage(request.getMessage());
        steer.setAttachments(request.getAttachments());
        steer.setClientMessageId(request.getClientMessageId());
        return steer(run, steer, currentUser);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunVo steer(String runId, AgentRunSteerDto request, User currentUser) {
        AgentRun run = requireOwnedRunForUpdate(runId, currentUser);
        if (run.getStatus().isTerminal()) {
            throw new ApiException(409, "任务已结束，请创建新的任务");
        }
        if (run.getStatus() == AgentRunStatus.CANCELING) {
            throw new ApiException(409, "任务正在取消，请稍后再试");
        }
        return steer(run, request, currentUser);
    }

    private AgentRunVo steer(AgentRun run, AgentRunSteerDto request, User currentUser) {
        String clientMessageId = clientMessageId(request.getClientMessageId());
        AgentRunInput existing = inputRepository
                .findByRunIdAndClientMessageId(run.getRunId(), clientMessageId)
                .orElse(null);
        if (existing != null) {
            AgentRunVo vo = AgentRunVo.from(run);
            vo.setSteered(true);
            return vo;
        }
        String message = StringUtils.trimToEmpty(request.getMessage());
        if (StringUtils.isBlank(message)
                && (request.getAttachments() == null || request.getAttachments().isEmpty())) {
            throw new ApiException(400, "追加消息不能为空");
        }
        if (StringUtils.isBlank(message)) {
            message = "请分析追加的附件内容。";
        }
        if (chatAttachmentService != null) {
            chatAttachmentService.ensureSendable(request.getAttachments(), currentUser);
            chatAttachmentService.bindToChat(
                    request.getAttachments(), currentUser, run.getChatId());
        }
        ChatSession session = chatSessionRepository
                .findBySessionIdAndCreateByForUpdate(run.getChatId(), currentUser.getId())
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        Message messageEntity = userMessage(message, request.getAttachments(), run.getRunId());
        chatSessionService.appendMessage(session, messageEntity, currentUser);

        AgentRunInput input = new AgentRunInput()
                .setRunId(run.getRunId())
                .setClientMessageId(clientMessageId)
                .setMessageId(messageEntity.getId())
                .setContent(message)
                .setAttachments(JacksonUtil.toJson(request.getAttachments() == null
                        ? List.of() : request.getAttachments()))
                .setStatus(AgentRunInputStatus.PENDING);
        input.setCreateBy(currentUser.getId());
        input.setUpdateBy(currentUser.getId());
        inputRepository.save(input);

        if (run.getStatus() == AgentRunStatus.PAUSED
                || run.getStatus() == AgentRunStatus.WAITING_USER) {
            run.setStatus(AgentRunStatus.QUEUED)
                    .setPausedReason(null)
                    .setInteractionJson(null)
                    .setPendingAction(null)
                    .setLeaseId(null)
                    .setLeaseUntil(null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputId", input.getId());
        payload.put("message", message);
        payload.put("messageId", messageEntity.getId());
        payload.put("status", input.getStatus());
        appendEvent(run, "steer.queued", run.getCurrentStepId(), payload);
        run.setUpdateBy(currentUser.getId());
        runRepository.save(run);
        AgentRunVo vo = AgentRunVo.from(run);
        vo.setSteered(true);
        return vo;
    }

    public AgentRunVo get(String runId, User currentUser) {
        return AgentRunVo.from(requireOwnedRun(runId, currentUser));
    }

    public PageRowsVo<AgentRunVo> list(String chatId,
                                       AgentRunStatus status,
                                       int page,
                                       int perPage,
                                       User currentUser) {
        if (currentUser == null) {
            throw new ApiException(403, "登录状态已失效");
        }
        Page<AgentRun> result = runRepository.findByPage(
                PageRequest.of(Math.max(page, 1) - 1, Math.max(perPage, 1)),
                StringUtils.trimToNull(chatId),
                status,
                currentUser.getId());
        return new PageRowsVo<>(
                result.getContent().stream().map(AgentRunVo::from).toList(),
                result.getTotalElements());
    }

    public List<AgentRunEventVo> events(String runId, long after, User currentUser) {
        return events(runId, after, List.of(), currentUser);
    }

    public List<AgentRunEventVo> events(String runId,
                                        long after,
                                        List<String> types,
                                        User currentUser) {
        requireOwnedRun(runId, currentUser);
        return findEvents(runId, Math.max(after, 0), normalizeEventTypes(types))
                .stream().map(AgentRunEventVo::from).toList();
    }

    public Flux<String> eventStream(String runId, long after, User currentUser) {
        return eventStream(runId, after, List.of(), currentUser);
    }

    public Flux<String> eventStream(String runId,
                                    long after,
                                    List<String> types,
                                    User currentUser) {
        requireOwnedRun(runId, currentUser);
        List<String> normalizedTypes = normalizeEventTypes(types);
        return Flux.create(sink -> {
            AtomicLong cursor = new AtomicLong(Math.max(after, 0));
            AtomicBoolean stopped = new AtomicBoolean();
            var task = Schedulers.boundedElastic().schedulePeriodically(() -> {
                if (stopped.get() || sink.isCancelled()) {
                    return;
                }
                try {
                    while (true) {
                        List<AgentRunEventVo> batch = findEvents(
                                runId, cursor.get(), normalizedTypes)
                                .stream().map(AgentRunEventVo::from).toList();
                        for (AgentRunEventVo event : batch) {
                            sink.next(JacksonUtil.toJson(event) + "\n");
                            cursor.accumulateAndGet(event.getSequence(), Math::max);
                        }
                        if (!batch.isEmpty()) {
                            cursor.accumulateAndGet(
                                    batch.get(batch.size() - 1).getSequence(), Math::max);
                        }
                        if (batch.size() < 500) {
                            break;
                        }
                    }
                    boolean terminal = runRepository.findByRunId(runId)
                            .map(run -> run.getStatus().isTerminal())
                            .orElse(true);
                    if (terminal) {
                        List<AgentRunEvent> remainingRows = findEvents(
                                runId, cursor.get(), normalizedTypes);
                        List<AgentRunEventVo> remaining = remainingRows
                                .stream().map(AgentRunEventVo::from).toList();
                        remaining.forEach(event -> {
                            sink.next(JacksonUtil.toJson(event) + "\n");
                            cursor.accumulateAndGet(event.getSequence(), Math::max);
                        });
                        if (remaining.isEmpty() && stopped.compareAndSet(false, true)) {
                            sink.complete();
                        }
                    }
                } catch (RuntimeException e) {
                    if (stopped.compareAndSet(false, true)) {
                        sink.error(e);
                    }
                }
            }, 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            sink.onDispose(() -> {
                stopped.set(true);
                task.dispose();
            });
        });
    }

    private List<AgentRunEvent> findEvents(String runId, long after, List<String> types) {
        return types.isEmpty()
                ? eventRepository.findTop500ByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
                        runId, after)
                : eventRepository.findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
                        runId, types, after);
    }

    private List<String> normalizeEventTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunVo pause(String runId, String reason, User currentUser) {
        AgentRun run = requireOwnedRunForUpdate(runId, currentUser);
        if (run.getStatus().isTerminal()) {
            return AgentRunVo.from(run);
        }
        run.setStatus(AgentRunStatus.PAUSED)
                .setPausedReason(StringUtils.left(StringUtils.defaultIfBlank(reason, "用户暂停"), 255))
                .setInteractionJson(null)
                .setPendingAction(null)
                .setLeaseId(null)
                .setLeaseUntil(null);
        runRepository.save(run);
        appendEvent(run, "run.paused", run.getCurrentStepId(), Map.of("reason", run.getPausedReason()));
        return AgentRunVo.from(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunVo resume(String runId, User currentUser) {
        AgentRun run = requireOwnedRunForUpdate(runId, currentUser);
        if (run.getStatus().isTerminal()) {
            throw new ApiException(409, "已结束的任务不能继续");
        }
        if (run.getStatus() == AgentRunStatus.WAITING_USER
                && AgentRunInteraction.fromRun(run) != null) {
            throw new ApiException(409, "请先处理当前交互再继续任务");
        }
        if (run.getStatus() != AgentRunStatus.PAUSED) {
            return AgentRunVo.from(run);
        }
        if (run.getDecisionSteps() >= run.getMaxSteps()) {
            run.setMaxSteps(run.getMaxSteps() + defaultMaxSteps);
        }
        if (run.getEffectiveMillis() >= run.getMaxDurationSeconds() * 1000L) {
            run.setMaxDurationSeconds(run.getMaxDurationSeconds() + defaultMaxDurationSeconds);
        }
        run.setStatus(AgentRunStatus.QUEUED)
                .setPausedReason(null)
                .setLastError(null)
                .setNoProgressCount(0)
                .setInteractionJson(null)
                .setPendingAction(null)
                .setLeaseId(null)
                .setLeaseUntil(null);
        runRepository.save(run);
        appendEvent(run, "run.resumed", run.getCurrentStepId(), Map.of());
        return AgentRunVo.from(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRunVo cancel(String runId, User currentUser) {
        AgentRun run = requireOwnedRunForUpdate(runId, currentUser);
        if (run.getStatus().isTerminal()) {
            return AgentRunVo.from(run);
        }
        if (run.getStatus() == AgentRunStatus.CANCELING) {
            return AgentRunVo.from(run);
        }
        if (run.getStatus() == AgentRunStatus.RUNNING) {
            run.setStatus(AgentRunStatus.CANCELING);
        } else {
            run.setStatus(AgentRunStatus.CANCELLED)
                    .setFinishedAt(new Date())
                    .setInteractionJson(null)
                    .setPendingAction(null)
                    .setLeaseId(null)
                    .setLeaseUntil(null);
            recordTerminalMetric(AgentRunStatus.CANCELLED);
        }
        runRepository.save(run);
        cancelPendingInvocations(run.getRunId());
        appendEvent(run, run.getStatus() == AgentRunStatus.CANCELLED
                ? "run.cancelled" : "run.canceling", run.getCurrentStepId(), Map.of());
        return AgentRunVo.from(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void recordEvent(String runId, String type, String stepId, Object data) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        appendEvent(run, type, stepId, data);
        runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun updatePlan(String runId, AgentRunPlan plan, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        run.setPlanJson(JacksonUtil.toJson(plan))
                .setPlanRevision(run.getPlanRevision() + 1)
                .setCurrentStepId(firstPendingStepId(plan));
        appendEvent(run, "plan.updated", stepId, Map.of("plan", plan, "revision", run.getPlanRevision()));
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun updateStep(String runId,
                               String stepId,
                               String status,
                               String summary,
                               List<Map<String, Object>> evidenceRefs) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        AgentRunPlan plan = parsePlan(run.getPlanJson());
        AgentRunPlanStep matched = null;
        for (AgentRunPlanStep step : plan.getSteps()) {
            if (StringUtils.equals(step.getId(), stepId)) {
                step.setStatus(StringUtils.defaultIfBlank(status, step.getStatus()));
                step.setSummary(StringUtils.left(
                        StringUtils.defaultString(summary), 4000));
                if (evidenceRefs != null) {
                    step.setEvidenceRefs(new ArrayList<>(evidenceRefs));
                }
                matched = step;
                break;
            }
        }
        if (matched == null) {
            throw new ApiException(400, "计划步骤不存在: " + stepId);
        }
        run.setPlanJson(JacksonUtil.toJson(plan))
                .setCurrentStepId(firstPendingStepId(plan));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", matched);
        appendEvent(run, "step.updated", stepId, payload);
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markWaitingUser(String runId, String question, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        AgentRunInteraction existing = AgentRunInteraction.fromRun(run);
        String interactionId = existing != null
                && AgentRunInteraction.KIND_USER_INPUT.equals(existing.getKind())
                && StringUtils.isNotBlank(existing.getInteractionId())
                ? existing.getInteractionId() : UUID.randomUUID().toString();
        AgentRunInteraction interaction = AgentRunInteraction.builder()
                .interactionId(interactionId)
                .kind(AgentRunInteraction.KIND_USER_INPUT)
                .title("需要补充信息")
                .prompt(StringUtils.defaultIfBlank(question, "请补充必要信息后继续"))
                .createdAt(new Date())
                .build();
        run.setStatus(AgentRunStatus.WAITING_USER)
                .setInteractionJson(JacksonUtil.toJson(interaction))
                .setPendingAction(JacksonUtil.toJson(Map.of(
                        "type", "user",
                        "question", StringUtils.defaultString(question),
                        "interactionId", interactionId)))
                .setPausedReason(StringUtils.left(StringUtils.defaultIfBlank(question, "等待用户输入"), 255))
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "run.waiting_user", stepId, Map.of(
                "question", StringUtils.defaultString(question),
                "interactionId", interactionId));
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markWaitingForCard(String runId,
                                       AgentRunInteraction interaction,
                                       String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        if (interaction == null
                || StringUtils.isBlank(interaction.getInteractionId())
                || !AgentRunInteraction.KIND_CARD_DECISION.equals(interaction.getKind())) {
            throw new IllegalArgumentException("卡片交互不能为空");
        }
        run.setStatus(AgentRunStatus.WAITING_USER)
                .setInteractionJson(JacksonUtil.toJson(interaction))
                .setPendingAction(JacksonUtil.toJson(Map.of(
                        "type", "card",
                        "interactionId", interaction.getInteractionId(),
                        "partId", StringUtils.defaultString(interaction.getPartId()),
                        "question", StringUtils.defaultString(interaction.getPrompt()))))
                .setPausedReason(StringUtils.left(StringUtils.defaultIfBlank(
                        interaction.getPrompt(), "请处理当前智能体工作流卡片后继续"), 255))
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "run.waiting_user", stepId, Map.of(
                "question", StringUtils.defaultString(interaction.getPrompt()),
                "interactionId", interaction.getInteractionId(),
                "kind", interaction.getKind()));
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markSucceeded(String runId, String answer, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        run.setStatus(AgentRunStatus.SUCCEEDED)
                .setFinalAnswer(answer)
                .setFinishedAt(new Date())
                .setLeaseId(null)
                .setLeaseUntil(null)
                .setInteractionJson(null)
                .setPendingAction(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answer", StringUtils.defaultString(answer));
        eventRepository.findFirstByRunIdAndTypeOrderBySequenceDesc(runId, "artifact.updated")
                .ifPresent(event -> {
                    if (StringUtils.isNotBlank(event.getPayload())) {
                        try {
                            payload.put("artifact", JacksonUtil.toMap(
                                    event.getPayload(),
                                    new TypeReference<Map<String, Object>>() {}));
                        } catch (RuntimeException ignored) {
                            payload.put("artifactRaw", event.getPayload());
                        }
                    }
                });
        appendEvent(run, "run.succeeded", stepId, payload);
        recordTerminalMetric(AgentRunStatus.SUCCEEDED);
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markPaused(String runId, String reason, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        run.setStatus(AgentRunStatus.PAUSED)
                .setPausedReason(StringUtils.left(StringUtils.defaultIfBlank(reason, "任务暂停"), 255))
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "run.paused", stepId, Map.of("reason", run.getPausedReason()));
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markFailed(String runId, String error, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        run.setStatus(AgentRunStatus.FAILED)
                .setLastError(StringUtils.left(StringUtils.defaultString(error), 8000))
                .setFinishedAt(new Date())
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "run.failed", stepId, Map.of("error", run.getLastError()));
        recordTerminalMetric(AgentRunStatus.FAILED);
        if (metrics != null) {
            metrics.failure("run");
        }
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun checkpointStep(String runId,
                                   long elapsedMillis,
                                   int toolCalls,
                                   boolean progressed,
                                   String pendingApproval,
                                   String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        run.setDecisionSteps(run.getDecisionSteps() + 1)
                .setEffectiveMillis(run.getEffectiveMillis() + Math.max(elapsedMillis, 0))
                .setToolCalls(run.getToolCalls() + Math.max(toolCalls, 0))
                .setNoProgressCount(progressed ? 0 : run.getNoProgressCount() + 1)
                .setLastError(null);
        if (StringUtils.isNotBlank(pendingApproval)) {
            run.setPendingAction(pendingApproval);
        }
        return runRepository.save(run);
    }

    /**
     * 模型调用失败属于执行重试，不应消耗 Run 的决策步预算。
     */
    @Transactional(transactionManager = "mysqlTransactionManager")
    public int checkpointFailure(String runId,
                                 long elapsedMillis,
                                 int toolCalls,
                                 String error,
                                 String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        int failureCount = (run.getNoProgressCount() == null ? 0 : run.getNoProgressCount()) + 1;
        run.setEffectiveMillis(run.getEffectiveMillis() + Math.max(elapsedMillis, 0))
                .setToolCalls(run.getToolCalls() + Math.max(toolCalls, 0))
                .setNoProgressCount(failureCount)
                .setLastError(StringUtils.left(StringUtils.defaultString(error), 8000));
        if (StringUtils.isBlank(run.getCurrentStepId())) {
            run.setCurrentStepId(stepId);
        }
        runRepository.save(run);
        return failureCount;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveCheckpoint(String runId, String checkpoint) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        run.setContextCheckpoint(StringUtils.left(
                StringUtils.defaultString(checkpoint), 24_000));
        runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun markCancelled(String runId, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus().isTerminal()) {
            return run;
        }
        run.setStatus(AgentRunStatus.CANCELLED)
                .setFinishedAt(new Date())
                .setInteractionJson(null)
                .setPendingAction(null)
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "run.cancelled", stepId, Map.of());
        recordTerminalMetric(AgentRunStatus.CANCELLED);
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public AgentRun requeue(String runId, String stepId, String reason) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus() == AgentRunStatus.CANCELING
                || run.getStatus() == AgentRunStatus.PAUSED
                || run.getStatus() == AgentRunStatus.WAITING_USER) {
            return run;
        }
        run.setStatus(AgentRunStatus.QUEUED)
                .setLeaseId(null)
                .setLeaseUntil(null)
                .setPausedReason(StringUtils.left(StringUtils.defaultString(reason), 255));
        appendEvent(run, "run.queued", stepId, Map.of("reason", StringUtils.defaultString(reason)));
        return runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void markApprovalRequired(String runId, String requestId, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        AgentRunInteraction interaction = AgentRunInteraction.builder()
                .interactionId(UUID.randomUUID().toString())
                .kind(AgentRunInteraction.KIND_MCP_APPROVAL)
                .requestId(requestId)
                .title("MCP 工具审批")
                .prompt("该工具需要你的明确许可后才能执行。")
                .createdAt(new Date())
                .build();
        run.setStatus(AgentRunStatus.WAITING_APPROVAL)
                .setInteractionJson(JacksonUtil.toJson(interaction))
                .setPendingAction(JacksonUtil.toJson(Map.of("type", "approval", "requestId", requestId)))
                .setLeaseId(null)
                .setLeaseUntil(null);
        appendEvent(run, "approval.required", stepId, Map.of(
                "requestId", requestId,
                "interactionId", interaction.getInteractionId()));
        runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void markApprovalUpdated(String runId, String requestId, String status, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (run.getStatus() == AgentRunStatus.WAITING_APPROVAL) {
            boolean expired = McpInvocationStatus.EXPIRED.name().equals(status);
            run.setStatus(expired ? AgentRunStatus.PAUSED : AgentRunStatus.QUEUED)
                    .setPendingAction(null)
                    .setInteractionJson(null)
                    .setPausedReason(expired ? "MCP工具审批已超时" : null)
                    .setLeaseId(null)
                    .setLeaseUntil(null);
        }
        appendEvent(run, "approval.updated", stepId,
                Map.of("requestId", requestId, "status", StringUtils.defaultString(status)));
        runRepository.save(run);
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public int recordTruncation(String runId, String stepId, String partialContent) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        int count = StringUtils.equals(run.getTruncationStepId(), stepId)
                ? (run.getTruncationCount() == null ? 0 : run.getTruncationCount()) + 1 : 1;
        run.setTruncationStepId(stepId).setTruncationCount(count);
        if (StringUtils.isNotBlank(partialContent)) {
            run.setContextCheckpoint(StringUtils.left(
                    "【当前步骤上次输出被截断，以下内容不完整，禁止视为已完成】\n" + partialContent,
                    24_000));
        }
        appendEvent(run, "run.output_truncated", stepId,
                Map.of("attempt", count, "maxAttempts", 2));
        if (metrics != null) {
            metrics.truncation();
        }
        runRepository.save(run);
        return count;
    }

    @Transactional(transactionManager = "mysqlTransactionManager")
    public void clearTruncation(String runId, String stepId) {
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (StringUtils.equals(run.getTruncationStepId(), stepId)) {
            run.setTruncationCount(0).setTruncationStepId(null);
            runRepository.save(run);
        }
    }

    private void cancelPendingInvocations(String runId) {
        List<com.coolxer.dao.mysql.entity.McpToolInvocation> invocations =
                invocationRepository.findByAgentRunIdAndStatusIn(runId,
                        List.of(McpInvocationStatus.PENDING, McpInvocationStatus.APPROVED));
        for (var invocation : invocations) {
            invocation.setStatus(McpInvocationStatus.CANCELLED)
                    .setErrorSummary("智能体任务已取消")
                    .setFinishTime(new Date());
        }
        if (!invocations.isEmpty()) {
            invocationRepository.saveAll(invocations);
        }
    }

    public AgentRun requireOwnedRun(String runId, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(403, "登录状态已失效");
        }
        return runRepository.findByRunIdAndCreateBy(runId, currentUser.getId())
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
    }

    private AgentRun requireOwnedRunForUpdate(String runId, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(403, "登录状态已失效");
        }
        AgentRun run = runRepository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new ApiException(404, "任务不存在"));
        if (!currentUser.getId().equals(run.getCreateBy())) {
            throw new ApiException(404, "任务不存在");
        }
        return run;
    }

    AgentRun lockOwnedRun(String runId, User currentUser) {
        return requireOwnedRunForUpdate(runId, currentUser);
    }

    void completeInteraction(AgentRun run,
                             String interactionId,
                             String stepId,
                             boolean requeue,
                             Map<String, Object> payload) {
        run.setInteractionJson(null).setPendingAction(null);
        if (requeue) {
            run.setStatus(AgentRunStatus.QUEUED)
                    .setPausedReason(null)
                    .setLeaseId(null)
                    .setLeaseUntil(null);
        }
        appendEvent(run, "interaction.responded", stepId, payload);
        runRepository.save(run);
    }

    private void appendEvent(AgentRun run, String type, String stepId, Object data) {
        long sequence = Math.max(
                run.getLastEventSequence() == null ? 0L : run.getLastEventSequence(),
                eventRepository.maxSequence(run.getRunId())) + 1;
        AgentRunEvent event = new AgentRunEvent()
                .setRunId(run.getRunId())
                .setSequence(sequence)
                .setType(type)
                .setStepId(stepId)
                .setPayload(data == null ? "{}" : JacksonUtil.toJson(data));
        event.setCreateBy(run.getCreateBy());
        event.setUpdateBy(run.getCreateBy());
        eventRepository.save(event);
        run.setLastEventSequence(sequence);
    }

    private String runCreateLockKey(String chatId, Integer userId) {
        try {
            String raw = StringUtils.defaultString(userId + ":" + chatId);
            return "dih_run:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 48);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 Agent Run 创建锁", e);
        }
    }

    private AgentRunVo withCreateLock(String lockKey, java.util.function.Supplier<AgentRunVo> action) {
        try (Connection lockConnection = mysqlDataSource.getConnection()) {
            try (PreparedStatement acquire = lockConnection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                acquire.setString(1, lockKey);
                acquire.setInt(2, 10);
                try (ResultSet result = acquire.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new ApiException(409, "该会话正在创建智能体任务，请稍后重试");
                    }
                }
            }
            try {
                return action.get();
            } finally {
                releaseNamedLock(lockConnection, lockKey);
            }
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException("无法获取 Agent Run 创建锁", e);
        }
    }

    private void releaseNamedLock(Connection connection, String lockKey) {
        try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            release.setString(1, lockKey);
            try (ResultSet ignored = release.executeQuery()) {
                // 结果只用于确认锁已释放；连接关闭时 MySQL 也会自动释放。
            }
        } catch (SQLException e) {
            log.warn("释放 Agent Run 创建锁失败: lockKey={}", lockKey, e);
        }
    }

    private void recordTerminalMetric(AgentRunStatus status) {
        if (metrics != null) {
            metrics.terminal(status);
        }
    }

    private AgentRunPlan initialPlan(String goal) {
        return AgentRunPlan.builder()
                .goal(goal)
                .steps(List.of(AgentRunPlanStep.builder()
                        .id("1")
                        .title("完成用户目标")
                        .acceptance("满足用户请求并给出可验证的完整答复")
                        .status("RUNNING")
                        .evidenceRefs(List.of())
                        .build()))
                .build();
    }

    private AgentRunPlan parsePlan(String json) {
        if (StringUtils.isBlank(json)) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
        try {
            AgentRunPlan plan = JacksonUtil.toObject(json, AgentRunPlan.class);
            return plan == null ? AgentRunPlan.builder().steps(List.of()).build() : plan;
        } catch (RuntimeException e) {
            return AgentRunPlan.builder().steps(List.of()).build();
        }
    }

    private String firstPendingStepId(AgentRunPlan plan) {
        if (plan == null || plan.getSteps() == null) {
            return null;
        }
        for (AgentRunPlanStep step : plan.getSteps()) {
            if (!"DONE".equalsIgnoreCase(step.getStatus())
                    && !"SKIPPED".equalsIgnoreCase(step.getStatus())) {
                return step.getId();
            }
        }
        return plan.getSteps().isEmpty() ? null
                : plan.getSteps().get(plan.getSteps().size() - 1).getId();
    }

    private Message userMessage(String content, List<ChatAttachment> attachments, String runId) {
        Message message = new Message("user", content);
        message.setRunId(runId);
        message.setInternal(false);
        if (attachments != null && !attachments.isEmpty()) {
            message.setAttachments(attachments);
        }
        return message;
    }

    private String clientMessageId(String clientMessageId) {
        return StringUtils.left(StringUtils.defaultIfBlank(
                clientMessageId, UUID.randomUUID().toString()), 128);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(503, "持久化智能体任务暂未启用");
        }
    }
}
