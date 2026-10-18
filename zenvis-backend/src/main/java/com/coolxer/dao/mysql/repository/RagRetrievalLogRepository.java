package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.RagRetrievalLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RAG检索审计日志
 */
public interface RagRetrievalLogRepository extends BaseRepository<RagRetrievalLog, Integer> {

    /**
     * 分页查询检索日志
     *
     * @param pageable        分页参数
     * @param knowledgeBaseId 知识库ID（可选）
     * @param requestType     请求类型（可选）
     * @param status          状态（可选）
     * @param keyword         检索请求文本模糊匹配（可选）
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_RAG_RETRIEVAL_LOG + " a WHERE a.is_delete = 0 AND " +
                    "(:knowledgeBaseId IS NULL OR a.knowledge_base_id = :knowledgeBaseId) AND " +
                    "(:requestType IS NULL OR a.request_type = :requestType) AND " +
                    "(:status IS NULL OR a.status = :status) AND " +
                    "(:keyword IS NULL OR a.query_text like concat('%',:keyword,'%')) " +
                    "ORDER BY a.create_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_RAG_RETRIEVAL_LOG + " a WHERE a.is_delete = 0 AND " +
                    "(:knowledgeBaseId IS NULL OR a.knowledge_base_id = :knowledgeBaseId) AND " +
                    "(:requestType IS NULL OR a.request_type = :requestType) AND " +
                    "(:status IS NULL OR a.status = :status) AND " +
                    "(:keyword IS NULL OR a.query_text like concat('%',:keyword,'%')) ")
    Page<RagRetrievalLog> findByPage(Pageable pageable,
                                     @Param("knowledgeBaseId") Integer knowledgeBaseId,
                                     @Param("requestType") String requestType,
                                     @Param("status") String status,
                                     @Param("keyword") String keyword);
}
