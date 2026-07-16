package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.AnalysisTaskApprovalMode;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * AI分析任务数据库操作类
 */
public interface AnalysisTaskRepository extends BaseRepository<AnalysisTask, Integer> {

    interface StatusCount {
        AnalysisTaskStatus getStatus();

        long getCount();
    }

    Optional<AnalysisTask> findById(Integer id);

    List<AnalysisTask> findByStatus(AnalysisTaskStatus status);

    long countByStatus(AnalysisTaskStatus status);

    long countByStatusIn(List<AnalysisTaskStatus> statuses);

    @Query("SELECT a.status AS status, COUNT(a) AS count FROM AnalysisTask a GROUP BY a.status")
    List<StatusCount> countGroupByStatus();

    List<AnalysisTask> findTop10ByOrderByUpdateTimeDesc();

    Optional<AnalysisTask> findFirstByStatusOrderByStartTimeAsc(AnalysisTaskStatus status);

    @Query("""
            SELECT a FROM AnalysisTask a
            WHERE (:name IS NULL OR a.name LIKE CONCAT('%', :name, '%'))
              AND (:status IS NULL OR a.status = :status)
              AND (:model IS NULL OR a.model = :model)
              AND (:approvalMode IS NULL OR a.approvalMode = :approvalMode)
            ORDER BY a.updateTime DESC
            """)
    Page<AnalysisTask> findByPage(Pageable pageable,
                                  @Param("name") String name,
                                  @Param("status") AnalysisTaskStatus status,
                                  @Param("model") String model,
                                  @Param("approvalMode") AnalysisTaskApprovalMode approvalMode);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " a " +
                    "WHERE a.status = 'PENDING' " +
                    "AND (a.scheduled_time IS NULL OR a.scheduled_time <= :now) " +
                    "ORDER BY CASE WHEN a.scheduled_time IS NULL THEN 1 ELSE 0 END ASC, " +
                    "a.scheduled_time ASC, a.priority DESC, a.create_time ASC, a.id ASC " +
                    "LIMIT 1")
    Optional<AnalysisTask> findNextReadyTask(@Param("now") Date now);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " a " +
                    "WHERE a.status = 'PENDING' " +
                    "ORDER BY CASE WHEN a.scheduled_time IS NULL THEN 1 ELSE 0 END ASC, " +
                    "a.scheduled_time ASC, a.priority DESC, a.create_time ASC, a.id ASC " +
                    "LIMIT 1")
    Optional<AnalysisTask> findNextPendingTask();

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " " +
                    "SET status = 'RUNNING', execution_id = :executionId, start_time = :now, " +
                    "finish_time = NULL, error_message = NULL, " +
                    "run_count = COALESCE(run_count, 0) + 1, lock_version = COALESCE(lock_version, 0) + 1 " +
                    "WHERE id = :id AND status = 'PENDING'")
    int claimPendingTask(@Param("id") Integer id,
                         @Param("executionId") String executionId,
                         @Param("now") Date now);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(nativeQuery = true,
            value = "UPDATE " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " " +
                    "SET approval_mode = 'MANUAL' WHERE approval_mode IS NULL")
    int backfillLegacyApprovalMode();

    @Query("""
            SELECT COUNT(a) FROM AnalysisTask a
            WHERE a.status = :status
              AND (a.scheduledTime IS NULL OR a.scheduledTime <= :now)
            """)
    long countReadyTasks(@Param("status") AnalysisTaskStatus status, @Param("now") Date now);
}
