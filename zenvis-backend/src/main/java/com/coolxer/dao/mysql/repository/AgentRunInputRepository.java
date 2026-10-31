package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.AgentRunInputStatus;
import com.coolxer.dao.mysql.entity.AgentRunInput;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AgentRunInputRepository extends BaseRepository<AgentRunInput, Integer> {

    Optional<AgentRunInput> findByRunIdAndClientMessageId(String runId, String clientMessageId);

    Optional<AgentRunInput> findFirstByRunIdAndInteractionIdOrderByCreateTimeAsc(
            String runId, String interactionId);

    List<AgentRunInput> findByRunIdAndStatusOrderByCreateTimeAsc(
            String runId, AgentRunInputStatus status);

    List<AgentRunInput> findByRunIdOrderByCreateTimeAsc(String runId);

    long countByRunIdAndStatus(String runId, AgentRunInputStatus status);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query("UPDATE AgentRunInput i SET i.status = :status, i.appliedAt = :appliedAt " +
            "WHERE i.runId = :runId AND i.status = 'PENDING'")
    int applyPending(@Param("runId") String runId,
                     @Param("status") AgentRunInputStatus status,
                     @Param("appliedAt") Date appliedAt);
}
