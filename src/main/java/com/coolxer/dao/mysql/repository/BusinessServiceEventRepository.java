package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import com.coolxer.dao.mysql.entity.BusinessServiceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.Optional;

public interface BusinessServiceEventRepository extends BaseRepository<BusinessServiceEvent, Integer> {

    Optional<BusinessServiceEvent> findByEventId(String eventId);

    long countByCreateTimeGreaterThanEqual(Date cutoff);

    @Query("""
            SELECT e FROM BusinessServiceEvent e
            WHERE (:keyword IS NULL
                OR LOWER(e.eventId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.message) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.traceId) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:serviceCode IS NULL OR e.serviceCode = :serviceCode)
              AND (:instanceId IS NULL OR e.instanceId = :instanceId)
              AND (:severity IS NULL OR e.severity = :severity)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:startTime IS NULL OR e.occurredTime >= :startTime)
              AND (:endTime IS NULL OR e.occurredTime <= :endTime)
            ORDER BY e.occurredTime DESC, e.id DESC
            """)
    Page<BusinessServiceEvent> findByPage(
            Pageable pageable,
            @Param("keyword") String keyword,
            @Param("serviceCode") String serviceCode,
            @Param("instanceId") String instanceId,
            @Param("severity") BusinessServiceEventSeverity severity,
            @Param("eventType") String eventType,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    @Modifying
    @Query("DELETE FROM BusinessServiceEvent e WHERE e.createTime < :cutoff")
    int deleteExpiredEvents(@Param("cutoff") Date cutoff);
}
