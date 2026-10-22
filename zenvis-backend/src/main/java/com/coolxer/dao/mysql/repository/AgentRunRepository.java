package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.AgentRunStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.AgentRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends BaseRepository<AgentRun, Integer> {

    Optional<AgentRun> findByRunIdAndCreateBy(String runId, Integer createBy);

    Optional<AgentRun> findFirstByChatIdAndCreateByAndStatusInOrderByCreateTimeDesc(
            String chatId, Integer createBy, List<AgentRunStatus> statuses);

    Optional<AgentRun> findByRunId(String runId);

    long countByStatusIn(List<AgentRunStatus> statuses);

    @Query("""
            SELECT a FROM AgentRun a
            WHERE a.createBy = :createBy
              AND (:chatId IS NULL OR a.chatId = :chatId)
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.updateTime DESC
            """)
    Page<AgentRun> findByPage(Pageable pageable,
                              @Param("chatId") String chatId,
                              @Param("status") AgentRunStatus status,
                              @Param("createBy") Integer createBy);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_AGENT_RUN + " a " +
                    "WHERE (a.status = 'QUEUED' OR " +
                    "(a.status = 'RUNNING' AND (a.lease_until IS NULL OR a.lease_until < :now))) " +
                    "ORDER BY a.create_time ASC, a.id ASC LIMIT 1")
    Optional<AgentRun> findNextRunnable(@Param("now") Date now);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_AGENT_RUN + " " +
                    "SET recovery_count = recovery_count + IF(status = 'RUNNING', 1, 0), " +
                    "status = 'RUNNING', lease_id = :leaseId, lease_until = :leaseUntil, " +
                    "heartbeat_time = :now, started_at = COALESCE(started_at, :now), " +
                    "attempt_count = attempt_count + 1, " +
                    "lock_version = lock_version + 1 " +
                    "WHERE id = :id AND (status = 'QUEUED' OR " +
                    "(status = 'RUNNING' AND (lease_until IS NULL OR lease_until < :now)))")
    int claimRun(@Param("id") Integer id,
                 @Param("leaseId") String leaseId,
                 @Param("leaseUntil") Date leaseUntil,
                 @Param("now") Date now);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_AGENT_RUN + " " +
                    "SET heartbeat_time = :now, lease_until = :leaseUntil, " +
                    "lock_version = lock_version + 1 " +
                    "WHERE id = :id AND lease_id = :leaseId AND status = 'RUNNING'")
    int heartbeat(@Param("id") Integer id,
                  @Param("leaseId") String leaseId,
                  @Param("now") Date now,
                  @Param("leaseUntil") Date leaseUntil);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_AGENT_RUN + " a " +
                    "WHERE a.id = :id FOR UPDATE")
    Optional<AgentRun> findByIdForUpdate(@Param("id") Integer id);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_AGENT_RUN + " a " +
                    "WHERE a.run_id = :runId FOR UPDATE")
    Optional<AgentRun> findByRunIdForUpdate(@Param("runId") String runId);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_AGENT_RUN + " " +
                    "SET status = 'QUEUED', lease_id = NULL, lease_until = NULL, " +
                    "recovery_count = recovery_count + 1, " +
                    "paused_reason = NULL, lock_version = lock_version + 1 " +
                    "WHERE status = 'RUNNING' " +
                    "AND (heartbeat_time IS NULL OR heartbeat_time < :staleBefore)")
    int requeueInterrupted(@Param("staleBefore") Date staleBefore);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_AGENT_RUN + " " +
                    "SET status = 'CANCELLED', finished_at = :now, " +
                    "lease_id = NULL, lease_until = NULL, lock_version = lock_version + 1 " +
                    "WHERE status = 'CANCELING' " +
                    "AND (lease_id IS NULL OR lease_until IS NULL OR lease_until < :now)")
    int finishCanceling(@Param("now") Date now);
}
