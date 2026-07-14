package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.McpTaskToolGrant;
import com.coolxer.dao.mysql.entity.McpTaskToolGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface McpTaskToolGrantRepository extends JpaRepository<McpTaskToolGrant, McpTaskToolGrantId> {

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(value = """
            INSERT INTO t_ai_mcp_task_tool_grant
                (execution_id, tool_key, analysis_task_id, requester_user_id, granted_by,
                 source_request_id, create_time, update_time)
            VALUES
                (:executionId, :toolKey, :taskId, :requesterUserId, :grantedBy,
                 :sourceRequestId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                granted_by = VALUES(granted_by),
                source_request_id = VALUES(source_request_id),
                update_time = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsert(@Param("executionId") String executionId,
                @Param("toolKey") String toolKey,
                @Param("taskId") Integer taskId,
                @Param("requesterUserId") Integer requesterUserId,
                @Param("grantedBy") Integer grantedBy,
                @Param("sourceRequestId") String sourceRequestId);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    void deleteByIdExecutionId(String executionId);
}
