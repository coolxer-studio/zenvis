package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.AnalysisTaskSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AnalysisTaskScheduleRepository extends BaseRepository<AnalysisTaskSchedule, Integer> {

    Optional<AnalysisTaskSchedule> findById(Integer id);

    Optional<AnalysisTaskSchedule> findByIdAndCreateBy(Integer id, Integer createBy);

    @Query("""
            SELECT schedule FROM AnalysisTaskSchedule schedule
            WHERE (:name IS NULL OR schedule.name LIKE CONCAT('%', :name, '%'))
              AND (:enabled IS NULL OR schedule.enabled = :enabled)
              AND schedule.createBy = :createBy
            ORDER BY schedule.updateTime DESC
            """)
    Page<AnalysisTaskSchedule> findByPage(Pageable pageable,
                                          @Param("name") String name,
                                          @Param("enabled") Boolean enabled,
                                          @Param("createBy") Integer createBy);

    @Query("""
            SELECT schedule.id FROM AnalysisTaskSchedule schedule
            WHERE schedule.enabled = true
              AND schedule.nextFireTime IS NOT NULL
              AND schedule.nextFireTime <= :now
            ORDER BY schedule.nextFireTime ASC, schedule.id ASC
            """)
    List<Integer> findDueIds(@Param("now") Date now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT schedule FROM AnalysisTaskSchedule schedule WHERE schedule.id = :id")
    Optional<AnalysisTaskSchedule> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT schedule FROM AnalysisTaskSchedule schedule
            WHERE schedule.id = :id AND schedule.createBy = :createBy
            """)
    Optional<AnalysisTaskSchedule> findOwnedByIdForUpdate(
            @Param("id") Integer id, @Param("createBy") Integer createBy);
}
