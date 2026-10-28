package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.ChatAttachmentRecord;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatAttachmentRecordRepository extends BaseRepository<ChatAttachmentRecord, Integer> {

    Optional<ChatAttachmentRecord> findByFileId(String fileId);

    Optional<ChatAttachmentRecord> findByFileIdAndUserId(String fileId, Integer userId);

    List<ChatAttachmentRecord> findByFileIdInAndUserId(Collection<String> fileIds, Integer userId);

    List<ChatAttachmentRecord> findByUserIdAndChatIdOrderByCreateTimeDesc(Integer userId, String chatId);

    List<ChatAttachmentRecord> findByParseStatusInAndHeartbeatAtBefore(
            List<String> statuses, Date heartbeatBefore);

    List<ChatAttachmentRecord> findByUserIdAndChatIdIsNullAndCreateTimeBefore(
            Integer userId, Date createdBefore);

    List<ChatAttachmentRecord> findByChatIdIsNullAndCreateTimeBefore(Date createdBefore);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query("UPDATE ChatAttachmentRecord a SET a.chatId = :chatId, a.updateTime = :now "
            + "WHERE a.fileId = :fileId AND a.userId = :userId")
    int bindChat(@Param("fileId") String fileId,
                 @Param("userId") Integer userId,
                 @Param("chatId") String chatId,
                 @Param("now") Date now);

    @Modifying
    @Transactional(transactionManager = "mysqlTransactionManager")
    @Query("UPDATE ChatAttachmentRecord a SET a.parseStatus = :status, "
            + "a.parseProgress = :progress, a.parseMessage = :message, "
            + "a.heartbeatAt = :heartbeatAt, a.updateTime = :heartbeatAt "
            + "WHERE a.fileId = :fileId AND a.userId = :userId")
    int updateParseState(@Param("fileId") String fileId,
                         @Param("userId") Integer userId,
                         @Param("status") String status,
                         @Param("progress") Integer progress,
                         @Param("message") String message,
                         @Param("heartbeatAt") Date heartbeatAt);
}
