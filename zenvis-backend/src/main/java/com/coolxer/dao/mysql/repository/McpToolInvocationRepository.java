package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.McpInvocationStatus;
import com.coolxer.commons.enums.McpApprovalScope;
import com.coolxer.dao.mysql.entity.McpToolInvocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface McpToolInvocationRepository extends BaseRepository<McpToolInvocation, Integer>,
        JpaSpecificationExecutor<McpToolInvocation> {
    Optional<McpToolInvocation> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invocation from McpToolInvocation invocation where invocation.requestId = :requestId")
    Optional<McpToolInvocation> findByRequestIdForUpdate(@Param("requestId") String requestId);
    Page<McpToolInvocation> findAllByOrderByCreateTimeDesc(Pageable pageable);
    Page<McpToolInvocation> findByRequesterUserIdOrderByCreateTimeDesc(Integer requesterUserId, Pageable pageable);
    Page<McpToolInvocation> findByStatusOrderByCreateTimeDesc(McpInvocationStatus status, Pageable pageable);
    Page<McpToolInvocation> findByRequesterUserIdAndStatusOrderByCreateTimeDesc(
            Integer requesterUserId, McpInvocationStatus status, Pageable pageable);
    List<McpToolInvocation> findByTurnIdAndStatusIn(String turnId, Collection<McpInvocationStatus> statuses);
    List<McpToolInvocation> findByStatusIn(Collection<McpInvocationStatus> statuses);
    List<McpToolInvocation> findByChatIdAndRequesterUserIdAndToolKeyAndStatus(
            String chatId, Integer requesterUserId, String toolKey, McpInvocationStatus status);
    Page<McpToolInvocation> findByAnalysisTaskIdAndStatusOrderByCreateTimeDesc(
            Integer analysisTaskId, McpInvocationStatus status, Pageable pageable);
    List<McpToolInvocation> findByTaskExecutionIdAndToolKeyAndStatus(
            String taskExecutionId, String toolKey, McpInvocationStatus status);
    List<McpToolInvocation> findByTaskExecutionIdAndStatusIn(
            String taskExecutionId, Collection<McpInvocationStatus> statuses);
    long countByTaskExecutionIdAndStatus(String taskExecutionId, McpInvocationStatus status);
    List<McpToolInvocation> findByAgentRunIdAndTurnIdAndStatusIn(
            String agentRunId, String turnId, Collection<McpInvocationStatus> statuses);
    List<McpToolInvocation> findByAgentRunIdAndTurnIdOrderByCreateTimeDesc(
            String agentRunId, String turnId);
    List<McpToolInvocation> findByAgentRunIdAndToolKeyAndStatus(
            String agentRunId, String toolKey, McpInvocationStatus status);
    List<McpToolInvocation> findByAgentRunIdAndStatusIn(
            String agentRunId, Collection<McpInvocationStatus> statuses);
    Optional<McpToolInvocation> findFirstByAgentRunIdAndTurnIdAndToolKeyAndArgumentsDigestOrderByCreateTimeDesc(
            String agentRunId, String turnId, String toolKey, String argumentsDigest);
    boolean existsByAgentRunIdAndToolKeyAndApprovalScopeAndStatusIn(
            String agentRunId, String toolKey, McpApprovalScope scope,
            Collection<McpInvocationStatus> statuses);
}
