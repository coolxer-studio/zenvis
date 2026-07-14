package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.McpChatToolGrant;
import com.coolxer.dao.mysql.entity.McpChatToolGrantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface McpChatToolGrantRepository extends JpaRepository<McpChatToolGrant, McpChatToolGrantId> {

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query(value = """
            INSERT INTO t_ai_mcp_chat_tool_grant
                (chat_id, requester_user_id, tool_key, granted_by, source_request_id, create_time, update_time)
            VALUES
                (:chatId, :requesterUserId, :toolKey, :grantedBy, :sourceRequestId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                granted_by = VALUES(granted_by),
                source_request_id = VALUES(source_request_id),
                update_time = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsert(@Param("chatId") String chatId,
                @Param("requesterUserId") Integer requesterUserId,
                @Param("toolKey") String toolKey,
                @Param("grantedBy") Integer grantedBy,
                @Param("sourceRequestId") String sourceRequestId);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query("delete from McpChatToolGrant g where g.id.chatId = :chatId "
            + "and g.id.requesterUserId = :requesterUserId")
    void deleteByChat(@Param("chatId") String chatId,
                      @Param("requesterUserId") Integer requesterUserId);
}
