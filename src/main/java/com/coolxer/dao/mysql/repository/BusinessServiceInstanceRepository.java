package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.BusinessServiceReportedStatus;
import com.coolxer.dao.mysql.entity.BusinessServiceInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.Optional;

public interface BusinessServiceInstanceRepository extends BaseRepository<BusinessServiceInstance, Integer> {

    Optional<BusinessServiceInstance> findById(Integer id);

    Optional<BusinessServiceInstance> findByServiceCodeAndInstanceId(String serviceCode, String instanceId);

    @Query("SELECT COUNT(DISTINCT b.serviceCode) FROM BusinessServiceInstance b")
    long countDistinctServices();

    long countByLastHeartbeatTimeBefore(Date cutoff);

    long countByLastHeartbeatTimeGreaterThanEqualAndReportedStatus(
            Date cutoff, BusinessServiceReportedStatus reportedStatus);

    @Query("""
            SELECT b FROM BusinessServiceInstance b
            WHERE (:keyword IS NULL
                OR LOWER(b.serviceCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(b.serviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(b.instanceId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(b.host) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:environment IS NULL OR LOWER(b.environment) = LOWER(:environment))
              AND (:statusFiltered = false
                OR (:offline = true AND b.lastHeartbeatTime < :cutoff)
                OR (:offline = false AND b.lastHeartbeatTime >= :cutoff AND b.reportedStatus = :reportedStatus))
            ORDER BY b.lastHeartbeatTime DESC, b.id DESC
            """)
    Page<BusinessServiceInstance> findByPage(
            Pageable pageable,
            @Param("keyword") String keyword,
            @Param("environment") String environment,
            @Param("statusFiltered") boolean statusFiltered,
            @Param("offline") boolean offline,
            @Param("reportedStatus") BusinessServiceReportedStatus reportedStatus,
            @Param("cutoff") Date cutoff);

    @Modifying
    @Query("""
            DELETE FROM BusinessServiceInstance b
            WHERE b.lastHeartbeatTime < :cutoff
              AND (b.lastEventTime IS NULL OR b.lastEventTime < :cutoff)
            """)
    int deleteStaleInstances(@Param("cutoff") Date cutoff);
}
