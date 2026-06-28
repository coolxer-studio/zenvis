package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * AI分析任务数据库操作类
 */
public interface AnalysisTaskRepository extends BaseRepository<AnalysisTask, Integer> {

    Optional<AnalysisTask> findById(Integer id);

    List<AnalysisTask> findByStatus(AnalysisTaskStatus status);

    long countByStatus(AnalysisTaskStatus status);

    Optional<AnalysisTask> findFirstByStatusOrderByStartTimeAsc(AnalysisTaskStatus status);

    @Query("""
            SELECT a FROM AnalysisTask a
            WHERE (:name IS NULL OR a.name LIKE CONCAT('%', :name, '%'))
              AND (:status IS NULL OR a.status = :status)
              AND (:model IS NULL OR a.model = :model)
            ORDER BY a.updateTime DESC
            """)
    Page<AnalysisTask> findByPage(Pageable pageable,
                                  @Param("name") String name,
                                  @Param("status") AnalysisTaskStatus status,
                                  @Param("model") String model);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " a " +
                    "WHERE a.status = 'PENDING' " +
                    "AND (a.scheduled_time IS NULL OR a.scheduled_time <= :now) " +
                    "ORDER BY a.priority DESC, " +
                    "CASE WHEN a.scheduled_time IS NULL THEN 0 ELSE 1 END ASC, " +
                    "a.scheduled_time ASC, a.create_time ASC, a.id ASC " +
                    "LIMIT 1")
    Optional<AnalysisTask> findNextReadyTask(@Param("now") Date now);

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_ANALYSIS_TASK + " a " +
                    "WHERE a.status = 'PENDING' " +
                    "ORDER BY a.priority DESC, " +
                    "CASE WHEN a.scheduled_time IS NULL THEN 0 ELSE 1 END ASC, " +
                    "a.scheduled_time ASC, a.create_time ASC, a.id ASC " +
                    "LIMIT 1")
    Optional<AnalysisTask> findNextPendingTask();

    @Query("""
            SELECT COUNT(a) FROM AnalysisTask a
            WHERE a.status = :status
              AND (a.scheduledTime IS NULL OR a.scheduledTime <= :now)
            """)
    long countReadyTasks(@Param("status") AnalysisTaskStatus status, @Param("now") Date now);
}
