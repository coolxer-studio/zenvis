package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * AI聊天会话
 */
public interface ChatSessionRepository extends BaseRepository<ChatSession, Integer> {

    /**
     * 根据id查询会话
     *
     * @param id id
     * @return 会话对象
     */
    Optional<ChatSession> findById(Integer id);

    /**
     * 根据会话id查询会话
     *
     * @param sessionId 会话id
     * @return 会话对象
     */
    Optional<ChatSession> findBySessionId(String sessionId);

    /**
     * 根据会话id和创建者查询会话。
     *
     * @param sessionId 会话id
     * @param createBy 创建者
     * @return 会话对象
     */
    Optional<ChatSession> findBySessionIdAndCreateBy(String sessionId, Integer createBy);

    /**
     * 报表素材选择器只读取当前用户最近的会话，避免跨用户暴露智能体产物。
     */
    List<ChatSession> findTop50ByCreateByOrderByUpdateTimeDesc(Integer createBy);

    /**
     * 报表写操作先锁定会话，保证首次建文档、保存和归档不会并发覆盖。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ChatSession session where session.id = :id and session.createBy = :createBy")
    Optional<ChatSession> findOwnedByIdForUpdate(
            @Param("id") Integer id,
            @Param("createBy") Integer createBy);

    /**
     * 查询用户置顶的会话
     *
     * @param createBy
     * @return
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_CHAT_SESSION + " a WHERE a.pin = true AND a.create_by =:createBy")
    List<ChatSession> findPinChatSessionByUser(@Param("createBy") Integer createBy);

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_CHAT_SESSION + " a WHERE " +
                    "(:title IS NULL OR a.title like concat('%',:title,'%')) AND " +
                    "(:type IS NULL OR a.type =:type) AND " +
                    "a.create_by =:createBy " +
            "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_AI_CHAT_SESSION + " a WHERE " +
                    "(:title IS NULL OR a.title like concat('%',:title,'%')) AND " +
                    "(:type IS NULL OR a.type =:type) AND " +
                    "a.create_by =:createBy ")
    Page<ChatSession> findByPage(Pageable pageable, @Param("title") String title, @Param("type") String type, @Param("createBy") Integer createBy);

}
