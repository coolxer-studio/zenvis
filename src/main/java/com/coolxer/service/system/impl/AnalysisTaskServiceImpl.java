package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.dao.mysql.repository.AnalysisTaskRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AgentLlmService;
import com.coolxer.service.dih.ChatMessagePartParser;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpApprovalEvent;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpTaskToolGrantService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.system.AnalysisTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persistent, one-shot AI analysis task queue.
 */
@Slf4j
@Service
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是 ZenVis 的 AI分析任务 Agent。请基于用户提供的任务提示词完成分析，输出结构清晰、结论明确的中文结果。
            如果提示词中包含数据、SQL结果、指标或上下文，请优先围绕这些信息分析；不要编造不存在的数据。
            输出建议包含：关键结论、过程说明、风险或异常点、下一步建议。
            MCP工具被拒绝或禁止时，不得再尝试绕过审批，应基于已有信息继续完成任务并说明限制。
            """;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();

    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;
    @Autowired
    private AgentLlmService agentLlmService;
    @Autowired
    private AIBaseService aiBaseService;
    @Autowired
    private AgentMcpToolService agentMcpToolService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private McpApprovalService mcpApprovalService;
    @Autowired
    private McpTaskToolGrantService taskToolGrantService;
    @Autowired
    private ChatMessagePartParser chatMessagePartParser;

    @Value("${app.ai.analysis-task.max-concurrency:1}")
    private int configuredMaxConcurrency = 1;

    @Value("${app.ai.analysis-task.max-suspended:20}")
    private int configuredMaxSuspended = 20;

    private final ReentrantLock dispatchLock = new ReentrantLock();
    private final ConcurrentHashMap<String, TaskExecutionControl> executions = new ConcurrentHashMap<>();
    private volatile ExecutorService workerPool;
    private volatile Semaphore runningSlots;
    private int maxConcurrency;
    private int maxSuspended;

    @PostConstruct
    public void initializeExecutor() {
        maxConcurrency = Math.max(configuredMaxConcurrency, 1);
        maxSuspended = Math.max(configuredMaxSuspended, 1);
        runningSlots = new Semaphore(maxConcurrency, true);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "analysis-task-worker-" + WORKER_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        workerPool = Executors.newFixedThreadPool(maxConcurrency + maxSuspended, threadFactory);
    }

    @PreDestroy
    public void shutdownExecutor() {
        ExecutorService executor = workerPool;
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public List<AnalysisTaskVo> findAll() {
        return analysisTaskRepository.findAll().stream().map(this::toVo).toList();
    }

    @Override
    public PageRowsVo<AnalysisTaskVo> getPageList(AnalysisTaskSearchDto condition) {
        try {
            AnalysisTaskSearchDto search = condition == null ? new AnalysisTaskSearchDto() : condition;
            Pageable pageable = PageRequest.of(Math.max(search.getPage(), 1) - 1, Math.max(search.getPerPage(), 1));
            Page<AnalysisTask> page = analysisTaskRepository.findByPage(
                    pageable,
                    blankToNull(search.getName()),
                    search.getStatus(),
                    blankToNull(search.getModel()),
                    search.getApprovalMode()
            );
            return new PageRowsVo<>(page.getContent().stream().map(this::toVo).toList(), page.getTotalElements());
        } catch (Exception e) {
            log.error("分页查询AI分析任务失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public AnalysisTask create(AnalysisTaskDto dto) {
        checkCreateOrUpdate(dto);
        List<String> skillIds = normalizeSkillIds(dto.getSkillIds());
        skillService.validateEnabledSkillIds(skillIds);
        dto.setSkillIds(skillIds);
        AnalysisTask task = new AnalysisTask();
        task.updateFromDto(dto);
        task.setStatus(AnalysisTaskStatus.PENDING);
        task.setPriority(defaultPriority(task.getPriority()));
        task.setRunCount(0);
        task.setExecutionId(UUID.randomUUID().toString());
        return analysisTaskRepository.save(task);
    }

    @Override
    public Boolean update(Long id, AnalysisTaskDto dto) {
        checkCreateOrUpdate(dto);
        List<String> skillIds = normalizeSkillIds(dto.getSkillIds());
        skillService.validateEnabledSkillIds(skillIds);
        dto.setSkillIds(skillIds);
        AnalysisTask task = analysisTaskRepository.findById(id).orElse(null);
        if (task == null) {
            return false;
        }
        checkNotRunning(task, "执行中或等待审批的AI分析任务不能修改");
        task.updateFromDto(dto);
        task.setPriority(defaultPriority(task.getPriority()));
        analysisTaskRepository.save(task);
        return true;
    }

    @Override
    public void delete(Long id) {
        AnalysisTask task = analysisTaskRepository.findById(id).orElse(null);
        if (task == null) {
            return;
        }
        checkNotRunning(task, "执行中或等待审批的AI分析任务不能删除");
        taskToolGrantService.revokeExecution(task.getExecutionId());
        analysisTaskRepository.deleteById(id);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids != null) {
            ids.forEach(this::delete);
        }
    }

    @Override
    public AnalysisTaskVo info(Long id) {
        return analysisTaskRepository.findById(id).map(this::toVo).orElse(null);
    }

    @Override
    public AnalysisTaskVo detail(Long id) {
        return analysisTaskRepository.findById(id).map(this::toDetailVo).orElse(null);
    }

    @Override
    public AnalysisTaskVo enqueue(Long id) {
        AnalysisTask task = analysisTaskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "AI分析任务不存在"));
        checkNotRunning(task, "执行中或等待审批的AI分析任务不能重新入队");
        skillService.validateEnabledSkillIds(new ArrayList<>(task.getSkillIds()));
        taskToolGrantService.revokeExecution(task.getExecutionId());
        task.setStatus(AnalysisTaskStatus.PENDING);
        task.setExecutionId(UUID.randomUUID().toString());
        task.setResult(null);
        task.setErrorMessage(null);
        task.setStartTime(null);
        task.setFinishTime(null);
        return toVo(analysisTaskRepository.save(task));
    }

    @Override
    public AnalysisTaskVo cancel(Long id) {
        AnalysisTask task = analysisTaskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "AI分析任务不存在"));
        if (task.getStatus() == AnalysisTaskStatus.CANCELED) {
            return toVo(task);
        }
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "已结束的AI分析任务无需取消");
        }
        Date now = new Date();
        String executionId = task.getExecutionId();
        TaskExecutionControl control = executions.get(executionId);
        if (task.getStatus() == AnalysisTaskStatus.PENDING) {
            task.setStatus(AnalysisTaskStatus.CANCELED).setFinishTime(now);
            taskToolGrantService.revokeExecution(executionId);
            return toVo(analysisTaskRepository.save(task));
        }
        task.setStatus(AnalysisTaskStatus.CANCELING);
        analysisTaskRepository.save(task);
        mcpApprovalService.cancelTaskExecution(executionId, "AI分析任务已被用户取消");
        if (control != null) {
            control.cancel();
        } else {
            task = analysisTaskRepository.findById(id).orElse(task);
            task.setStatus(AnalysisTaskStatus.CANCELED).setFinishTime(now);
            analysisTaskRepository.save(task);
        }
        return info(id);
    }

    /**
     * Atomically claims one ready task and submits it to the background executor.
     */
    @Override
    public AnalysisTaskVo executeNextTask() {
        ensureExecutor();
        if (analysisTaskRepository.countByStatus(AnalysisTaskStatus.WAITING_APPROVAL) >= maxSuspended) {
            log.debug("等待审批的AI分析任务已达上限: {}", maxSuspended);
            return null;
        }
        if (!runningSlots.tryAcquire()) {
            return null;
        }

        dispatchLock.lock();
        try {
            Optional<AnalysisTask> candidate = analysisTaskRepository.findNextReadyTask(new Date());
            if (candidate.isEmpty()) {
                runningSlots.release();
                return null;
            }
            AnalysisTask selected = candidate.get();
            String executionId = StringUtils.defaultIfBlank(selected.getExecutionId(), UUID.randomUUID().toString());
            Date now = new Date();
            if (analysisTaskRepository.claimPendingTask(selected.getId(), executionId, now) != 1) {
                runningSlots.release();
                return null;
            }
            AnalysisTask claimed = analysisTaskRepository.findById(selected.getId())
                    .orElseThrow(() -> new IllegalStateException("已认领的AI分析任务不存在"));
            TaskExecutionControl control = new TaskExecutionControl(executionId, true);
            executions.put(executionId, control);
            try {
                Future<?> future = workerPool.submit(() -> runClaimedTask(claimed.getId(), executionId, control));
                control.setFuture(future);
            } catch (RuntimeException e) {
                executions.remove(executionId, control);
                control.releaseSlot();
                claimed.setStatus(AnalysisTaskStatus.PENDING)
                        .setErrorMessage("后台执行器忙，任务已退回队列")
                        .setStartTime(null);
                analysisTaskRepository.save(claimed);
                throw e;
            }
            return toVo(claimed);
        } finally {
            dispatchLock.unlock();
        }
    }

    @Override
    public AnalysisTaskQueueVo queueStatus() {
        Date now = new Date();
        AnalysisTaskVo runningTask = analysisTaskRepository
                .findFirstByStatusOrderByStartTimeAsc(AnalysisTaskStatus.RUNNING)
                .map(this::toVo)
                .orElse(null);
        AnalysisTaskVo nextTask = analysisTaskRepository.findNextPendingTask().map(this::toVo).orElse(null);
        long running = analysisTaskRepository.countByStatus(AnalysisTaskStatus.RUNNING);
        long waiting = analysisTaskRepository.countByStatus(AnalysisTaskStatus.WAITING_APPROVAL);
        return new AnalysisTaskQueueVo(
                runningTask,
                nextTask,
                analysisTaskRepository.countByStatus(AnalysisTaskStatus.PENDING),
                analysisTaskRepository.countReadyTasks(AnalysisTaskStatus.PENDING, now),
                running,
                waiting,
                runningSlots == null ? 0 : runningSlots.availablePermits(),
                maxSuspended,
                now
        );
    }

    @Override
    public void recoverRunningTasks() {
        int migrated = analysisTaskRepository.backfillLegacyApprovalMode();
        if (migrated > 0) {
            log.info("已将 {} 个历史AI分析任务的审批模式补齐为 MANUAL", migrated);
        }
        List<AnalysisTask> activeTasks = new ArrayList<>(
                analysisTaskRepository.findByStatus(AnalysisTaskStatus.RUNNING));
        activeTasks.addAll(analysisTaskRepository.findByStatus(AnalysisTaskStatus.WAITING_APPROVAL));
        activeTasks.addAll(analysisTaskRepository.findByStatus(AnalysisTaskStatus.CANCELING));
        if (activeTasks.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (AnalysisTask task : activeTasks) {
            String interruptedExecution = task.getExecutionId();
            mcpApprovalService.cancelTaskExecution(interruptedExecution, "服务重启，原AI分析任务执行已失效");
            taskToolGrantService.revokeExecution(interruptedExecution);
            if (task.getStatus() == AnalysisTaskStatus.CANCELING) {
                task.setStatus(AnalysisTaskStatus.CANCELED).setFinishTime(now);
            } else {
                task.setStatus(AnalysisTaskStatus.PENDING)
                        .setExecutionId(UUID.randomUUID().toString())
                        .setStartTime(null)
                        .setFinishTime(null)
                        .setErrorMessage("服务重启，原执行已中断，任务已自动重新入队。重新执行可能重复原执行已产生的外部副作用。");
            }
        }
        analysisTaskRepository.saveAll(activeTasks);
        log.warn("已恢复 {} 个未完成的AI分析任务", activeTasks.size());
    }

    private void runClaimedTask(Integer taskId, String executionId, TaskExecutionControl control) {
        control.setWorkerThread(Thread.currentThread());
        try {
            AnalysisTask task = loadCurrentExecution(taskId, executionId);
            skillService.validateEnabledSkillIds(new ArrayList<>(task.getSkillIds()));
            String result = callAiAnalyze(task, control);
            if (control.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new TaskCancelledException();
            }
            finishExecution(taskId, executionId, AnalysisTaskStatus.SUCCESS, result, null);
            log.info("AI分析任务执行完成, id: {}, executionId: {}", taskId, executionId);
        } catch (TaskCancelledException e) {
            finishExecution(taskId, executionId, AnalysisTaskStatus.CANCELED, null, "AI分析任务已取消");
        } catch (Exception e) {
            if (control.isCancelled() || Thread.currentThread().isInterrupted()) {
                finishExecution(taskId, executionId, AnalysisTaskStatus.CANCELED, null, "AI分析任务已取消");
            } else {
                finishExecution(taskId, executionId, AnalysisTaskStatus.FAILED, null, resolveErrorMessage(e));
                log.error("AI分析任务执行失败, id: {}, executionId: {}", taskId, executionId, e);
            }
        } finally {
            control.releaseSlot();
            executions.remove(executionId, control);
            taskToolGrantService.revokeExecution(executionId);
        }
    }

    private AnalysisTask loadCurrentExecution(Integer taskId, String executionId) {
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("AI分析任务不存在"));
        if (!executionId.equals(task.getExecutionId()) || task.getStatus() == AnalysisTaskStatus.CANCELED) {
            throw new TaskCancelledException();
        }
        return task;
    }

    private void finishExecution(Integer taskId,
                                 String executionId,
                                 AnalysisTaskStatus status,
                                 String result,
                                 String error) {
        try {
            AnalysisTask task = analysisTaskRepository.findById(taskId).orElse(null);
            if (task == null || !executionId.equals(task.getExecutionId())) {
                return;
            }
            if (task.getStatus() == AnalysisTaskStatus.CANCELED
                    || (task.getStatus() == AnalysisTaskStatus.CANCELING && status != AnalysisTaskStatus.CANCELED)) {
                return;
            }
            task.setStatus(status)
                    .setFinishTime(new Date())
                    .setErrorMessage(error);
            if (status == AnalysisTaskStatus.SUCCESS) {
                task.setResult(result);
            }
            analysisTaskRepository.save(task);
        } catch (OptimisticLockingFailureException e) {
            log.warn("AI分析任务终态已被其他操作更新, id={}, executionId={}", taskId, executionId);
        }
    }

    private String callAiAnalyze(AnalysisTask task) {
        return callAiAnalyze(task, null);
    }

    private String callAiAnalyze(AnalysisTask task, TaskExecutionControl control) {
        String model = aiBaseService.resolveChatModel(task.getModel(), false, false);
        McpToolContext mcpToolContext = agentMcpToolService.resolve("agent_analysis");
        if (mcpToolContext.hasTools()) {
            McpInvocationContext invocationContext = control == null
                    ? McpInvocationContext.background("agent_analysis")
                    : McpInvocationContext.backgroundTask(
                            task.getId(),
                            task.getExecutionId(),
                            task.getCreateBy(),
                            effectiveApprovalMode(task),
                            event -> onMcpApprovalEvent(task.getId(), task.getExecutionId(), control, event),
                            control::isCancelled
                    );
            mcpToolContext = mcpToolContext.withInvocationContext(invocationContext);
        }
        try {
            agentLlmService.setModel(model);
            agentLlmService.setMcpToolContext(mcpToolContext);
            return agentLlmService.callWithSystemPrompt(buildAnalysisSystemPrompt(task), buildAnalyzePrompt(task));
        } finally {
            agentLlmService.clearModel();
            agentLlmService.clearMcpToolContext();
        }
    }

    private void onMcpApprovalEvent(Integer taskId,
                                    String executionId,
                                    TaskExecutionControl control,
                                    McpApprovalEvent event) {
        if (event == null || event.data() == null || control.isCancelled()) {
            return;
        }
        McpApprovalVo approval = event.data();
        String requestId = approval.getRequestId();
        if ("approval_required".equals(event.event())) {
            control.rememberWaitThread(requestId, Thread.currentThread());
            transitionExecutionStatus(taskId, executionId, AnalysisTaskStatus.WAITING_APPROVAL);
            control.releaseSlot();
            return;
        }
        if (!control.isWaitThread(requestId, Thread.currentThread())) {
            return;
        }
        McpInvocationStatus status = approval.getStatus();
        if (status == McpInvocationStatus.RUNNING
                || status == McpInvocationStatus.REJECTED
                || status == McpInvocationStatus.DENIED
                || status == McpInvocationStatus.EXPIRED) {
            control.acquireSlot();
            if (!control.isCancelled()) {
                transitionExecutionStatus(taskId, executionId, AnalysisTaskStatus.RUNNING);
            }
            if (status != McpInvocationStatus.RUNNING) {
                control.forgetWaitThread(requestId);
            }
        } else if (status == McpInvocationStatus.CANCELLED) {
            control.forgetWaitThread(requestId);
            control.cancel();
        }
    }

    private void transitionExecutionStatus(Integer taskId,
                                           String executionId,
                                           AnalysisTaskStatus status) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AnalysisTask task = analysisTaskRepository.findById(taskId).orElse(null);
                if (task == null || !executionId.equals(task.getExecutionId())
                        || task.getStatus() == AnalysisTaskStatus.CANCELING
                        || task.getStatus() == AnalysisTaskStatus.CANCELED
                        || task.getStatus() == AnalysisTaskStatus.SUCCESS
                        || task.getStatus() == AnalysisTaskStatus.FAILED) {
                    return;
                }
                task.setStatus(status);
                analysisTaskRepository.saveAndFlush(task);
                return;
            } catch (OptimisticLockingFailureException e) {
                log.debug("更新AI分析任务审批状态时发生并发竞争, id={}, attempt={}", taskId, attempt + 1);
            }
        }
    }

    private String buildAnalysisSystemPrompt() {
        String skillPrompt = skillService.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_ANALYSIS);
        return appendSkillPrompt(skillPrompt);
    }

    private String buildAnalysisSystemPrompt(AnalysisTask task) {
        List<String> selected = task.getSkillIds() == null
                ? List.of() : new ArrayList<>(task.getSkillIds());
        String skillPrompt = skillService.buildTaskSkillPrompt(BuiltinAgentSkillRegistry.AGENT_ANALYSIS, selected);
        return appendSkillPrompt(skillPrompt);
    }

    private String appendSkillPrompt(String skillPrompt) {
        return StringUtils.isBlank(skillPrompt)
                ? ANALYSIS_SYSTEM_PROMPT
                : ANALYSIS_SYSTEM_PROMPT + "\n\n【已加载 Skill】\n" + skillPrompt;
    }

    private String buildAnalyzePrompt(AnalysisTask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务名称：").append(task.getName()).append("\n");
        if (StringUtils.isNotBlank(task.getDescription())) {
            prompt.append("任务描述：").append(task.getDescription()).append("\n");
        }
        prompt.append("分析提示词：\n").append(task.getPrompt());
        return prompt.toString();
    }

    private AnalysisTaskVo toVo(AnalysisTask task) {
        AnalysisTaskVo vo = new AnalysisTaskVo(task);
        if (StringUtils.isNotBlank(task.getExecutionId())) {
            vo.setPendingApprovalCount(mcpApprovalService.countPendingTaskApprovals(task.getExecutionId()));
        }
        return vo;
    }

    private AnalysisTaskVo toDetailVo(AnalysisTask task) {
        AnalysisTaskVo vo = toVo(task);
        List<ChatMessagePart> resultParts = StringUtils.isBlank(task.getResult())
                ? List.of()
                : chatMessagePartParser.parse(task.getResult(), MessageType.TEXT);
        vo.setResultParts(resultParts);
        return vo;
    }

    private void ensureExecutor() {
        if (workerPool == null || runningSlots == null) {
            synchronized (this) {
                if (workerPool == null || runningSlots == null) {
                    initializeExecutor();
                }
            }
        }
    }

    private static void checkCreateOrUpdate(AnalysisTaskDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getPrompt())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (dto.getApprovalMode() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "请选择MCP审批模式");
        }
    }

    private static void checkNotRunning(AnalysisTask task, String message) {
        if (task.getStatus() != null && task.getStatus().isRunning()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
        }
    }

    private static AnalysisTaskApprovalMode effectiveApprovalMode(AnalysisTask task) {
        return task.getApprovalMode() == null ? AnalysisTaskApprovalMode.MANUAL : task.getApprovalMode();
    }

    private static List<String> normalizeSkillIds(List<String> skillIds) {
        if (skillIds == null) {
            return new ArrayList<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String skillId : skillIds) {
            if (StringUtils.isNotBlank(skillId)) {
                normalized.add(skillId.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private static Integer defaultPriority(Integer priority) {
        return priority == null ? 0 : priority;
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String resolveErrorMessage(Exception e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }

    private final class TaskExecutionControl {
        private final String executionId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean slotHeld;
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
        private final ConcurrentHashMap<String, Thread> waitThreads = new ConcurrentHashMap<>();
        private volatile Thread workerThread;

        private TaskExecutionControl(String executionId, boolean slotHeld) {
            this.executionId = executionId;
            this.slotHeld = new AtomicBoolean(slotHeld);
        }

        private void setWorkerThread(Thread workerThread) {
            this.workerThread = workerThread;
        }

        private void setFuture(Future<?> value) {
            future.set(value);
            if (cancelled.get()) {
                value.cancel(true);
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Future<?> running = future.get();
                if (running != null) {
                    running.cancel(true);
                }
                Thread thread = workerThread;
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }

        private void releaseSlot() {
            if (slotHeld.compareAndSet(true, false)) {
                runningSlots.release();
            }
        }

        private void acquireSlot() {
            if (slotHeld.get() || cancelled.get()) {
                return;
            }
            try {
                runningSlots.acquire();
                if (!slotHeld.compareAndSet(false, true)) {
                    runningSlots.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel();
                throw new TaskCancelledException();
            }
        }

        private void rememberWaitThread(String requestId, Thread thread) {
            if (StringUtils.isNotBlank(requestId)) {
                waitThreads.put(requestId, thread);
            }
        }

        private boolean isWaitThread(String requestId, Thread thread) {
            return thread != null && thread.equals(waitThreads.get(requestId));
        }

        private void forgetWaitThread(String requestId) {
            waitThreads.remove(requestId);
        }
    }

    private static final class TaskCancelledException extends RuntimeException {
    }
}
