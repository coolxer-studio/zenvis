package com.coolxer.service.dih.agentrun;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.dao.mysql.repository.AgentRunRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent Run 的 CAS 认领与多线程调度器。
 */
@Slf4j
@Component
public class AgentRunScheduler {

    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentRunExecutor runExecutor;
    private final int maxConcurrency;
    private final long leaseSeconds;
    private final AtomicInteger workerSequence = new AtomicInteger();
    private final Semaphore slots;
    private ExecutorService workers;

    @Autowired(required = false)
    private AgentRunMetrics metrics;

    public AgentRunScheduler(AgentRunRepository runRepository,
                             AgentRunService runService,
                             AgentRunExecutor runExecutor,
                             @Value("${app.ai.agent-run.max-concurrency:4}") int maxConcurrency,
                             @Value("${app.ai.agent-run.lease-seconds:60}") long leaseSeconds) {
        this.runRepository = runRepository;
        this.runService = runService;
        this.runExecutor = runExecutor;
        this.maxConcurrency = Math.max(maxConcurrency, 1);
        this.leaseSeconds = Math.max(leaseSeconds, 10);
        this.slots = new Semaphore(this.maxConcurrency);
    }

    @PostConstruct
    public void initialize() {
        workers = Executors.newFixedThreadPool(maxConcurrency, runnable -> {
            Thread thread = new Thread(runnable,
                    "agent-run-worker-" + workerSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        if (!runService.isEnabled()) {
            return;
        }
        try {
            Date staleBefore = new Date(System.currentTimeMillis() - Math.max(leaseSeconds * 2, 120) * 1000L);
            int recovered = runRepository.requeueInterrupted(staleBefore);
            if (recovered > 0) {
                log.warn("已重新入队 {} 个中断的 Agent Run", recovered);
            }
        } catch (RuntimeException e) {
            log.error("恢复中断 Agent Run 失败", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.agent-run.dispatch-delay-ms:1000}")
    public void dispatch() {
        if (!runService.isEnabled()) {
            return;
        }
        try {
            runRepository.finishCanceling(new Date());
            if (metrics != null) {
                metrics.queueLength(runRepository.countByStatusIn(AgentRunService.ACTIVE_STATUSES));
            }
            while (slots.tryAcquire()) {
                boolean submitted = claimAndSubmit();
                if (!submitted) {
                    slots.release();
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.error("Agent Run 调度失败", e);
        }
    }

    private boolean claimAndSubmit() {
        Date now = new Date();
        Optional<AgentRun> candidate = runRepository.findNextRunnable(now);
        if (candidate.isEmpty()) {
            return false;
        }
        AgentRun run = candidate.get();
        if (run.getRecoveryCount() != null && run.getRecoveryCount() >= 3) {
            runService.markFailed(run.getRunId(), "执行租约连续过期，任务已停止以避免重复副作用", run.getCurrentStepId());
            if (metrics != null) {
                metrics.failure("lease_expired");
            }
            return false;
        }
        String leaseId = java.util.UUID.randomUUID().toString();
        Date leaseUntil = new Date(now.getTime() + leaseSeconds * 1000L);
        int claimed = runRepository.claimRun(run.getId(), leaseId, leaseUntil, now);
        if (claimed != 1) {
            return false;
        }
        if (metrics != null && run.getRecoveryCount() != null && run.getRecoveryCount() > 0) {
            metrics.recovery();
        }
        workers.submit(() -> {
            try {
                runExecutor.execute(run.getRunId(), leaseId);
            } finally {
                slots.release();
            }
        });
        return true;
    }

    @PreDestroy
    public void shutdown() {
        if (workers != null) {
            workers.shutdownNow();
            try {
                workers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
